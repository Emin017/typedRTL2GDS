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

  def runtimeEnv(config: InputConfig, settings: SynthSettings) = Seq(
    "CLK_FREQ_MHZ" -> config.designInfo.clkFreqMHz.toString,
    "FOUNDARY_PATH" -> config.foundry.pdkDir,
    "SYNTH_FLATTEN" -> (if (settings.flatten) "1" else "0"),
    "SYNTH_CLOCK_GATING" -> (if (settings.clockGating) "1" else "0"),
    "SYNTH_MAX_FANOUT" -> settings.maxFanout.toString
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
