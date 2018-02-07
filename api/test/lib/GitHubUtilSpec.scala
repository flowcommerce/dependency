package io.flow.dependency.api.lib

import io.flow.common.v0.models.Name
import util.DependencySpec

class GithubUtilSpec extends DependencySpec {

  "GithubHelper.parseName" in {
    GithubHelper.parseName("") must be(Name())
    GithubHelper.parseName("  ") must be(Name())
    GithubHelper.parseName("mike") must be(Name(first = Some("mike")))
    GithubHelper.parseName("mike bryzek") must be(Name(first = Some("mike"), last = Some("bryzek")))
    GithubHelper.parseName("   mike    maciej    bryzek  ") must be(
      Name(first = Some("mike"), last = Some("maciej bryzek"))
    )
  }

  "parseUri" in {
    GithubUtil.parseUri("http://github.com/mbryzek/apidoc") must be(
      Right(
        GithubUtil.Repository("mbryzek", "apidoc")
      )
    )
  }

  "parseUri for invalid URLs" in {
    GithubUtil.parseUri("   ") must be(
      Left(s"URI cannot be an empty string")
    )

    GithubUtil.parseUri("http://github.com") must be(
      Left("URI path cannot be empty for uri[http://github.com]")
    )

    GithubUtil.parseUri("http://github.com/mbryzek") must be(
      Left("Invalid uri path[http://github.com/mbryzek] missing project name")
    )

    GithubUtil.parseUri("http://github.com/mbryzek/apidoc/other") must be(
      Left("Invalid uri path[http://github.com/mbryzek/apidoc/other] - expected exactly two path components")
    )
  }

}
