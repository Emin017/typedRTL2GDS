package rtl2gds

import cats.effect.*
import cats.syntax.all.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.yaml.parser
import mainargs.*
import rtl2gds.flow.Flow

import scala.io.Source

import types.ContextTypes.InitialContext

case class DesignInfo(
    clkPortName: String,
    clkFreqMHz: Int,
    coreUtilization: Double
)
case class Foundry(
    name: String,
    pdkDir: String,
    tapCell: String = "TAPCELL_DEFAULT",
    tapDistance: Int = 10,
    endCap: String = "ENDCAP_DEFAULT"
)
case class InputConfig(
    designName: String,
    rtlFile: String,
    enableFileList: Boolean,
    resultDir: String,
    sdcFile: String,
    designInfo: DesignInfo,
    foundry: Foundry
)

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

  /** Runs the complete EDA flow based on the provided CLI arguments.
    * @param cliArgs
    *   Parsed command-line arguments containing the config file path.
    * @return
    *   An IO action resulting in an ExitCode indicating success or failure.
    */
  def runFlow(cliArgs: CliArgs): IO[ExitCode] = {
    for {
      _ <- IO.println(s"Loading config from: ${cliArgs.configPath}")
      config <- loadConfig(cliArgs.configPath)

      initialCtx <- InitialContext.fromConfig(config)

      _ <- IO.println(s"Initialized Flow for design: ${config.designName}")

      synCtx <- Flow.runSynthesis(initialCtx)
      fpCtx <- Flow.runFloorplan(config, synCtx)
      plCtx <- Flow.runPlacement(config, fpCtx)
      ctsCtx <- Flow.runCTS(config, plCtx)
      lgCtx <- Flow.runLegalization(config, ctsCtx)
      rtCtx <- Flow.runRouting(config, lgCtx)

      _ <- IO.println("Flow completed successfully.")
      _ <- IO.println(s"Final step output def: ${rtCtx.outputCtx.defPath
          .map(_.value)
          .getOrElse("N/A")}")

    } yield ExitCode.Success
  }

  /** Loads and parses the YAML configuration file.
    * @param path
    *   Path to the YAML config file.
    * @return
    *   An IO action resulting in the parsed InputConfig.
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
