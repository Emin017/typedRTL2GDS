package rtl2gds.types

import cats.effect.*
import cats.syntax.all.*

object EDATypes {
  trait Combinable[T]:
    extension (a: T) def +(b: T): T

  opaque type VerilogPath = String
  object VerilogPath {
    def from(path: String): Either[String, VerilogPath] =
      Either.cond(
        path.endsWith(".v") || path.endsWith(".sv"),
        path,
        s"Invalid Verilog file: '$path' (must end with .v or .sv)"
      )

    def check(s: String): IO[VerilogPath] = IO.fromEither(
      VerilogPath.from(s).leftMap(new IllegalArgumentException(_))
    )

    def apply(s: String): VerilogPath = s

    extension (p: VerilogPath) def value: String = p
  }

  opaque type DefPath = String
  object DefPath {
    def from(path: String): Either[String, DefPath] =
      Either.cond(
        path.endsWith(".def"),
        path,
        s"Invalid DEF file: '$path' (must end with .def)"
      )

    def check(s: String): IO[DefPath] = IO.fromEither(
      DefPath.from(s).leftMap(new IllegalArgumentException(_))
    )

    def apply(s: String): DefPath = s

    extension (p: DefPath) def value: String = p
  }

  opaque type GdsPath = String
  object GdsPath {
    def from(path: String): Either[String, GdsPath] =
      Either.cond(
        path.endsWith(".gds"),
        path,
        s"Invalid GDS file: '$path' (must end with .gds)"
      )

    extension (p: GdsPath) def value: String = p
  }

  opaque type InputLef = List[String]
  object InputLef {
    def apply(path: String): InputLef =
      require(
        !path.exists(_.isWhitespace) && path.endsWith(".lef"),
        s"Invalid LEF file: '$path' (must end with .lef and contain no whitespace)"
      )
      List(path)

    def from(path: String): Either[String, InputLef] =
      Either.cond(
        !path.exists(_.isWhitespace) && path.endsWith(".lef"),
        List(path),
        s"Invalid LEF file: '$path' (must end with .lef and contain no whitespace)"
      )

    def check(s: String): IO[InputLef] = IO.fromEither(
      InputLef.from(s).leftMap(new IllegalArgumentException(_))
    )

    extension (lef: InputLef) def value: List[String] = lef

    given Combinable[InputLef] with
      extension (lef: InputLef)
        def +(other: InputLef): InputLef = {
          lef ++ other
        }
  }
}
