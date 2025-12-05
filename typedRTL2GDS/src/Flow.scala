package rtl2gds.flow

import cats.effect.*
import cats.syntax.all.*
import rtl2gds.InputConfig
import rtl2gds.Yosys
import rtl2gds.configs.GlobalConfigs
import rtl2gds.types.EDATypes.DefPath
import rtl2gds.types.EDATypes.VerilogPath

import scala.sys.process.*

case class InputCTX(
    defPath: Option[DefPath],
    verilogFile: Option[VerilogPath]
    // Add more artifacts as needed
) {
  def validate: Either[String, Unit] = Either.cond(
    defPath.isDefined || verilogFile.isDefined,
    (),
    "At least one input artifact (DEF or Verilog) must be provided."
  )
}

case class OutputCTX(
    defPath: Option[DefPath],
    verilogFile: Option[VerilogPath]
    // Add more artifacts as needed
) {
  def validate: Either[String, Unit] = Either.cond(
    defPath.isDefined || verilogFile.isDefined,
    (),
    "At least one output artifact (DEF or Verilog) must be generated."
  )
}

abstract class FlowContext {
  def config: InputConfig

  def validate: Either[String, Unit]

  def inputCtx: InputCTX

  def outputCtx: OutputCTX
}

abstract class BackendFlowContext[T <: InputCTX](
    val ctx: T
) extends FlowContext {
  def backendStep: String

  def backendOutArtifacts: OutputCTX = OutputCTX(
    defPath = Some(DefPath(s"${config.designName}_${backendStep}.def")),
    verilogFile = Some(VerilogPath(s"${config.designName}_$backendStep.v"))
  )
}

case class InitialContext(config: InputConfig, inputRtl: VerilogPath)
    extends FlowContext {
  def validate: Either[String, Unit] =
    Either.cond(
      config.designInfo.clkFreqMHz > 0,
      (),
      "Clock frequency must be positive, currently: " + config.designInfo.clkFreqMHz
    )

  def inputCtx: InputCTX =
    InputCTX(defPath = None, verilogFile = Some(inputRtl))

  def outputCtx: OutputCTX = OutputCTX(defPath = None, verilogFile = None)
}

object InitialContext {
  def fromConfig(config: InputConfig): IO[InitialContext] = {
    IO.fromEither(
      VerilogPath
        .from(
          System.getProperty("user.dir") + "/" + config.rtlFile
            .stripPrefix("./")
        )
        .leftMap(new IllegalArgumentException(_))
    ).map(InitialContext(config, _))
  }
}

case class SynContext(initial: InitialContext, netlist: VerilogPath)
    extends FlowContext {
  def config: InputConfig = initial.config

  def validate: Either[String, Unit] =
    Either
      .cond(
        config.designInfo.coreUtilization > 0 && config.designInfo.coreUtilization < 1.0,
        (),
        "Core utilization must be between 0.0 and 1.0, currently: " + config.designInfo.coreUtilization
      )
      .flatMap(_ => initial.validate)

  def inputCtx: InputCTX = initial.inputCtx

  def outputCtx: OutputCTX =
    OutputCTX(defPath = None, verilogFile = Some(netlist))
}

case class FloorplanContext(c: InputConfig, inputCtx: InputCTX)
    extends BackendFlowContext(inputCtx) {
  override def backendStep: String = "Floorplan"

  def config: InputConfig = c

  def validate: Either[String, Unit] = inputCtx.validate

  def outputCtx: OutputCTX = backendOutArtifacts
}

case class PlaceContext(c: InputConfig, inputCtx: InputCTX)
    extends BackendFlowContext(inputCtx) {
  override def backendStep: String = "Placement"

  def config: InputConfig = c

  def validate: Either[String, Unit] = inputCtx.validate

  def outputCtx: OutputCTX = backendOutArtifacts
}

case class CTSContext(c: InputConfig, inputCtx: InputCTX)
    extends BackendFlowContext(inputCtx) {
  override def backendStep: String = "ClockTreeSynthesis"

  def config: InputConfig = c

  def validate: Either[String, Unit] = inputCtx.validate

  def outputCtx: OutputCTX = backendOutArtifacts
}

trait FlowStep[In <: FlowContext, Out <: FlowContext] {
  def stepName: String

  def scriptRelativePath: String

  def stepEnv(c: InputConfig, i: InputCTX): Seq[(String, String)]

  def construct(c: InputConfig, i: InputCTX): Out
}

object FlowStep {
  given FlowStep[SynContext, FloorplanContext] with {
    def stepName = "Floorplan"

    def scriptRelativePath = "script/iFP_script/run_iFP.tcl"

    def stepEnv(c: InputConfig, i: InputCTX): Seq[(String, String)] = Seq(
      "NETLIST_FILE" -> i.verilogFile.map(_.value).getOrElse(""),
      "TOP_NAME" -> c.designName,
      "CLK_PORT_NAME" -> c.designInfo.clkPortName,
      "USE_FIXED_BBOX" -> "False",
      "CORE_UTIL" -> c.designInfo.coreUtilization.toString,
      "TAPCELL" -> c.foundry.tapCell,
      "TAP_DISTANCE" -> c.foundry.tapDistance.toString,
      "ENDCAP" -> c.foundry.endCap
    )

    def construct(c: InputConfig, i: InputCTX) =
      FloorplanContext(c, i)
  }

  given FlowStep[FloorplanContext, PlaceContext] with {
    def stepName = "Placement"
    def scriptRelativePath = "script/iPL_script/run_iPL.tcl"

    def stepEnv(c: InputConfig, i: InputCTX): Seq[(String, String)] = Seq(
      "INPUT_DEF" -> i.defPath.map(_.value).getOrElse("")
    )

    def construct(c: InputConfig, i: InputCTX) =
      PlaceContext(c, i)
  }

