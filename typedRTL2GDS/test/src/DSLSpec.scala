package rtl2gds

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import dsl.Design

import java.nio.file.Files
import java.io.File
import rtl2gds.types.EDATypes.VerilogPath

class DSLSpec extends AnyFlatSpec with Matchers {

  val configContent =
    """
      |designName: test_design
      |rtlFile: ./src/test.v
      |enableFileList: false
      |resultDir: ./results
      |sdcFile: ./constraints.sdc
      |foundry:
      |  name: test_foundry
      |  pdkDir: /pdk
      |  techLef: /pdk/tech.lef
      |  stdCellLef: /pdk/std.lef
      |  stdCellLib: /pdk/std.lib
      |  tapCell: TAP_CELL
      |  tapDistance: 10
      |  endCap: END_CAP
      |designInfo:
      |  clkPortName: clk
      |  clkFreqMHz: 100
      |  coreUtilization: 0.6
      |""".stripMargin

  def withTempConfig(testCode: String => Any): Any = {
    val file = File.createTempFile("test_config", ".yaml")
    Files.write(file.toPath, configContent.getBytes)
    try {
      testCode(file.getAbsolutePath)
    } finally {
      file.delete()
    }
  }

  "Design DSL" should "load configuration from YAML" in withTempConfig {
    configPath =>
      val design = Design("test_design")
      val initialCtx = design.input(config = configPath).unsafeRunSync()

      initialCtx.config.designName shouldBe "test_design"
      initialCtx.config.rtlFile shouldBe "./src/test.v"
      initialCtx.config.sdcFile shouldBe "./constraints.sdc"
  }

  it should "override verilog file path" in withTempConfig { configPath =>
    val design = Design("test_design")
    val overrideVerilog = "/abs/path/to/override.v"
    val initialCtx = design
      .input(
        config = configPath,
        verilog = Some(overrideVerilog)
      )
      .unsafeRunSync()

    initialCtx.config.rtlFile shouldBe overrideVerilog
    // Check if the resolved path in InitialContext is correct
    initialCtx.inputRtl.value shouldBe overrideVerilog
  }

  it should "override SDC file path" in withTempConfig { configPath =>
    val design = Design("test_design")
    val overrideSdc = "./override.sdc"
    val initialCtx = design
      .input(
        config = configPath,
        sdc = Some(overrideSdc)
      )
      .unsafeRunSync()

    initialCtx.config.sdcFile shouldBe overrideSdc
  }

  it should "resolve relative paths correctly" in withTempConfig { configPath =>
    val design = Design("test_design")
    val initialCtx = design.input(config = configPath).unsafeRunSync()

    val expectedPath = System.getProperty("user.dir") + "/src/test.v"
    initialCtx.inputRtl.value shouldBe expectedPath
  }

  it should "support method chaining syntax" in withTempConfig { configPath =>
    import dsl.DSL.*

    val flow = Design("test_chain")
      .input(config = configPath)
      .synthesize(target = "sky130", configure = _.flatten(false).maxFanout(50))
      .floorplan(utilization = 0.5)
      .place()
      .cts()
      .route()
      .signoff()

    assert(flow != null)
  }

  it should "support declarative configuration block" in withTempConfig {
    configPath =>
      import dsl.DSL.*

      val flow = Design("test_block")
        .input(config = configPath)
        .synthesize { config =>
          config
            .flatten(true)
            .clockGating(true)
            .maxFanout(10)
        }
        .floorplan(utilization = 0.7)

      assert(flow != null)
  }

  it should "support starting from Netlist (skipping synthesis)" in withTempConfig {
    configPath =>
      val netlistPath = "/abs/path/to/netlist.v"
      val synCtx = Design("test_netlist")
        .inputNetlist(netlist = netlistPath, config = configPath)
        .unsafeRunSync()

      synCtx.netlist.value shouldBe netlistPath
  }

  it should "support starting from Netlist and chaining floorplan" in withTempConfig {
    configPath =>
      import dsl.DSL.*

      val netlistPath = "/abs/path/to/netlist.v"
      val flow = Design("test_netlist_chain")
        .inputNetlist(netlist = netlistPath, config = configPath)
        .floorplan(utilization = 0.6)

      assert(flow != null)
  }

  it should "support starting from DEF (skipping floorplan)" in withTempConfig {
    configPath =>
      val defPath = "/abs/path/to/design.def"
      val netlistPath = "/abs/path/to/netlist.v"

      val fpCtx = Design("test_def")
        .inputDef(defFile = defPath, netlist = netlistPath, config = configPath)
        .unsafeRunSync()

      fpCtx.inputCtx.defPath.map(_.value) shouldBe Some(defPath)
      fpCtx.inputCtx.verilogFile.map(_.value) shouldBe Some(netlistPath)
  }

  it should "support starting from DEF and chaining placement" in withTempConfig {
    configPath =>
      import dsl.DSL.*

      val defPath = "/abs/path/to/design.def"
      val netlistPath = "/abs/path/to/netlist.v"

      val flow = Design("test_def_chain")
        .inputDef(defFile = defPath, netlist = netlistPath, config = configPath)
        .place()

      assert(flow != null)
  }
}
