package com.bryzek.dependency.api.lib

import com.bryzek.dependency.v0.models.BinaryForm
import org.specs2.mutable._

class SimpleScalaParserSpec extends Specification {

  "definesVariable" in {
    SimpleScalaParserUtil.toVariable("var foo = 3") should beEqualTo(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("val foo = 3") should beEqualTo(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("lazy var foo = 3") should beEqualTo(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("lazy val foo = 3") should beEqualTo(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("foo := 3") should be(None)
  }

  "toVariable tolerates spaces" in {
    SimpleScalaParserUtil.toVariable("""   val     foo = "bar"""") should beEqualTo(
      Some(SimpleScalaParserUtil.Variable("foo", "bar"))
    )

    SimpleScalaParserUtil.toVariable("""   lazy  val     foo = "bar"""") should beEqualTo(
      Some(SimpleScalaParserUtil.Variable("foo", "bar"))
    )
  }

}
