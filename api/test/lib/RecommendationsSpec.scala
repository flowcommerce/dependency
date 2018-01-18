package com.bryzek.dependency.api.lib

import com.bryzek.dependency.v0.models.VersionForm
import org.specs2.mutable._

class RecommendationsSpec extends Specification {

  def simpleRecs(value: String, others: Seq[String]): Option[String] = {
    Recommendations.version(
      VersionForm(value),
      others.map(VersionForm(_))
    )
  }

  "No recommendation if others is empty" in {
    simpleRecs("1.0.0", Nil) must be(None)
  }

  "No recommendation if others is self" in {
    simpleRecs("1.0.0", Seq("1.0.0")) must be(None)
  }

  "No recommendation if others are lower than self" in {
    simpleRecs("1.0.0", Seq("0.1.0", "0.1.1")) must be(None)
  }

  "No recommendation if greater versions are beta versions" in {
    simpleRecs("1.0.0", Seq("1.0.1-rc1")) must be(None)
  }

  "postgresql example" in {
    simpleRecs(
      "9.4-1201-jdbc41",
      Seq("9.4-1205-jdbc4", "9.4-1205-jdbc41", "9.4-1205-jdbc42")
    ) must beSome("9.4-1205-jdbc42")
  }

  "scalatest example" in {
    simpleRecs(
      "1.4.0-M3",
      Seq("1.4.0-M3", "1.4.0-M4", "1.4.0-SNAP1")
    ) must beSome("1.4.0-M4")
  }

  "flow play upgrade example" in {
    simpleRecs(
      "0.4.21",
      Seq("0.4.20", "0.4.21", "0.4.22", "0.4.20-play26", "0.4.21-play26", "0.4.22-play26")
    ) must beSome("0.4.22")
  }

  "webjars-play example" in {
    simpleRecs(
      "2.4.0",
      Seq("2.4.0", "2.4.0-1", "2.4.0-2")
    ) must beNone

    simpleRecs(
      "2.4.0-1",
      Seq("2.4.0", "2.4.0-1", "2.4.0-2")
    ) must beSome("2.4.0-2")
  }

  "slick example - respects major version when textual" in {
    simpleRecs(
      "2.1.0-M3",
      Seq("2.1.0-M3", "3.1.0-M4", "3.1")
    ) must beSome("3.1")
  }

  "matches on cross build version" in {
    Recommendations.version(
      VersionForm("1.0.1", Some("2.11.7")),
      Seq(
        VersionForm("1.0.1", Some("2.11.7")),
        VersionForm("1.3.2", Some("0.13")),
        VersionForm("1.3.3", Some("0.13"))
      )
    ) must be(None)
  }

  "matches on cross build version" in {
    Recommendations.version(
      VersionForm("1.0.1", Some("2.11.7")),
      Seq(
        VersionForm("1.0.1", Some("2.11.7")),
        VersionForm("1.3.2", Some("0.13")),
        VersionForm("1.3.3", Some("0.13")),
        VersionForm("1.1", Some("2.11.6")),
        VersionForm("1.1", Some("2.11.7"))
      )
    ) must beSome("1.1")
  }

  "matches on partial cross build version" in {
    Recommendations.version(
      VersionForm("1.2.1", Some("2.11.7")),
      Seq(
        VersionForm("1.2.1", Some("2.11.7")),
        VersionForm("1.2.1", Some("2.11")),
        VersionForm("1.2.2", Some("2.11")),
        VersionForm("1.2.1", Some("2.10")),
        VersionForm("1.2.2", Some("2.10"))
      )
    ) must beSome("1.2.2")
  }

  "matches on partial cross build version" in {
    Recommendations.version(
      VersionForm("1.2.1", Some("2.12")),
      Seq(
        VersionForm("1.2.1", Some("2.11.7")),
        VersionForm("1.2.1", Some("2.11")),
        VersionForm("1.2.2", Some("2.11")),
        VersionForm("1.2.1", Some("2.10")),
        VersionForm("1.2.2", Some("2.10"))
      )
    ) must beNone
  }
}
