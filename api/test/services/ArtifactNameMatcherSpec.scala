package services

import io.flow.test.utils.FlowPlaySpec

class ArtifactNameMatcherSpec extends FlowPlaySpec {
  import ArtifactNameMatcher.matches

  "exact match" in {
    matches("foo", "foo") must be(true)
    matches("foo-bar", "foo-bar") must be(true)
    matches("foo", "foo1") must be(false)
  }

  "known suffixes" in {
    matches("foo-play", "foo") must be(true)
    matches("foo-play28", "foo") must be(true)
    matches("foo-play28x", "foo") must be(false)
    matches("foo-other", "foo") must be(false)
  }

}
