package rtl2gds

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl2gds.dsl.*
import rtl2gds.dsl.TclApi.*
import rtl2gds.dsl.types.{TclBool, TclVariable}

class IfStatementSpec extends AnyFlatSpec with Matchers {

  "If statement" should "compile simple if without else" in {
    val script = Scripts {
      val a = TclVariable("a")
      If(a is TclBool(true)) {
        puts("success!")
      }
    }

    val tclCode = TclCompiler.genTclScripts(script)
    println("Generated TCL:")
    println(tclCode)

    tclCode should include("if {$a == 1}")
    tclCode should include("puts \"success!\"")
  }

}
