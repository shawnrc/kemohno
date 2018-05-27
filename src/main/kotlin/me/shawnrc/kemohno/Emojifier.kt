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
    val normalized = string.toUpperCase()
    var index = 0
    while (index < normalized.length) {
      val skipIndex = index + 2
      if (normalized[index] == ':'
          && skipIndex < normalized.length
          && normalized.substring(index..skipIndex) == ":b:") {
        append(":b:")
        index = skipIndex
        continue
      }
      val currentLetter = normalized[index].toString()
      val letterPool = emojiMap.array<String>(currentLetter) ?: continue
      append(letterPool.random())

      ++index
    }
  }

  private fun <E> List<E>.random(): E {
    if (this.isEmpty()) throw IndexOutOfBoundsException("random called on empty list")
    return this[random.nextInt(size)]
  }
}