  given FlowStep[PlaceContext, CTSContext] with {
    def stepName = "ClockTreeSynthesis"
    def scriptRelativePath = "script/iCTS_script/run_iCTS.tcl"

    def stepEnv(c: InputConfig, i: InputCTX): Seq[(String, String)] = Seq(
      "INPUT_DEF" -> i.defPath.map(_.value).getOrElse("")
    )

    def construct(c: InputConfig, i: InputCTX) =
      CTSContext(c, i)
  }

}

object Flow extends GlobalConfigs {
  def runCommand(cmd: String, env: Seq[(String, String)]): IO[Int] = {
    IO.blocking(Process(Seq("sh", "-c", cmd), None, env*).!)
  }

  def checkCtx[T <: FlowContext](ctx: T): IO[Unit] = {
    IO.fromEither(ctx.validate.leftMap(new RuntimeException(_)))
  }

  /** Simulates the Synthesis step. Takes InitialContext, validates it, and
    * produces SynContext with a Netlist file.
    */
  def runSynthesis(ctx: InitialContext): IO[SynContext] = {
    for {
      _ <- checkCtx(ctx)

      _ <- IO.println(s"[Synthesis] Reading RTL from: ${ctx.inputRtl.value}")
      _ <- IO.println(
        s"[Synthesis] Target Frequency: ${ctx.config.designInfo.clkFreqMHz} MHz"
      )

      (synthResultDir, outputNetList) = Yosys.artifacts(
        ctx.config.designName,
        resultDir = ctx.config.resultDir
      )
      // Ensure output directory exists
      _ <- IO.blocking(new java.io.File(synthResultDir).mkdirs())

      cmd = Yosys.synthCommand(
        config = ctx.config,
        scriptDir = yosysScriptsPath,
        pdkScriptDir = ctx.config.foundry.name,
        rtlFile = ctx.inputRtl,
        outputNetList = outputNetList,
        outputDir = synthResultDir
      )

      env = Yosys.runtimeEnv(
        clkFreqMHz = ctx.config.designInfo.clkFreqMHz,
        foundryPath = ctx.config.foundry.pdkDir
      )

      exitCode <- runCommand(cmd, env)

      _ <- IO.raiseWhen(exitCode != 0)(
        new RuntimeException(s"Yosys failed with exit code $exitCode")
      )

      netlistOutput <- VerilogPath.check(outputNetList)

      _ <- IO.println(s"[Synthesis] Generated Netlist: ${netlistOutput.value}")
    } yield SynContext(ctx, netlistOutput)
  }

  def runBackendFlow[In <: FlowContext, Out <: FlowContext](
      config: InputConfig,
      ctx: In
  )(using step: FlowStep[In, Out]): IO[Out] = {
    for {
      _ <- checkCtx(ctx)

      stepName = step.stepName
      _ <- IO.println(
        s"[$stepName] Processing design: ${ctx.config.designName}"
      )
      _ <- IO.println(
        s"[$stepName] Using Input file: ${ctx.inputCtx.defPath
            .map(_.value)
            .getOrElse("N/A")} ${ctx.inputCtx.verilogFile.map(_.value).getOrElse("N/A")}"
      )

      commonEnv = genCommonEnv(ctx)

      stepEnv = step.stepEnv(config, ctx.inputCtx)

      env = commonEnv ++ stepEnv

      _ <- runCommand(
        s"iEDA ${iEDAScriptsPath}/${step.scriptRelativePath}",
        env
      )

      _ <- IO.println(s"[$stepName] Completed successfully.")
      _ <- IO.println(
        s"[$stepName] Output Def file: ${ctx.outputCtx.defPath.map(_.value).getOrElse("N/A")}"
      )
      outputDefPath <- IO.fromEither(
        ctx.outputCtx.defPath
          .toRight(new RuntimeException(s"No DEF file generated in $stepName"))
      )

      _ <- IO.println(
        s"[$stepName] Output Verilog file: ${ctx.outputCtx.verilogFile.map(_.value).getOrElse("N/A")}"
      )
      outputVerilogPath <- IO.fromEither(
        ctx.outputCtx.verilogFile
          .toRight(
            new RuntimeException(s"No Verilog file generated in $stepName")
          )
      )

    } yield step.construct(config, ctx.inputCtx)
  }

  /** Simulates the Place & Route step. Takes SynContext, validates it, and
    * produces PnrContext with a GDS file.
    */
  def runFloorplan(c: InputConfig, ctx: SynContext): IO[FloorplanContext] = {
    for {
      _ <- IO.println(s"[FP] Reading Netlist from: ${ctx.netlist.value}")
      _ <- IO.println(
        s"[FP] Core Utilization: ${ctx.config.designInfo.coreUtilization}"
      )

      nextCtx <- runBackendFlow(c, ctx)
    } yield nextCtx
  }

  def runPlacement(c: InputConfig, ctx: FloorplanContext): IO[PlaceContext] = {
    for {
      _ <- IO.println(
        s"[Place] Core Utilization: ${ctx.config.designInfo.coreUtilization}"
      )
      nextCtx <- runBackendFlow(c, ctx)

    } yield nextCtx
  }

  def runCTS(c: InputConfig, ctx: PlaceContext): IO[CTSContext] = {
    for {
      _ <- IO.println("[CTS] Running Clock Tree Synthesis...")
      nextCtx <- runBackendFlow(c, ctx)

    } yield nextCtx
  }

}
