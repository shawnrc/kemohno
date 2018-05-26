package me.shawnrc.kemohno

object SlackClient {
  fun getUser(userId: String, oauthToken: String): User {
    val userBlob = khttp.get(
        url = "https://slack.com/api/users.info",
        params = mapOf("token" to oauthToken, "user" to userId)).jsonObject
    val realName = userBlob.getString("real_name")
    val imageUrl = userBlob.getJSONObject("profile")
        .getString("image_512")
    return User(realName, imageUrl)
  }
}

data class User(val realName: String, val imageUrl: String)
