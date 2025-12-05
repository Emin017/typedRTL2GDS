package rtl2gds.types

import cats.effect.*
import cats.syntax.all.*
import rtl2gds.InputConfig
import rtl2gds.types.EDATypes.{DefPath, VerilogPath}

object ContextTypes {

  /** Context for input artifacts to a flow stage.
    *
    * @param defPath
    *   Optional DEF file path.
    * @param verilogFile
    *   Optional Verilog file path.
    */
  case class InputCTX(
      defPath: Option[DefPath],
      verilogFile: Option[VerilogPath]
      // Add more artifacts as needed
  ) {

    /** Validates that at least one input artifact is provided.
      * @return
      *   Either an error message or Unit if valid.
      */
    def validate: Either[String, Unit] = Either.cond(
      defPath.isDefined || verilogFile.isDefined,
      (),
      "At least one input artifact (DEF or Verilog) must be provided."
    )
  }

  /** Context for output artifacts from a flow stage.
    *
    * @param defPath
    *   Optional DEF file path.
    * @param verilogFile
    *   Optional Verilog file path.
    */
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

  /** Abstract Flow Context
    */
  abstract class FlowContext {
    def config: InputConfig

    def validate: Either[String, Unit]

    def inputCtx: InputCTX

    def outputCtx: OutputCTX
  }

  /** Abstract Backend Flow Context
    * @param ctx
    *   Input context containing artifacts from the previous stage
    *
    * @example
    *   Use this abstract class to define contexts for backend stages. These
    *   context types are required by the FlowStep typeclass to ensure type
    *   safety between steps. For example, to define a FloorplanContext, you can
    *   do:
    *   {{{
    *   case class FloorplanContext(c: InputConfig, inputCtx: InputCTX)
    *       extends BackendFlowContext(inputCtx) {
    *     override def backendStep: String = "Floorplan" //
    *     def config: InputConfig = c
    *     def validate: Either[String, Unit] = ??? // implement validation logic
    *     def outputCtx: OutputCTX = ??? // implement output context logic
    *   }}}
    *
    * Then you can create a FlowStep instance for [[FloorplanContext]] as shown
    * in the [[rtl2gds.flow.FlowStep]] object.
    */
  abstract class BackendFlowContext[T <: InputCTX](
      val ctx: T
  ) extends FlowContext {
    def backendStep: String

    def backendOutArtifacts: OutputCTX = OutputCTX(
      defPath = Some(DefPath(s"${config.designName}_$backendStep.def")),
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

    /** Creates an InitialContext from the provided configuration.
      * @param config
      *   Input configuration containing design parameters.
      * @return
      *   An IO action resulting in the InitialContext.
      */
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

  /** Synthesis Context
    * @param initial
    *   Initial context containing input RTL and configuration
    * @param netlist
    *   Generated netlist file path
    */
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

  /** Floorplan Context
    * @param c
    *   Input configuration
    * @param inputCtx
    *   Input context containing artifacts from the previous stage
    */
  case class FloorplanContext(c: InputConfig, inputCtx: InputCTX)
      extends BackendFlowContext(inputCtx) {
    override def backendStep: String = "Floorplan"

    def config: InputConfig = c

    def validate: Either[String, Unit] = inputCtx.validate

    def outputCtx: OutputCTX = backendOutArtifacts
  }

  /** Placement Context
    * @param c
    *   Input configuration
    * @param inputCtx
    *   Input context containing artifacts from the previous stage
    */
  case class PlaceContext(c: InputConfig, inputCtx: InputCTX)
      extends BackendFlowContext(inputCtx) {
    override def backendStep: String = "Placement"

    def config: InputConfig = c

    def validate: Either[String, Unit] = inputCtx.validate

    def outputCtx: OutputCTX = backendOutArtifacts
  }

  /** Clock Tree Synthesis Context
    * @param c
    *   Input configuration
    * @param inputCtx
    *   Input context containing artifacts from the previous stage
    */
  case class CTSContext(c: InputConfig, inputCtx: InputCTX)
      extends BackendFlowContext(inputCtx) {
    override def backendStep: String = "ClockTreeSynthesis"

    def config: InputConfig = c

    def validate: Either[String, Unit] = inputCtx.validate

    def outputCtx: OutputCTX = backendOutArtifacts
  }

  /** Legalization Context
    * @param c
    *   Input configuration
    * @param inputCtx
    *   Input context containing artifacts from the previous stage
    */
  case class LegalizationContext(c: InputConfig, inputCtx: InputCTX)
      extends BackendFlowContext(inputCtx) {
    override def backendStep: String = "Legalization"

    def config: InputConfig = c

    def validate: Either[String, Unit] = inputCtx.validate

    def outputCtx: OutputCTX = backendOutArtifacts
  }

  /** Routing Context
    * @param c
    *   Input configuration
    * @param inputCtx
    *   Input context containing artifacts from the previous stage
    */
  case class RouteContext(c: InputConfig, inputCtx: InputCTX)
      extends BackendFlowContext(inputCtx) {
    override def backendStep: String = "Routing"

    def config: InputConfig = c

    def validate: Either[String, Unit] = inputCtx.validate

    def outputCtx: OutputCTX = backendOutArtifacts
  }
}
