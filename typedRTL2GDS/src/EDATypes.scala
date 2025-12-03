package rtl2gds.types

import cats.effect.*
import cats.syntax.all.*

object EDATypes {
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
}
