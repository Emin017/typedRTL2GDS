package rtl2gds.flow

import cats.effect.*
import cats.syntax.all.*
import rtl2gds.InputConfig
import rtl2gds.Yosys
import rtl2gds.configs.GlobalConfigs
import rtl2gds.types.EDATypes.GdsPath
import rtl2gds.types.EDATypes.VerilogPath

import scala.sys.process.*

sealed trait FlowContext {
  def config: InputConfig
  def validate: Either[String, Unit]
}

case class InitialContext(config: InputConfig, inputRtl: VerilogPath)
    extends FlowContext {
  def validate: Either[String, Unit] =
    Either.cond(
      config.designInfo.clkFreqMHz > 0,
      (),
      "Clock frequency must be positive"
    )
}

object InitialContext {
  def fromConfig(config: InputConfig): IO[InitialContext] = {
    IO.fromEither(
      VerilogPath
        .from(config.rtlFile)
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
        "Core utilization must be between 0.0 and 1.0"
      )
      .flatMap(_ => initial.validate)
}

case class PnrContext(syn: SynContext, gdsFile: GdsPath) extends FlowContext {
  def config: InputConfig = syn.config

  def validate: Either[String, Unit] = syn.validate
}

object Flow extends GlobalConfigs {
  def runCommand(cmd: String, env: Seq[(String, String)]): IO[Int] = {
    IO.blocking(Process(Seq("sh", "-c", cmd), None, env*).!)
  }

  /** Simulates the Synthesis step. Takes InitialContext, validates it, and
    * produces SynContext with a Netlist file.
    */
  def runSynthesis(ctx: InitialContext): IO[SynContext] = {
    for {
      _ <- IO.fromEither(ctx.validate.leftMap(new RuntimeException(_)))

      _ <- IO.println(s"[Synthesis] Reading RTL from: ${ctx.inputRtl.value}")
      _ <- IO.println(
        s"[Synthesis] Target Frequency: ${ctx.config.designInfo.clkFreqMHz} MHz"
      )

      outputNetList = s"output/${ctx.config.designName}.v"

      cmd = Yosys.synthCommand(
        config = ctx.config,
        scriptDir = yosysScriptsPath,
        pdkScriptDir = ctx.config.foundry.name,
        rtlFile = ctx.inputRtl,
        outputNetList = outputNetList
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

  /** Simulates the Place & Route step. Takes SynContext, validates it, and
    * produces PnrContext with a GDS file.
    */
  def runPlaceAndRoute(ctx: SynContext): IO[PnrContext] = {
    for {
      _ <- IO.fromEither(ctx.validate.leftMap(new RuntimeException(_)))

      _ <- IO.println(s"[PnR] Reading Netlist from: ${ctx.netlist.value}")
      _ <- IO.println(
        s"[PnR] Core Utilization: ${ctx.config.designInfo.coreUtilization}"
      )

      gdsPathStr = s"output/${ctx.config.designName}.gds"
      gdsOutput <- IO.fromEither(
        GdsPath.from(gdsPathStr).leftMap(new IllegalArgumentException(_))
      )

      _ <- IO.println(s"[PnR] Generated GDS: ${gdsOutput.value}")
    } yield PnrContext(ctx, gdsOutput)
  }

}
