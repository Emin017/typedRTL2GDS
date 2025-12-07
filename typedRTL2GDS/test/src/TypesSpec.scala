package rtl2gds

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rtl2gds.types.EDATypes.InputLef

class TypesSpec extends AnyFlatSpec with Matchers {

  "Input Lef" should "support combine operation" in {

    val lef1 = InputLef("/path/to/lef1.lef")
    val lef2 = InputLef("/path/to/lef2.lef")

    val combined = lef1 + lef2

    combined.value should contain theSameElementsAs List(
      "/path/to/lef1.lef",
      "/path/to/lef2.lef"
    )
  }
}
