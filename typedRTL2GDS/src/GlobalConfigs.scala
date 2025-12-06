package rtl2gds.configs

import rtl2gds.InputConfig
import rtl2gds.types.ContextTypes.FlowContext
import rtl2gds.utils.ResourceExtractor

trait GlobalConfigs {
  // TODO: make this configurable via CLI or config file
  // This implement is not pure, but it's acceptable for global config
  private lazy val scriptsPath: String =
    ResourceExtractor.ensureScripts().toString

  def yosysScriptsPath: String = s"$scriptsPath/yosys"

  def iEDAScriptsPath: String = s"$scriptsPath/ieda"

  def genCommonEnv[T <: FlowContext](c: InputConfig, i: T) = {
    val foundryEnv = Seq(
      "TECH_LEF" -> c.foundry.techLef,
      "LEF_STDCELL" -> c.foundry.stdCellLef,
      "LIB_STDCELL" -> c.foundry.stdCellLib
    )

    Seq(
      "IEDA_CONFIG_DIR" -> s"$iEDAScriptsPath/iEDA_config",
      "IEDA_TCL_SCRIPT_DIR" -> s"$iEDAScriptsPath/script",
      "RESULT_DIR" -> i.config.resultDir,
      "OUTPUT_DEF" -> i.outputCtx.defPath.map(_.value).getOrElse(""),
      "OUTPUT_VERILOG" -> i.outputCtx.verilogFile.map(_.value).getOrElse(""),
      "SDC_FILE" -> i.config.sdcFile
    ) ++ foundryEnv
  }
}
