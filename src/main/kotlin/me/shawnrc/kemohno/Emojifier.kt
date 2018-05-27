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
      val skipIndex = index + 3
      if (normalized.bemojiAt(index, skipIndex)) {
        append(":b:")
        index = skipIndex
        continue
      }
      val currentLetter = normalized[index].toString()
      val letterPool = emojiMap.array<String>(currentLetter)
      append(letterPool?.randomItem() ?: currentLetter)

      ++index
    }
  }

  private fun String.bemojiAt(start: Int, end: Int): Boolean {
    return this[start] == ':'
        && end < length
        && substring(start until end) == ":B:"
  }

  private fun <E> List<E>.randomItem(): E {
    if (isEmpty()) throw IndexOutOfBoundsException("randomItem called on empty list")
    return this[random.nextInt(size)]
  }
}
