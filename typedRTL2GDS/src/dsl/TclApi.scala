package rtl2gds.dsl

private[rtl2gds] object TclApi:

  private inline def putsMacro(msg: String): TclPuts = ${
    TclMacros.putsImpl('msg)
  }
  private inline def setMacro(key: String, value: Any): TclNode = ${
    TclMacros.setImpl('key, 'value)
  }
  private inline def addCommandMacro(cmd: String, args: Seq[String]): TclNode =
    ${ TclMacros.commandImpl('cmd, 'args) }

  inline def puts(msg: String)(using b: ScriptBuilder = null): TclPuts =
    val node = putsMacro(msg)
    if (b != null) b.add(node)
    node

  inline def set(key: String, value: Any)(using
      b: ScriptBuilder = null
  ): TclNode =
    val node = setMacro(key, value)
    if (b != null) b.add(node)
    node

  inline def addCommand(cmd: String, args: String*)(using
      b: ScriptBuilder = null
  ): TclNode =
    val node = addCommandMacro(cmd, args)
    if (b != null) b.add(node)
    node

  // helper methods to create blocks of TclNodes
  def script(nodes: TclNode*): TclNode.Block =
    TclNode.Block(nodes.toList)

  def scripts(block: ScriptBuilder ?=> Unit): TclNode.Block =
    val builder = new ScriptBuilder
    block(using builder)
    TclNode.Block(builder.result)

sealed class Set:
  def apply(key: String, value: Any)(using b: ScriptBuilder = null): TclNode =
    TclApi.set(key, value)

object Set:
  def apply(key: String, value: Any)(using b: ScriptBuilder = null): TclNode =
    new Set().apply(key, value)

object Puts:
  def apply(msg: String)(using b: ScriptBuilder = null): TclPuts =
    TclApi.puts(msg)

object Command:
  def apply(cmd: String, args: String*)(using
      b: ScriptBuilder = null
  ): TclNode =
    TclApi.addCommand(cmd, args*)

object Scripts:
  def apply(block: ScriptBuilder ?=> Unit): TclNode.Block =
    TclApi.scripts(block)
