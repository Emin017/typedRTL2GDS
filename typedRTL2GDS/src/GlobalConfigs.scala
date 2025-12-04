package rtl2gds.configs

import rtl2gds.flow.FlowContext
import rtl2gds.utils.ResourceExtractor

trait GlobalConfigs {
  // TODO: make this configurable via CLI or config file
  // This implement is not pure, but it's acceptable for global config
  lazy val scriptsPath: String = ResourceExtractor.ensureScripts().toString

  def yosysScriptsPath: String = s"${scriptsPath}/yosys"

  def iEDAScriptsPath: String = s"${scriptsPath}/ieda"

  def genCommonEnv[T <: FlowContext](i: T) = Seq(
    "IEDA_CONFIG_DIR" -> s"$iEDAScriptsPath/iEDA_config",
    "IEDA_TCL_SCRIPT_DIR" -> s"$iEDAScriptsPath/script",
    "RESULT_DIR" -> i.config.resultDir,
    "OUTPUT_DEF" -> i.outputCtx.defPath.map(_.value).getOrElse(""),
    "OUTPUT_VERILOG" -> i.outputCtx.verilogFile.map(_.value).getOrElse("")
  )
}
