import rtl2gds.dsl.{Set, Puts, Command, Scripts}
import rtl2gds.dsl.TclCompiler.*

@main def runAdtDemo(): Unit = {

  val program = Scripts {
    val pString = "Hello, Tcl DSL!"

    val s2 = pString.substring(7, 10)

    val _ = Command("run_irt", s2, " ")
    val _ = Puts(s"Substring: $s2")
    val _ = Puts("Start program")
    val _ = Set("timeout", 5000)
    val _ = Set("mode", "strict")
    val _ = Puts("End program")
    Command("run_simulation", "arg1", "arg2")
  }

  println(s"ADT Structure: $program")
  println("-" * 20)

  val code = genTclScripts(program)
  println(code)
}
