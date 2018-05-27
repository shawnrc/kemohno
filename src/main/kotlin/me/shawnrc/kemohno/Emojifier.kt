package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import java.io.File
import java.nio.charset.Charset
import java.util.*

class Emojifier(emojiFile: File) {
  private val random = Random()
  private val emojiMap = Klaxon().parseJsonObject(
      emojiFile.bufferedReader(Charset.defaultCharset()))

  fun translate(string: String): String = buildString {
    var index = 0
    while (index < string.length) {
      val skipIndex = index + 3
      if (skipIndex < string.length
          && string.substring(index..skipIndex) == ":b:") {
        append(":b:")
        index = skipIndex
        continue
      }
      val currentLetter = string[index].toString().toUpperCase()
      val letterPool = emojiMap.array<String>(currentLetter)
          ?: throw Exception("no letters for letter $currentLetter")
      append(letterPool[random.nextInt(letterPool.size)])

      ++index
    }
  }
}
