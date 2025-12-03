package rtl2gds.configs

trait GlobalConfigs {
  def scriptsPath = s"${System.getProperty("user.dir")}/scripts/"

  def yosysScriptsPath: String = s"${scriptsPath}/yosys"
}
