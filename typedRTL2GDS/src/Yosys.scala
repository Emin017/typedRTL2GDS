package rtl2gds

import rtl2gds.types.EDATypes.VerilogPath

object Yosys {
  def artifacts(designName: String, resultDir: String): (String, String) = {
    val synthResDir = s"$resultDir/synthesis"
    Tuple2(
      synthResDir,
      s"$synthResDir/${designName}_nl.v"
    )
  }

  def runtimeEnv(clkFreqMHz: Int, foundryPath: String) = Seq(
    "CLK_FREQ_MHZ" -> clkFreqMHz.toString,
    "FOUNDARY_PATH" -> foundryPath
  )

  def synthCommand(
      config: InputConfig,
      scriptDir: String,
      pdkScriptDir: String,
      rtlFile: VerilogPath,
      outputNetList: String,
      outputDir: String
  ): String =
    s"echo tcl $scriptDir/synth.tcl ${config.designName} $pdkScriptDir ${rtlFile.value} $outputNetList | yosys -g -l $outputDir/yosys_synth.log -s -"
}
