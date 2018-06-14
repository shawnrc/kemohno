package me.shawnrc.kemohno

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.util.*
import kotlin.math.min

class Emojifier(emojiPath: String) {
  private val emojiMap: JsonObject

  init {
    val parser = Klaxon()
    emojiMap = if (emojiPath.startsWith("http")) {
      LOG.info("fetching emoji map from external http source")
      val response = khttp.get(emojiPath)
      if (response.statusCode !in 200..299) {
        LOG.error("failed to fetch emoji map, aborting")
        LOG.error("fetch failed, status ${response.statusCode}")
        throw Exception("failed to get emoji")
      }
      parser.parseJsonObject(response.text.reader())
    } else {
      LOG.info("using emojifile at $emojiPath")
      parser.parseJsonObject(
          File(emojiPath).bufferedReader(Charset.defaultCharset()))
    }
  }

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

  private companion object {
    private val RANDOM = Random()
    private val LOG: Logger = LoggerFactory.getLogger(Emojifier::class.java)

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
