package me.shawnrc.kemohno

import com.beust.klaxon.JsonObject
import java.util.ArrayDeque
import java.util.Queue

class EmojiDispenser(private val emojiBlob: JsonObject) {
  private val emojiMap: Map<Char, Queue<String>> = emojiBlob.keys.map {
    it[0] to ArrayDeque(emojiBlob.array<String>(it))
  }.toMap()

  operator fun get(char: Char): String {
    val key = char.toString()
    val emojiQueue = emojiMap[char] ?: return key
    if (emojiQueue.isEmpty()) {
      val shuffled = emojiBlob.array<String>(key)
          ?.shuffled()
          ?.toMutableList() ?: return key
      emojiQueue.addAll(shuffled)
    }
    return emojiQueue.remove()
  }
}
