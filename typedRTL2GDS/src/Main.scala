package rtl2gds

import cats.effect.*
import cats.syntax.all.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.yaml.parser
import mainargs.*
import rtl2gds.flow.Flow
import rtl2gds.flow.InitialContext
import rtl2gds.types.EDATypes.GdsPath

import scala.io.Source

case class DesignInfo(
    clkPortName: String,
    clkFreqMHz: Int,
    coreUtilization: Double
)
case class Foundry(name: String, pdkDir: String)
case class InputConfig(
    designName: String,
    rtlFile: String,
    enableFileList: Boolean,
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

  /** Main runFlow flow: Load config -> Synthesis -> Place & Route
    */
  def runFlow(cliArgs: CliArgs): IO[ExitCode] = {
    for {
      _ <- IO.println(s"Loading config from: ${cliArgs.configPath}")
      config <- loadConfig(cliArgs.configPath)

      initialCtx <- InitialContext.fromConfig(config)

      _ <- IO.println(s"Initialized Flow for design: ${config.designName}")

      synCtx <- Flow.runSynthesis(initialCtx)
      pnrCtx <- Flow.runPlaceAndRoute(synCtx)

      _ <- IO.println("Flow completed successfully.")
      _ <- IO.println(s"Final GDS: ${pnrCtx.gdsFile.value}")

    } yield ExitCode.Success
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
