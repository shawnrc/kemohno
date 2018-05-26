package me.shawnrc.kemohno

import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

object SlackClient {
  private val LOG: Logger = LoggerFactory.getLogger(SlackClient::class.java)

  fun getUser(userId: String, oauthToken: String): User {
    val responseBlob = request(
        verb = "get",
        url = "https://slack.com/api/users.info",
        params = mapOf("token" to oauthToken, "user" to userId))

    val userBlob = responseBlob.getJSONObject("user")
    val realName = userBlob.getString("real_name")
    val imageUrl = userBlob.getJSONObject("profile")
        .getString("image_512")
    return User(realName, imageUrl)
  }

  private fun request(
      verb: String,
      url: String,
      params: Map<String, String>): JSONObject {
    val response = when (verb) {
      "get" -> khttp.get(url, params)
      "post" -> khttp.post(url, params)
      else -> throw Exception("unsupported verb $verb")
    }
    val responseBlob = response.jsonObject
    if (response.statusCode != 200 || !responseBlob.getBoolean("ok")) {
      val endpoint = File(url).name
      LOG.error("call to $endpoint endpoint failed, dumping")
      LOG.error(responseBlob["error"].toString())
      throw Exception("bad call to $endpoint")
    }
    return responseBlob
  }
}

data class User(val realName: String, val imageUrl: String)
