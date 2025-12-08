package rtl2gds.dsl

import rtl2gds.dsl.types.TclBool
import rtl2gds.dsl.types.TclValue.*
import rtl2gds.dsl.types.TclVariable

import scala.collection.mutable.ListBuffer

sealed trait TclNode

trait Combinable[T]:
  extension (a: T) def +(b: T): T

case class TclPuts(msg: String, position: String) extends TclNode

// New trait for conditions that can be compiled to Tcl expressions
sealed trait TclCondition {
  def toTclCondition: String
}

object TclCondition {
  case class BooleanCondition(bool: TclBool) extends TclCondition {
    def toTclCondition = bool.value
  }

  case class VariableCondition(variable: TclVariable, expectedValue: TclBool)
      extends TclCondition {
    def toTclCondition =
      s"${variable.value} == ${expectedValue.value}"
  }

  case class ExpressionCondition(expr: String) extends TclCondition {
    def toTclCondition = expr
  }
}

object TclNode:
  case class Set(key: String, value: String, position: String) extends TclNode
  case class AddCommand(cmd: String, args: Seq[String], position: String)
      extends TclNode
  case class Block(nodes: List[TclNode]) extends TclNode
  case class IfStatement(
      condition: TclCondition,
      thenBranch: TclNode.Block,
      elseBranch: Option[TclNode.Block],
      position: String
  ) extends TclNode

class IfBuilder(condition: TclCondition, position: String) {

  def apply(
      thenBlock: ScriptBuilder ?=> Unit
  )(using b: Option[ScriptBuilder] = None): IfBuilderWithThen = {
    val thenBuilder = new ScriptBuilder
    thenBlock(using thenBuilder)
    val thenNode = TclNode.Block(thenBuilder.result)

    b.foreach { builder =>
      val ifNode = TclNode.IfStatement(condition, thenNode, None, position)
      builder.add(ifNode)
    }

    new IfBuilderWithThen(condition, thenNode, position)
  }
}

class IfBuilderWithThen(
    condition: TclCondition,
    thenNode: TclNode.Block,
    position: String
) {

  def Else(
      elseBlock: ScriptBuilder ?=> Unit
  )(using b: Option[ScriptBuilder] = None): Unit = {
    val elseBuilder = new ScriptBuilder
    elseBlock(using elseBuilder)
    val elseNode = TclNode.Block(elseBuilder.result)

    b.foreach { builder =>
      val ifNode =
        TclNode.IfStatement(condition, thenNode, Some(elseNode), position)
      // Remove the previously added if statement and add the complete if-else statement
      builder.removeLastNode()
      builder.add(ifNode)
    }
  }
}

class ScriptBuilder:
  private val nodes = ListBuffer[TclNode]()
  def add(node: TclNode): Unit = nodes += node
  def result: List[TclNode] = nodes.toList
  def removeLastNode(): Unit = if (nodes.nonEmpty) nodes.dropRightInPlace(1)

object ScriptBuilder:
  given (using b: ScriptBuilder): Option[ScriptBuilder] = Some(b)
