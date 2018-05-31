package me.shawnrc.kemohno

import org.json.JSONException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class SlackClient(private val oauthToken: String) {
  private val userCache = mutableMapOf(
      "U053MCJHX" to User(
          "Don Julio Eiol",
          "https://avatars.slack-edge.com/2018-04-19/350748747254_dc26bc070ffa7bb86d29_192.jpg")
  )

  fun getUserData(userId: String): User {
    if (userCache.containsKey(userId)) return userCache.getValue(userId)

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
      if (response.statusCode !in 200..299) {
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
