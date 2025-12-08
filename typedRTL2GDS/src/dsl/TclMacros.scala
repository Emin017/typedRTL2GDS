package rtl2gds.dsl

import scala.quoted.*

object TclMacros:
  import rtl2gds.dsl.TclNode.*

  private inline def _sourceLocator(using Quotes) =
    getFileAndLine.value match
      case Some((fileName, lineNumber)) => (Expr(fileName), Expr(lineNumber))
      case None => ('{ "" }, Expr(0)) // Fallback, won't happen in practice

  def putsImpl(msg: Expr[String])(using Quotes): Expr[TclPuts] = {
    val (fileNameExpr, lineNumExpr) = _sourceLocator

    '{ TclPuts($msg, $fileNameExpr + ":" + $lineNumExpr) }
  }

  def setImpl(key: Expr[String], value: Expr[Any])(using Quotes): Expr[Set] = {
    val (fileNameExpr, lineNumExpr) = _sourceLocator

    '{ TclNode.Set($key, $value.toString, $fileNameExpr + ":" + $lineNumExpr) }
  }

  def commandImpl(cmd: Expr[String], args: Expr[Seq[String]])(using
      Quotes
  ): Expr[TclNode] = {
    val (fileNameExpr, lineNumExpr) = _sourceLocator

    '{ TclNode.AddCommand($cmd, $args, $fileNameExpr + ":" + $lineNumExpr) }
  }

  private def getFileAndLine(using Quotes): Expr[(String, Int)] =
    import quotes.reflect.*

    val pos = Position.ofMacroExpansion

    val fileName = pos.sourceFile.name

    val lineNumber = pos.startLine + 1

    Expr((fileName, lineNumber))
