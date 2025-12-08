package rtl2gds.dsl.types

import rtl2gds.dsl.TclCondition

sealed trait TclBase

opaque type TclValue = String
object TclValue:
  def apply(s: String): TclValue = s
  extension (t: TclValue) def value: String = t

opaque type TclComparable <: TclValue = String
object TclComparable:
  given Equable[TclComparable, TclComparable, TclCondition] with
    extension (a: TclComparable)
      infix def is(b: TclComparable): TclCondition =
        Option
          .when(a.startsWith("$"))(
            TclCondition.ExpressionCondition(s"${a} == ${b}")
          )
          .getOrElse {
            TclCondition.BooleanCondition(
              Option.when(a == b)(TclBool(true)).getOrElse(TclBool(false))
            )
          }

      infix def isNot(b: TclComparable): TclCondition =
        Option
          .when(a.startsWith("$"))(
            TclCondition.ExpressionCondition(s"${a} != ${b}")
          )
          .getOrElse {
            TclCondition.BooleanCondition(
              Option
                .when(a != b)(TclBool(true))
                .getOrElse(TclBool(false))
            )
          }

opaque type TclNumber <: TclValue = String
object TclNumber:
  def apply(n: Int): TclNumber = n.toString

opaque type TclBool <: TclComparable = String
object TclBool:
  def apply(b: Boolean): TclBool = if (b) "1" else "0"

opaque type TclVariable <: TclComparable = String
object TclVariable:
  def apply(name: String): TclVariable = s"$$$name"

opaque type TclExpression <: TclValue = String
object TclExpression:
  def apply(expr: String): TclExpression = s"[$expr]"
