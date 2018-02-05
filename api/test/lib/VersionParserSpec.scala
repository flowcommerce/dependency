package io.flow.dependency.api.lib

import util.DependencySpec

class VersionParserSpec extends DependencySpec {

  "simple semver version numbers" in {
    VersionParser.parse("1") must be(Version("1", Seq(Tag.Semver(1, 0, 0))))
    VersionParser.parse("1.0") must be(Version("1.0", Seq(Tag.Semver(1, 0, 0))))
    VersionParser.parse("1.0.0") must be(Version("1.0.0", Seq(Tag.Semver(1, 0, 0))))
    VersionParser.parse("1.2.3") must be(Version("1.2.3", Seq(Tag.Semver(1, 2, 3))))
    VersionParser.parse("r1.2.3") must be(Version("r1.2.3", Seq(Tag.Semver(1, 2, 3))))
    VersionParser.parse("V1.2.3") must be(Version("V1.2.3", Seq(Tag.Semver(1, 2, 3))))
    VersionParser.parse("experimental1.2.3") must be(Version("experimental1.2.3", Seq(Tag.Text("experimental"), Tag.Semver(1, 2, 3))))
    VersionParser.parse("1.2.3.4") must be(Version("1.2.3.4", Seq(Tag.Semver(1, 2, 3, Seq(4)))))
    VersionParser.parse("dev") must be(Version("dev", Seq(Tag.Text("dev"))))
    VersionParser.parse("1.0.0-dev") must be(Version("1.0.0-dev", Seq(Tag.Semver(1, 0, 0), Tag.Text("dev"))))
    VersionParser.parse("1.0.0_dev") must be(Version("1.0.0_dev", Seq(Tag.Semver(1, 0, 0), Tag.Text("dev"))))
    VersionParser.parse("1.0.0.dev") must be(Version("1.0.0.dev", Seq(Tag.Semver(1, 0, 0), Tag.Text("dev"))))
  }

  "isDate" in {
    VersionParser.isDate(123) must be(false)
    VersionParser.isDate(20141018) must be(true)
    VersionParser.isDate(10141018) must be(false)
    VersionParser.isDate(19141018) must be(true)
  }

  "date version numbers" in {
    VersionParser.parse("123") must be(Version("123", Seq(Tag.Semver(123, 0, 0))))
    VersionParser.parse("20141018") must be(Version("20141018", Seq(Tag.Date(20141018, 0))))
    VersionParser.parse("20141018.1") must be(Version("20141018.1", Seq(Tag.Date(20141018, 1))))
    VersionParser.parse("r20141018.1") must be(Version("r20141018.1", Seq(Tag.Date(20141018, 1))))
    VersionParser.parse("10141018") must be(Version("10141018", Seq(Tag.Semver(10141018, 0, 0))))
  }

  "postgresql version" in {
    VersionParser.parse("9.4-1201-jdbc41") must be(
      Version(
        "9.4-1201-jdbc41",
        Seq(
          Tag.Semver(9, 4, 0),
          Tag.Semver(1201, 0, 0),
          Tag.Text("jdbc"),
          Tag.Semver(41, 0, 0)
        )
      )
    )
    VersionParser.parse("42.1.3") > VersionParser.parse("9.4.1212") must be(true)
  }

  "separated text from numbers" in {
    VersionParser.parse("1.4.0-M4") must be(
      Version(
        "1.4.0-M4",
        Seq(
          Tag.Semver(1, 4, 0),
          Tag.Text("M"),
          Tag.Semver(4, 0, 0)
        )
      )
    )
  }

  "scala lang versions" in {
    VersionParser.parse("2.9.1.final") must be(
      Version(
        "2.9.1.final",
        Seq(
          Tag.Semver(2, 9, 1),
          Tag.Text("final")
        )
      )
    )
  }

  "sortKey" in {
    VersionParser.parse("TEST").sortKey must be("20.test.99999")
    VersionParser.parse("r20141211.1").sortKey must be("40.20141211.10001.99999")
    VersionParser.parse("1.2.3").sortKey must be("60.10001.10002.10003.99999")
    VersionParser.parse("r1.2.3").sortKey must be("60.10001.10002.10003.99999")
    VersionParser.parse("1.2.3.4").sortKey must be("60.10001.10002.10003.10004.99999")

    VersionParser.parse("1.0.0").sortKey must be("60.10001.10000.10000.99999")
    VersionParser.parse("1.0.0-g-1").sortKey must be("60.10001.10000.10000.99998,20.g.99998,60.10001.10000.10000.99998")
  }

  "sorts 1 element version" in {
    assertSorted(Seq("0", "1", "5"), "0 1 5")
    assertSorted(Seq("5", "0", "1"), "0 1 5")
    assertSorted(Seq("2", "1", "0"), "0 1 2")
  }

  "sorts 2 element version" in {
    assertSorted(Seq("0.0", "0.1", "2.1"), "0.0 0.1 2.1")
    assertSorted(Seq("0.0", "0.1", "2.1"), "0.0 0.1 2.1")
    assertSorted(Seq("1.0", "0.0", "1.1", "1.2", "0.10"), "0.0 0.10 1.0 1.1 1.2")
  }

