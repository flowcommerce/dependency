package db

import io.flow.dependency.v0.models.{CredentialsUndefinedType, UsernamePassword}
import util.DependencySpec

class UtilSpec extends DependencySpec {

  "maskCredentials" in {
    Util.maskCredentials(UsernamePassword("foo")) must be(Some(UsernamePassword("foo")))
    Util.maskCredentials(UsernamePassword("foo", Some("bar"))) must be(Some(UsernamePassword("foo", Some("masked"))))
    Util.maskCredentials(CredentialsUndefinedType("foo")) must be(None)
  }

}
