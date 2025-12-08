package rtl2gds.dsl

object TclCompiler:
  import TclNode.*

  def genTclScripts(node: TclNode, indent: Int = 0): String =
    val prefix = "  " * indent

    //TODO: Remove global node, support parallelism if needed?
    node match
      case Block(nodes) =>
        nodes.map(n => genTclScripts(n, indent)).mkString("\n")

      case TclPuts(msg, position) =>
        s"${prefix}puts \"$msg\" ;# From $position"

      case Set(k, v, line) =>
        s"${prefix}set $k $v ;# From $line"

      case AddCommand(c, args, position) =>
        val argsStr = args.mkString(" ")
        s"${prefix}$c $argsStr ;# From $position"