  "sorts 3 element version" in {
    assertSorted(Seq("0.0.0", "0.0.1", "0.1.0", "5.1.0"), "0.0.0 0.0.1 0.1.0 5.1.0")
    assertSorted(Seq("10.10.10", "10.0.1", "1.1.50", "15.2.2", "1.0.10"), "1.0.10 1.1.50 10.0.1 10.10.10 15.2.2")
  }

  "sorts string tags as strings" in {
    assertSorted(Seq("r20140201.1", "r20140201.2"), "r20140201.1 r20140201.2")
  }

  "sorts strings mixed with semver tags" in {
    assertSorted(Seq("0.8.6", "0.8.8", "development"), "development 0.8.6 0.8.8")
  }

  "sorts versions w/ varying lengths" in {
    assertSorted(Seq("1", "0.1"), "0.1 1")
    assertSorted(Seq("1", "0.1", "0.0.1"), "0.0.1 0.1 1")
    assertSorted(Seq("1.2", "1.2.1"), "1.2 1.2.1")
    assertSorted(Seq("1.2", "1.2.1", "2"), "1.2 1.2.1 2")
  }

  "Sorts semvers with more than 3 components" in {
    assertSorted(Seq("1.0.9.5", "1.0.9.8", "1.0.10.1", "1.0.10.2"), "1.0.9.5 1.0.9.8 1.0.10.1 1.0.10.2")
  }

  "numeric tags are considered newer than string tags" in {
    assertSorted(Seq("1.0.0", "r20140201.1"), "r20140201.1 1.0.0")
  }

  "scalatestplus version numbers" in {
    assertSorted(Seq("1.4.0-M4", "1.4.0-M3"), "1.4.0-M3 1.4.0-M4")
    assertSorted(Seq("1.4.0-M4", "1.4.0-M10"), "1.4.0-M4 1.4.0-M10")
  }

  "webjars-play version numbers" in {
    assertSorted(Seq("2.4.0", "2.4.0-1", "2.4.0-2"), "2.4.0 2.4.0-1 2.4.0-2")
  }

  "parses major from semver versions" in {
    VersionParser.parse("0.0.0").major must be(Some(0))
    VersionParser.parse("0.0.0").major must be(Some(0))
    VersionParser.parse("0.0.0-dev").major must be(Some(0))

    VersionParser.parse("1.0.0").major must be(Some(1))
    VersionParser.parse("1.0.0-dev").major must be(Some(1))
  }

  "parses major from github versions" in {
    VersionParser.parse("v1").major must be(Some(1))
    VersionParser.parse("v1.0.0").major must be(Some(1))
    VersionParser.parse("v1.0.0-dev").major must be(Some(1))
  }

  "returns none when no major number" in {
    VersionParser.parse("v").major must be(None)
    VersionParser.parse("dev").major must be(None)
  }

  "major ignores whitespace" in {
    VersionParser.parse(" 1.0").major must be(Some(1))
    VersionParser.parse(" v2.0").major must be(Some(2))
  }

  "nextMicro" in {
    VersionParser.parse("foo").nextMicro must be(None)
    VersionParser.parse("foo-bar").nextMicro must be(None)
    VersionParser.parse("foo-0.1.2").nextMicro must be(Some(Version("foo-0.1.3", Seq(Tag.Text("foo"), Tag.Semver(0, 1, 3)))))
    VersionParser.parse("0.0.1").nextMicro.map(_.value) must be(Some("0.0.2"))
    VersionParser.parse("1.2.3").nextMicro.map(_.value) must be(Some("1.2.4"))
    VersionParser.parse("0.0.5-dev").nextMicro.map(_.value) must be(Some("0.0.6-dev"))
  }

  "can parse long ints" in {
    VersionParser.parse("20131213005945") must be(Version("20131213005945", Seq(Tag.Date(20131213005945l, 0))))
  }

  "sorts developer tags before release tags (latest release tag must be last)" in {
    // test if there is no text, that we sort so that
    // the -1, -2 are considered later version numbers
    assertSorted(Seq("1.0.1", "1.0.1-1", "1.0.1-2", "1.0.1-10"), "1.0.1 1.0.1-1 1.0.1-2 1.0.1-10")

    // test if there IS text, we consider those to be pre release versions.
    // Common example here would be 1.0.0-RC1, 1.0.0-RC2, 1.0.0-RC3 then finally 1.0.0
    assertSorted(Seq("1.0.0", "1.0.0-g-1"), "1.0.0-g-1 1.0.0")
    assertSorted(Seq("0.6.0-3-g3b52fba", "0.7.6"), "0.6.0-3-g3b52fba 0.7.6")

    assertSorted(Seq("0.28.1", "0.28.1-dev"), "0.28.1-dev 0.28.1")
    assertSorted(Seq("0.28.1-dev", "0.28.1"), "0.28.1-dev 0.28.1")

    assertSorted(Seq("1.0.1", "1.0.1-dev", "1.0.2", "1.0.0"), "1.0.0 1.0.1-dev 1.0.1 1.0.2")
  }  

  def assertSorted(versions: Seq[String], target: String) {
    versions.map( VersionParser.parse(_) ).sorted.map(_.value).mkString(" ") must be(target)
  }
}
