package db

import io.flow.dependency.v0.models.{Credentials, CredentialsUndefinedType, UsernamePassword}

import org.specs2.mutable._

class UtilSpec extends Specification {

  "maskCredentials" in {
    Util.maskCredentials(UsernamePassword("foo")) must beEqualTo(Some(UsernamePassword("foo")))
    Util.maskCredentials(UsernamePassword("foo", Some("bar"))) must beEqualTo(Some(UsernamePassword("foo", Some("masked"))))
    Util.maskCredentials(CredentialsUndefinedType("foo")) must be(None)
  }

}
