package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import khttp.responses.Response
import org.json.JSONException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Reader

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

    LOG.debug("hitting users.profile.get")
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
    LOG.info("caching user $userId")
    userCache[userId] = user
  }

  fun sendMessage(
      text: String,
      channel: String,
      user: User,
      targetUrl: String = "https://slack.com/api/chat.postMessage") {
    LOG.debug("hitting chat.postMessage")
    khttp.async.post(
        url = targetUrl,
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
      val reader = if (it.isHttp) getRemoteCacheReader(it) else {
        LOG.info("using provided userCache seed at $it")
        File(cacheSeed).reader()
      }
      val seed = Klaxon().parseJsonObject(reader)
      seed.keys.map { key ->
        val blob = seed.obj(key) ?: throw Exception("failed parsing emoji, key $key not mapped to an object")
        key to User(
            blob.getString("realName"),
            blob.getString("imageUrl"))
      }.toMap().toMutableMap()
    } ?: mutableMapOf()

    fun getRemoteCacheReader(cacheUrl: String): Reader {
      LOG.info("fetching userCache from external http source")
      val response = khttp.get(cacheUrl)
      if (response.statusCode != 200) {
        LOG.error("fetch failed, status ${response.statusCode}")
        throw Exception("failed to get userCache")
      }
      return response.text.reader()
    }
  }
}

data class User(val realName: String, val imageUrl: String)
