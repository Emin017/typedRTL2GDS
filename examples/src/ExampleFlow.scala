package rtl2gds

import cats.effect.*
import rtl2gds.dsl.*
import rtl2gds.dsl.DSL.*

object ExampleFlow extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    // Define a design flow using the DSL, overriding some config parameters
    // This is just building the execution plan, not running it yet
    val flow = Design("GCD_Example")
      .input(
        verilog = Some("gcd.v"),
        config = "typedRTL2GDS/test/example.yaml"
      )
      .synthesize(
        target = "ics55",
        configure = { config =>
          config.flatten(true).maxFanout(100)
        }
      )
      .floorplan(utilization = 0.6) // Override core utilization
      .place()
      .cts()
      .route()
      .signoff()

    IO.println("Starting Custom DSL Flow...") *>
      // Run the defined flow
      flow.as(ExitCode.Success)
  }
}
