package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import org.json.JSONException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class SlackClient(private val oauthToken: String) {
  private val userCache = mutableMapOf<String, User>()

  constructor(oauthToken: String, cacheSeed: String) : this(oauthToken) {
    LOG.info("using provided userCache seed at $cacheSeed")
    val seed = Klaxon().parseJsonObject(File(cacheSeed).reader())
    for (key in seed.keys) {
      val blob = seed.obj(key)
      blob?.let {
        userCache[key] = User(
            it.getString("realName"),
            it.getString("imageUrl"))
      }
    }
  }

  fun getUserData(userId: String): User {
    if (userId in userCache) return userCache.getValue(userId)

    LOG.debug("hitting getUserData")
    val response = khttp.get(
        url = "https://slack.com/api/users.profile.get",
        params = mapOf("token" to oauthToken, "user" to userId))
    errorHandler(response)
    val responseBlob = response.jsonObject

    val profile = responseBlob.getJSONObject("profile")
    val realName = profile.getString("real_name")
    val imageUrl = profile.getString("image_512")
    return User(realName, imageUrl)
  }

  fun sendMessage(
      text: String,
      channel: String,
      user: User) {
    LOG.debug("hitting chat.postMessage")
    khttp.async.post(
        url = "https://slack.com/api/chat.postMessage",
        params = mapOf(
            "text" to text,
            "as_user" to "false",
            "channel" to channel,
            "icon_url" to user.imageUrl,
            "username" to user.realName,
            "response_type" to "in_channel",
            "token" to oauthToken
        ),
        onResponse = errorHandler
    )
  }

  private companion object {
    private val LOG: Logger = LoggerFactory.getLogger(SlackClient::class.java)

    private val errorHandler = { response: khttp.responses.Response ->
      if (response.statusCode !in 200..299 || response.jsonObject["ok"] != "true") {
        val endpoint = File(response.url).name
        LOG.error("call to $endpoint endpoint failed")
        try {
          val json = response.jsonObject
          LOG.error("ok=${json.getBoolean("ok")} error=${json.getString("error")}")
        } catch (_: JSONException) {
          LOG.error(response.text)
        }
        throw Exception("bad call to $endpoint")
      }
    }
  }
}

data class User(val realName: String, val imageUrl: String)
