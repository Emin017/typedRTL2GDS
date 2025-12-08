package rtl2gds.dsl

import scala.collection.mutable.ListBuffer

//trait TclTypes {}

sealed trait TclNode

trait Combinable[T]:
  extension (a: T) def +(b: T): T

case class TclPuts(msg: String, position: String) extends TclNode

object TclNode:
  case class Set(key: String, value: String, position: String) extends TclNode
  case class AddCommand(cmd: String, args: Seq[String], position: String)
      extends TclNode
  case class Block(nodes: List[TclNode]) extends TclNode

class ScriptBuilder:
  private val nodes = ListBuffer[TclNode]()
  def add(node: TclNode): Unit = nodes += node
  def result: List[TclNode] = nodes.toList
