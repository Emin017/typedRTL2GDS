package rtl2gds.dsl.types

trait Operation[T <: TclValue]:
  def value: T
  def is(other: T): T
  def not(other: T): T

// Equality operations for TclValue types
trait Equable[L, R, Out]:
  extension (a: L)
    infix def is(b: R): Out
    infix def isNot(b: R): Out
