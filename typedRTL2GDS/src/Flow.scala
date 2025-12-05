package rtl2gds.flow

import cats.effect.*
import cats.syntax.all.*
import rtl2gds.InputConfig
import rtl2gds.Yosys
import rtl2gds.configs.GlobalConfigs
import rtl2gds.types.EDATypes.DefPath
import rtl2gds.types.EDATypes.VerilogPath
import rtl2gds.types.ContextTypes.*

import scala.sys.process.*

object Flow extends GlobalConfigs {
  private def runCommand(cmd: String, env: Seq[(String, String)]): IO[Int] = {
    IO.blocking(Process(Seq("sh", "-c", cmd), None, env*).!)
  }

  private def checkCtx[T <: FlowContext](ctx: T): IO[Unit] = {
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

  private def runBackendFlow[In <: FlowContext, Out <: FlowContext](
      config: InputConfig,
      ctx: In
  )(using step: FlowStep[In, Out]): IO[Out] = {
    for {
      _ <- checkCtx(ctx)

      // Use output of previous step as input for this step
      prevInput = InputCTX(
        ctx.outputCtx.defPath,
        ctx.outputCtx.verilogFile
      )

      // Construct the output context first to get the expected output paths
      outCtx = step.construct(config, prevInput)

      stepName = step.stepName
      _ <- IO.println(
        s"[$stepName] Processing design: ${ctx.config.designName}"
      )
      _ <- IO.println(
        s"[$stepName] Using Input file: ${prevInput.defPath
            .map(_.value)
            .getOrElse("N/A")} ${prevInput.verilogFile.map(_.value).getOrElse("N/A")}"
      )

      // Ensure output directory exists
      _ <- IO.blocking(new java.io.File(config.resultDir).mkdirs())

      commonEnv = genCommonEnv(outCtx)

      stepEnv = step.stepEnv(config, prevInput)

      env = commonEnv ++ stepEnv

      _ <- runCommand(
        s"iEDA $iEDAScriptsPath/${step.scriptRelativePath}",
        env
      )

      _ <- IO.println(s"[$stepName] Completed successfully.")

      _ <- IO.println(
        s"[$stepName] Output Def file: ${outCtx.outputCtx.defPath.map(_.value).getOrElse("N/A")}"
      )
      outputDefPath <- IO.fromEither(
        outCtx.outputCtx.defPath
          .toRight(new RuntimeException(s"No DEF file generated in $stepName"))
      )

      _ <- IO.println(
        s"[$stepName] Output Verilog file: ${outCtx.outputCtx.verilogFile.map(_.value).getOrElse("N/A")}"
      )
      outputVerilogPath <- IO.fromEither(
        outCtx.outputCtx.verilogFile
          .toRight(
            new RuntimeException(s"No Verilog file generated in $stepName")
          )
      )

    } yield outCtx
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

  def runLegalization(
      c: InputConfig,
      ctx: CTSContext
  ): IO[LegalizationContext] = {
    for {
      _ <- IO.println("[Legalization] Running Legalization step...")

      nextCtx <- runBackendFlow(c, ctx)
    } yield nextCtx
  }

  def runRouting(c: InputConfig, ctx: LegalizationContext): IO[RouteContext] = {
    for {
      _ <- IO.println("[Routing] Running Routing step...")
      nextCtx <- runBackendFlow(c, ctx)

    } yield nextCtx
  }

}
