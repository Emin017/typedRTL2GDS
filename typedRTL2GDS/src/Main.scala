package typedRTL2GDS

import cats.effect._
import cats.syntax.all._
import mainargs._
// import java.io.{File, FileReader}
import io.circe._
import io.circe.generic.auto._
import io.circe.yaml.parser
import scala.io.Source
import scala.sys.process._

case class DesignInfo(
    clkPortName: String,
    clkFreqMHz: Int,
    coreUtilization: Double
)
case class InputConfig(
    designName: String,
    rtlFile: String,
    enableFileList: Boolean,
    designInfo: DesignInfo
)

object EDATypes {
  opaque type VerilogPath = String
  object VerilogPath {
    def from(path: String): Either[String, VerilogPath] =
      if (path.endsWith(".v") || path.endsWith(".sv")) Right(path)
      else Left(s"Invalid Verilog file: '$path' (must end with .v or .sv)")

    extension (p: VerilogPath) def value: String = p
  }

  opaque type DefPath = String
  object DefPath {
    def from(path: String): Either[String, DefPath] =
      if (path.endsWith(".def")) Right(path)
      else Left(s"Invalid DEF file: '$path' (must end with .def)")

    extension (p: DefPath) def value: String = p
  }

  opaque type GdsPath = String
  object GdsPath {
    def from(path: String): Either[String, GdsPath] =
      if (path.endsWith(".gds")) Right(path)
      else Left(s"Invalid GDS file: '$path' (must end with .gds)")

    extension (p: GdsPath) def value: String = p
  }
}

import EDATypes._

trait FlowContext {
  def config: InputConfig
  def validate: Either[String, Unit]
}

case class InitialContext(config: InputConfig, inputRtl: VerilogPath)
    extends FlowContext {
  def validate: Either[String, Unit] =
    if (config.designInfo.clkFreqMHz <= 0)
      Left("Clock frequency must be positive")
    else Right(())
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

case class SynContext(initial: InitialContext, defFile: DefPath)
    extends FlowContext {
  def config: InputConfig = initial.config

  def validate: Either[String, Unit] =
    if (
      config.designInfo.coreUtilization <= 0 || config.designInfo.coreUtilization >= 1.0
    )
      Left("Core utilization must be between 0.0 and 1.0")
    else initial.validate
}

case class PnrContext(syn: SynContext, gdsFile: GdsPath) extends FlowContext {
  def config: InputConfig = syn.config

  def validate: Either[String, Unit] = syn.validate
}

object Main extends IOApp {

  @main
  case class CliArgs(
      @arg(name = "config", short = 'c', doc = "Path to the YAML config file")
      configPath: String
  )

  override def run(args: List[String]): IO[ExitCode] = {
    val cliResult = ParserForClass[CliArgs].constructEither(args)

    cliResult match {
      case Left(error) =>
        IO.println(error).as(ExitCode.Error)

      case Right(cliArgs) =>
        runFlow(cliArgs)
    }
  }

  /** Main runFlow flow: Load config -> Synthesis -> Place & Route
    */
  def runFlow(cliArgs: CliArgs): IO[ExitCode] = {
    for {
      _ <- IO.println(s"Loading config from: ${cliArgs.configPath}")
      config <- loadConfig(cliArgs.configPath)

      initialCtx <- InitialContext.fromConfig(config)

      _ <- IO.println(s"Initialized Flow for design: ${config.designName}")

      synCtx <- runSynthesis(initialCtx)
      pnrCtx <- runPlaceAndRoute(synCtx)

      _ <- IO.println(s"Flow completed successfully.")
      _ <- IO.println(s"Final GDS: ${pnrCtx.gdsFile.value}")

    } yield ExitCode.Success
  }

  /** Simulates the Synthesis step. Takes InitialContext, validates it, and
    * produces SynContext with a DEF file.
    */
  def runSynthesis(ctx: InitialContext): IO[SynContext] = {
    for {
      _ <- IO.fromEither(ctx.validate.leftMap(new RuntimeException(_)))

      _ <- IO.println(s"[Synthesis] Reading RTL from: ${ctx.inputRtl.value}")
      _ <- IO.println(
        s"[Synthesis] Target Frequency: ${ctx.config.designInfo.clkFreqMHz} MHz"
      )

      cmd =
        s"echo '[Shell] Running Yosys synthesis on ${ctx.inputRtl.value}...'"
      exitCode <- IO.blocking(cmd.!)

      _ <-
        if (exitCode != 0)
          IO.raiseError(
            new RuntimeException(s"Yosys failed with exit code $exitCode")
          )
        else IO.unit

      defPathStr = s"output/${ctx.config.designName}.def"
      defOutput <- IO.fromEither(
        DefPath.from(defPathStr).leftMap(new IllegalArgumentException(_))
      )

      _ <- IO.println(s"[Synthesis] Generated DEF: ${defOutput.value}")
    } yield SynContext(ctx, defOutput)
  }

  /** Simulates the Place & Route step. Takes SynContext, validates it, and
    * produces PnrContext with a GDS file.
    */
  def runPlaceAndRoute(ctx: SynContext): IO[PnrContext] = {
    for {
      _ <- IO.fromEither(ctx.validate.leftMap(new RuntimeException(_)))

      _ <- IO.println(s"[PnR] Reading DEF from: ${ctx.defFile.value}")
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

  /** Loads and parses the YAML configuration file.
    */
  def loadConfig(path: String): IO[InputConfig] = {
    val acquire = IO(Source.fromFile(path))
    val release = (source: Source) => IO(source.close())

    Resource
      .make(acquire)(release)
      .use { source =>
        for {
          content <- IO.blocking(source.mkString)
          config <- IO.fromEither(
            parser.parse(content).flatMap(_.as[InputConfig])
          )
        } yield config
      }
      .handleErrorWith { error =>
        IO.raiseError(
          new RuntimeException(
            s"Failed to parse config: ${error.getMessage}",
            error
          )
        )
      }
  }
}
