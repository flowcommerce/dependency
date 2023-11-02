package io.flow.dependency.api.lib

import util.DependencySpec

class SimpleScalaParserSpec extends DependencySpec {

  "definesVariable" in {
    SimpleScalaParserUtil.toVariable("var foo = 3") must be(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("val foo = 3") must be(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("lazy var foo = 3") must be(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("lazy val foo = 3") must be(
      Some(SimpleScalaParserUtil.Variable("foo", "3"))
    )

    SimpleScalaParserUtil.toVariable("foo := 3") must be(None)
  }

  "toVariable tolerates spaces" in {
    SimpleScalaParserUtil.toVariable("""   val     foo = "bar"""") must be(
      Some(SimpleScalaParserUtil.Variable("foo", "bar"))
    )

    SimpleScalaParserUtil.toVariable("""   lazy  val     foo = "bar"""") must be(
      Some(SimpleScalaParserUtil.Variable("foo", "bar"))
    )
  }

}
