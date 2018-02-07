package io.flow.dependency.lib

// From https://github.com/mbryzek/apidoc/blob/6186612993a0c913cfd0b7a36417bda45281825e/lib/src/main/scala/Text.scala
object Text {

  private val Ellipsis = "..."

  /**
   * if value is longer than maxLength characters, it wil be truncated
   * to <= (maxLength-Ellipsis.length) characters and an ellipsis
   * added. We try to truncate on a space to avoid breaking a word in
   * pieces.
   */
  def truncate(value: String, maxLength: Int = 100): String = {
    require(maxLength >= 10, "maxLength must be >= 10")

    if (value.length <= maxLength) {
      value
    } else {
      val pieces = value.split(" ")
      var i = pieces.length
      while (i > 0) {
        val sentence = pieces.slice(0, i).mkString(" ")
        if (sentence.length <= (maxLength-Ellipsis.length)) {
          return sentence + Ellipsis
        }
        i -= 1
      }

      val letters = value.split("")
      letters.slice(0, letters.length-4).mkString("") + Ellipsis
    }
  }

}
