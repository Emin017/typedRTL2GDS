package rtl2gds.configs

import rtl2gds.flow.FlowContext
import rtl2gds.utils.ResourceExtractor

trait GlobalConfigs {
  // TODO: make this configurable via CLI or config file
  // This implement is not pure, but it's acceptable for global config
  lazy val scriptsPath: String = ResourceExtractor.ensureScripts().toString

  def yosysScriptsPath: String = s"${scriptsPath}/yosys"

  def iEDAScriptsPath: String = s"${scriptsPath}/ieda"

  def genCommonEnv[T <: FlowContext](i: T) = {
    val foundryEnv = if (i.config.foundry.name == "ics55") {
      val pdkDir = i.config.foundry.pdkDir
      Seq(
        "TECH_LEF" -> s"$pdkDir/prtech/techLEF/N551P6M_ieda.lef",
        "LEF_STDCELL" -> s"$pdkDir/IP/STD_cell/ics55_LLSC_H7C_V1p10C100/ics55_LLSC_H7CL/lef/ics55_LLSC_H7CL_ieda.lef",
        "LIB_STDCELL" -> s"$pdkDir/IP/STD_cell/ics55_LLSC_H7C_V1p10C100/ics55_LLSC_H7CL/liberty/ics55_LLSC_H7CL_typ_tt_1p2_25_nldm.lib"
      )
    } else {
      Seq.empty
    }

    Seq(
      "IEDA_CONFIG_DIR" -> s"$iEDAScriptsPath/iEDA_config",
      "IEDA_TCL_SCRIPT_DIR" -> s"$iEDAScriptsPath/script",
      "RESULT_DIR" -> i.config.resultDir,
      "OUTPUT_DEF" -> i.outputCtx.defPath.map(_.value).getOrElse(""),
      "OUTPUT_VERILOG" -> i.outputCtx.verilogFile.map(_.value).getOrElse("")
    ) ++ foundryEnv
  }
}
