package rtl2gds.dsl

import cats.effect.IO
import cats.syntax.all.*
import rtl2gds.Main.loadConfig
import rtl2gds.SynthSettings
import rtl2gds.flow.Flow
import rtl2gds.types.ContextTypes.*
import rtl2gds.types.EDATypes.DefPath
import rtl2gds.types.EDATypes.VerilogPath

import java.nio.file.Paths
import scala.annotation.targetName

case class Design(name: String) {

  private def resolvePath(path: String): String =
    Option
      .when(Paths.get(path).isAbsolute)(path)
      .getOrElse(s"${System.getProperty("user.dir")}/${path.stripPrefix("./")}")

  /** Configures the input for the design flow.
    *
    * @param verilog
    *   Optional path to the Verilog file. Overrides the value in the config
    *   file.
    * @param sdc
    *   Optional path to the SDC file. Overrides the value in the config file.
    * @param config
    *   Path to the YAML configuration file.
    * @return
    *   An IO effect that produces the InitialContext.
    */
  def input(
      verilog: Option[String] = None,
      sdc: Option[String] = None,
      config: String
  ): IO[InitialContext] = {
    for {
      // Load the base configuration from the YAML file
      baseConfig <- loadConfig(config)

      // Override configuration fields if arguments are provided
      modifiedConfig = baseConfig.copy(
        designName = this.name, // Ensure design name matches
        rtlFile = verilog.getOrElse(baseConfig.rtlFile),
        sdcFile = sdc.getOrElse(baseConfig.sdcFile)
      )

      // Resolve Verilog path (handle absolute vs relative)
      resolvedRtlPath = resolvePath(modifiedConfig.rtlFile)

      // Validate and create VerilogPath
      validRtlPath <- IO.fromEither(
        VerilogPath
          .from(resolvedRtlPath)
          .leftMap(new IllegalArgumentException(_))
      )

    } yield InitialContext(modifiedConfig, validRtlPath)
  }

  /** Configures the input for the design flow starting from a Netlist (skipping
    * Synthesis).
    *
    * @param netlist
    *   Path to the Netlist file.
    * @param sdc
    *   Optional path to the SDC file.
    * @param config
    *   Path to the YAML configuration file.
    * @return
    *   An IO effect that produces the SynContext.
    */
  def inputNetlist(
      netlist: String,
      sdc: Option[String] = None,
      config: String
  ): IO[SynContext] = {
    for {
      baseConfig <- loadConfig(config)
      modifiedConfig = baseConfig.copy(
        designName = this.name,
        sdcFile = sdc.getOrElse(baseConfig.sdcFile)
      )

      resolvedNetlistPath = resolvePath(netlist)
      validNetlistPath <- IO.fromEither(
        VerilogPath
          .from(resolvedNetlistPath)
          .leftMap(new IllegalArgumentException(_))
      )

      // Create InitialContext with netlist as inputRtl to satisfy type requirements
      initialCtx = InitialContext(modifiedConfig, validNetlistPath)
    } yield SynContext(initialCtx, validNetlistPath)
  }

  /** Configures the input for the design flow starting from a DEF file
    * (skipping Floorplan).
    *
    * @param defFile
    *   Path to the DEF file.
    * @param netlist
    *   Path to the Netlist file.
    * @param sdc
    *   Optional path to the SDC file.
    * @param config
    *   Path to the YAML configuration file.
    * @return
    *   An IO effect that produces the FloorplanContext.
    */
  def inputDef(
      defFile: String,
      netlist: String,
      sdc: Option[String] = None,
      config: String
  ): IO[FloorplanContext] = {
    for {
      baseConfig <- loadConfig(config)
      modifiedConfig = baseConfig.copy(
        designName = this.name,
        sdcFile = sdc.getOrElse(baseConfig.sdcFile)
      )

      resolvedDefPath = resolvePath(defFile)
      validDefPath <- IO.fromEither(
        DefPath.from(resolvedDefPath).leftMap(new IllegalArgumentException(_))
      )

      resolvedNetlistPath = resolvePath(netlist)
      validNetlistPath <- IO.fromEither(
        VerilogPath
          .from(resolvedNetlistPath)
          .leftMap(new IllegalArgumentException(_))
      )

      inputCtx = InputCTX(Some(validDefPath), Some(validNetlistPath))
    } yield FloorplanContext(modifiedConfig, inputCtx)
  }
}

object DSL {
  extension (ioCtx: IO[InitialContext])
    def synthesize(
        target: String = "",
        configure: SynthSettings => SynthSettings = identity
    ): IO[SynContext] = {
      ioCtx.flatMap { ctx =>
        val configWithTarget = Option
          .when(target.nonEmpty)(
            ctx.config.copy(foundry = ctx.config.foundry.copy(name = target))
          )
          .getOrElse(ctx.config)

        val newSettings = configure(ctx.synthSettings)

        val newCtx = ctx.copy(
          config = configWithTarget,
          synthSettings = newSettings
        )
        Flow.runSynthesis(newCtx)
      }
    }

    def synthesize(configure: SynthSettings => SynthSettings): IO[SynContext] =
      synthesize("", configure)

  extension (ioCtx: IO[SynContext])
    def floorplan(
        utilization: Double
    ): IO[FloorplanContext] = {
      ioCtx.flatMap { ctx =>
        // Update core utilization
        val newConfig = ctx.config.copy(
          designInfo = ctx.config.designInfo.copy(coreUtilization = utilization)
        )
        Flow.runFloorplan(newConfig, ctx)
      }
    }

  extension (ioCtx: IO[FloorplanContext])
    def place(optimization: Boolean = true): IO[PlaceContext] = {
      ioCtx.flatMap { ctx =>
        Flow.runPlacement(ctx.config, ctx)
      }
    }

  extension (ioCtx: IO[PlaceContext])
    def cts(): IO[CTSContext] = {
      ioCtx.flatMap { ctx =>
        Flow.runCTS(ctx.config, ctx)
      }
    }

  extension (ioCtx: IO[CTSContext])
    def legalize(): IO[LegalizationContext] = {
      ioCtx.flatMap { ctx =>
        Flow.runLegalization(ctx.config, ctx)
      }
    }

    /** Runs routing. Automatically runs legalization if called on CTSContext.
      */
    @targetName("routeFromCTS")
    def route(): IO[RouteContext] = {
      ioCtx.flatMap { ctx =>
        for {
          lgCtx <- Flow.runLegalization(ctx.config, ctx)
          rtCtx <- Flow.runRouting(lgCtx.config, lgCtx)
        } yield rtCtx
      }
    }

  extension (ioCtx: IO[LegalizationContext])
    @targetName("routeFromLegalization")
    def route(): IO[RouteContext] = {
      ioCtx.flatMap { ctx =>
        Flow.runRouting(ctx.config, ctx)
      }
    }

  extension (ioCtx: IO[RouteContext])
    def signoff(): IO[Unit] = {
      ioCtx.flatMap { ctx =>
        IO.println(
          s"[Signoff] Design ${ctx.config.designName} completed successfully."
        )
      }
    }
}
