package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import khttp.responses.Response
import org.json.JSONException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class SlackClient(
    private val oauthToken: String,
    botToken: String,
    cacheSeed: String? = null) {
  private val userCache: MutableMap<String, User> = buildCache(cacheSeed)
  private val fixtures: Set<String> = userCache.keys.toSet()
  private val apiPostHeaders = mapOf(
      "Content-Type" to APPLICATION_JSON,
      "Authorization" to "Bearer $botToken")

  fun getUserData(userId: String): User {
    if (userId in userCache) return userCache.getValue(userId)

    LOG.debug("hitting getUserData")
    val response = khttp.get(
        url = "https://slack.com/api/users.profile.get",
        params = mapOf("token" to oauthToken, "user" to userId))
    errorHandler(response)
    val responseBlob = response.jsonObject

    val profile = responseBlob.getJSONObject("profile")
    val user = User(
        realName = profile.getString("real_name"),
        imageUrl = profile.getString("image_512"))
    cacheUser(userId, user)
    return user
  }

  fun cacheUser(userId: String, user: User) {
    if (userId in fixtures) return
    userCache[userId] = user
  }

  fun sendMessage(
      text: String,
      channel: String,
      user: User) {
    LOG.debug("hitting chat.postMessage")
    khttp.async.post(
        url = "https://slack.com/api/chat.postMessage",
        headers = apiPostHeaders,
        json = mapOf(
            "text" to text,
            "as_user" to false,
            "channel" to channel,
            "icon_url" to user.imageUrl,
            "username" to user.realName,
            "response_type" to "in_channel"),
        onResponse = errorHandler
    )
  }

  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(SlackClient::class.java)

    val errorHandler = { response: Response ->
      if (response.statusCode !in 200..299 || !response.jsonObject.getBoolean("ok")) {
        val endpoint = response.endpoint
        LOG.error("call to $endpoint endpoint failed")
        try {
          val json = response.jsonObject
          LOG.error("ok=${json.getBoolean("ok")} error=${json.getString("error")}")
        } catch (_: JSONException) {
          when (response.statusCode) {
            in 400..499 -> LOG.error("client error: ${response.statusCode}")
            in 500..599 -> LOG.error("slack servers are malfunctioning, aborting")
          }
        }
        throw Exception("bad call to $endpoint")
      }
      if (response.jsonObject.has("warning")) {
        LOG.warn("warning from api: ${response.jsonObject["warning"]}")
      }
    }

    val Response.endpoint
      get() = File(url).name.split('?')[0]

    fun buildCache(cacheSeed: String?) = cacheSeed?.let {
      LOG.info("using provided userCache seed at $it")
      val seed = Klaxon().parseJsonObject(File(cacheSeed).reader())
      seed.keys.map { key ->
        val blob = seed.obj(key) ?: throw Exception("failed parsing emoji, key $key not mapped to an object")
        key to User(
            blob.getString("realName"),
            blob.getString("imageUrl"))
      }.toMap().toMutableMap()
    } ?: mutableMapOf()
  }
}

data class User(val realName: String, val imageUrl: String)
