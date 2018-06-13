package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import java.io.File
import java.nio.charset.Charset
import java.util.*
import kotlin.math.min

class Emojifier(emojiPath: String) {
  private val emojiMap = Klaxon().parseJsonObject(
      File(emojiPath).bufferedReader(Charset.defaultCharset()))

  fun translate(string: String): String = buildString {
    val normalized = string.toUpperCase()
    var index = 0
    var retries = 0
    val usedEmoji = mutableSetOf<String>()
    while (index < normalized.length) {
      val skipIndex = index + 3
      if (normalized.bemojiAt(index, skipIndex)) {
        append(":b:")
        index = skipIndex
        continue
      }
      val currentLetter = normalized[index].toString()
      val letterPool = emojiMap.array<String>(currentLetter)
      val selected = letterPool?.randomItem() ?: currentLetter
      if (selected.isEmoji) {
        if (selected in usedEmoji && retries < 2) {
          ++retries
          continue
        }
        usedEmoji.add(selected)
        retries = 0
      }
      append(selected)

      ++index
    }
  }

  companion object {
    private val RANDOM = Random()

    fun stripRawMentions(text: String): String =
      text.replace("""<@\S{9}>""".toRegex(), "")
          .replace("""<([@#])\S{9}\|(\S+)>""".toRegex(), "$1$2")

    private val String.isEmoji: Boolean
      get() = startsWith(':') && endsWith(':')

    private fun String.bemojiAt(start: Int, end: Int): Boolean {
      return substring(start until min(end, length)) == ":B:"
    }

    private fun <E> List<E>.randomItem(): E {
      if (isEmpty()) throw IndexOutOfBoundsException("randomItem called on empty list")
      return this[RANDOM.nextInt(size)]
    }
  }
}
