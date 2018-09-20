package me.shawnrc.kemohno

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Reader
import java.util.ArrayDeque
import java.util.Queue
import kotlin.math.min

class Emojifier(emojiPath: String) {
  private val dispenser: EmojiDispenser

  init {
    val reader = if (emojiPath.isHttp) getRemoteEmojiReader(emojiPath) else {
      LOG.info("using emojifile at $emojiPath")
      File(emojiPath).reader()
    }
    dispenser = EmojiDispenser(emojiBlob = Klaxon().parseJsonObject(reader))
  }

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
      append(dispenser[normalized[index]])
      ++index
    }
  }

  private class EmojiDispenser(private val emojiBlob: JsonObject) {
    private val emojiMap: Map<Char, Queue<String>> = emojiBlob.keys.associate {
      it[0] to ArrayDeque(emojiBlob.array<String>(it)?.shuffled())
    }

    operator fun get(char: Char): String {
      val key = char.toString()
      val emojiQueue = emojiMap[char] ?: return key
      if (emojiQueue.isEmpty()) {
        val shuffled = emojiBlob.array<String>(key)?.shuffled() ?: return key
        emojiQueue.addAll(shuffled)
      }
      return emojiQueue.remove()
    }
  }

  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(Emojifier::class.java)

    fun getRemoteEmojiReader(emojiPath: String): Reader {
      LOG.info("fetching emoji map from external http source")
      val response = khttp.get(emojiPath)
      if (response.statusCode != 200) {
        LOG.error("fetch failed, status ${response.statusCode}")
        throw Exception("failed to get emoji")
      }
      return response.text.reader()
    }

    fun String.bemojiAt(start: Int, end: Int): Boolean =
        substring(start until min(end, length)) == ":B:"
  }
}
