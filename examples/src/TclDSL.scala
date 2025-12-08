import rtl2gds.dsl.{If, Scripts}
import rtl2gds.dsl.TclApi.*
import rtl2gds.dsl.TclCompiler.*
import rtl2gds.dsl.types.TclVariable

@main def runAdtDemo(): Unit = {

  val program = Scripts {
    val pString = "Hello, Tcl DSL!"

    val s2 = pString.substring(7, 10)

    val _ = addCommand("run_irt", s2, " ")
    val _ = puts(s"Substring: $s2")
    val _ = puts("Start program")
    val _ = set("timeout", 5000)
    val _ = set("mode", "strict")
    val _ = puts("End program")
    val _ = addCommand("run_simulation", "arg1", "arg2")

    val a = TclVariable("a")
    val b = TclVariable("b")
    If(a is b) {
      puts("success!")
    }
  }

  println(s"ADT Structure: $program")
  println("-" * 20)

  val code = genTclScripts(program)
  println(code)
}
