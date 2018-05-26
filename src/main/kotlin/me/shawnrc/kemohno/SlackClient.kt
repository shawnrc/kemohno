package me.shawnrc.kemohno

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object SlackClient {
  private val LOG: Logger = LoggerFactory.getLogger(SlackClient::class.java)

  fun getUser(userId: String, oauthToken: String): User {
    val responseBlob = khttp.get(
        url = "https://slack.com/api/users.info",
        params = mapOf("token" to oauthToken, "user" to userId)).jsonObject
    if (!responseBlob.getBoolean("ok")) {
      LOG.error("call to userinfo endpoint failed, dumping")
      LOG.error(responseBlob["error"].toString())
      throw Exception("bad call to userinfo")
    }

    val userBlob = responseBlob.getJSONObject("user")
    val realName = userBlob.getString("real_name")
    val imageUrl = userBlob.getJSONObject("profile")
        .getString("image_512")
    return User(realName, imageUrl)
  }
}

data class User(val realName: String, val imageUrl: String)
