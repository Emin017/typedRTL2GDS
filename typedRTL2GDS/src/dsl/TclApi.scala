package rtl2gds.dsl

object TclApi:

  private inline def putsMacro(msg: String): TclPuts = ${
    TclMacros.putsImpl('msg)
  }
  private inline def setMacro(key: String, value: Any): TclNode = ${
    TclMacros.setImpl('key, 'value)
  }
  private inline def addCommandMacro(cmd: String, args: Seq[String]): TclNode =
    ${ TclMacros.commandImpl('cmd, 'args) }

  inline def puts(msg: String)(using b: Option[ScriptBuilder] = None): TclPuts =
    val node = putsMacro(msg)
    b.foreach(_.add(node))
    node

  inline def set(key: String, value: Any)(using
      b: Option[ScriptBuilder] = None
  ): TclNode =
    val node = setMacro(key, value)
    b.foreach(_.add(node))
    node

  inline def addCommand(cmd: String, args: String*)(using
      b: Option[ScriptBuilder] = None
  ): TclNode =
    val node = addCommandMacro(cmd, args)
    b.foreach(_.add(node))
    node

  // helper methods to create blocks of TclNodes
  def script(nodes: TclNode*): TclNode.Block =
    TclNode.Block(nodes.toList)

  def scripts(block: ScriptBuilder ?=> Unit): TclNode.Block =
    val builder = new ScriptBuilder
    block(using builder)
    TclNode.Block(builder.result)

object Scripts:
  def apply(block: ScriptBuilder ?=> Unit): TclNode.Block =
    TclApi.scripts(block)

object If {
  private inline def ifMacro(condition: TclCondition): IfBuilder = ${
    TclMacros.ifImpl('condition)
  }

  inline def apply(condition: TclCondition): IfBuilder =
    ifMacro(condition)
}
