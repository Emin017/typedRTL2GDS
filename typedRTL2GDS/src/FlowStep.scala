package rtl2gds.flow

import rtl2gds.InputConfig
import rtl2gds.types.EDATypes.{DefPath, VerilogPath}
import rtl2gds.types.ContextTypes.*

trait FlowStep[In <: FlowContext, Out <: FlowContext] {
  def stepName: String

  def scriptRelativePath: String

  def stepEnv(c: InputConfig, i: InputCTX): Seq[(String, String)]

  def construct(c: InputConfig, i: InputCTX): Out
}

object FlowStep {

  /** Defines the Backend Flow Steps for iEDA EDA Toolchain: Floorplan ->
    *
    * Placement -> Clock Tree Synthesis -> Legalization -> Routing
    *
    * Each step defines the necessary environment variables and constructs the
    * corresponding FlowContext. FlowSteps accept [[InputConfig]] and
    * [[InputCTX]] to generate the required context for the next step. And the
    * type parameters In and Out ensure type safety between steps, which also
    * defines the order of execution.
    *
    * If you want to add more steps, just define more given instances here,
    * like:
    * {{{
    * given FlowStep[PreviousContext, NewContext] with {
    *   // implement the required methods
    * }
    * }}}
    */
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

  given FlowStep[CTSContext, LegalizationContext] with {
    def stepName = "Legalization"

    def scriptRelativePath = "script/iPL_script/run_iPL_legalization.tcl"

    def stepEnv(c: InputConfig, i: InputCTX): Seq[(String, String)] = Seq(
      "INPUT_DEF" -> i.defPath.map(_.value).getOrElse("")
    )

    def construct(c: InputConfig, i: InputCTX) =
      LegalizationContext(c, i)
  }

  given FlowStep[LegalizationContext, RouteContext] with {
    def stepName = "Routing"

    def scriptRelativePath = "script/iRT_script/run_iRT.tcl"

    def stepEnv(c: InputConfig, i: InputCTX): Seq[(String, String)] = Seq(
      "INPUT_DEF" -> i.defPath.map(_.value).getOrElse("")
    )

    def construct(c: InputConfig, i: InputCTX) =
      RouteContext(c, i)
  }

}
