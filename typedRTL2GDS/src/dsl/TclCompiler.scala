package rtl2gds.dsl

object TclCompiler:
  import TclNode.*

  def genTclScripts(node: TclNode, indent: Int = 0): String =
    val prefix = "  " * indent

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

      case TclNode.IfStatement(condition, thenBranch, elseBranch, position) =>
        val conditionStr = condition.toTclCondition
        val thenStr = genTclScripts(thenBranch, indent + 1)

        val elseStr = elseBranch match {
          case Some(elseBranch) =>
            s"\n${prefix}else {\n${genTclScripts(elseBranch, indent + 1)}\n${prefix}}"
          case None => ""
        }

        s"${prefix}if {${conditionStr}} {\n${thenStr}\n${prefix}}${elseStr} ;# From $position"
