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
        id = userId,
        realName = profile.getString("real_name"),
        imageUrl = profile.getString("image_512"))
    cacheUser(user)
    return user
  }

  fun cacheUser(user: User) {
    if (user.id in fixtures) return
    LOG.info("caching user ${user.id}")
    userCache[user.id] = user
  }

  private fun sendMessage(
      text: String,
      channel: String,
      options: Map<String, Any> = mapOf(),
      onResponse: Response.() -> Unit = {}) {
    LOG.debug("hitting chat.postMessage")
    khttp.async.post(
        url = "https://slack.com/api/chat.postMessage",
        headers = apiPostHeaders,
        json = options + mapOf(
            "text" to text,
            "channel" to channel),
        onResponse = onResponse)
  }

  fun sendToChannelAsUser(
      text: String,
      channel: String,
      user: User,
      fallbackUrl: String? = null) = sendMessage(
      text,
      channel,
      options = mapOf(
          "as_user" to false,
          "icon_url" to user.imageUrl,
          "username" to user.realName,
          "response_type" to "in_channel"),
      onResponse = {
        if (fallbackUrl != null
            && statusCode == 200
            && slackSentError
            && jsonObject.getString("error") == "channel_not_found") {
          LOG.info("could not send to private channel, letting caller know")
          LOG.debug("failed to send to channel $channel")
          respondEphemeral(CANNOT_SEND_TO_PRIVATE_CHANNEL, fallbackUrl)
        } else {
          errorHandler(this)
        }
      }
  )

  fun respondEphemeral(text: String, responseUrl: String) = khttp.async.post(
      url = responseUrl,
      onResponse = errorHandler,
      json = mapOf(
          "response_type" to "ephemeral",
          "text" to text))

  fun respondInChannel(text: String, responseUrl: String) = khttp.async.post(
      url = responseUrl,
      onResponse = errorHandler,
      json = mapOf(
          "response_type" to "in_channel",
          "text" to text))

  fun isMpim(channel: String): Boolean {
    if (!channel.startsWith("G")) return false
    LOG.debug("checking to see if $channel is an mpim")
    val response = khttp.get(
        url = "https://slack.com/api/groups.info",
        params = mapOf("token" to oauthToken, "channel" to channel))
    errorHandler(response)
    return response.jsonObject.getJSONObject("group").getBoolean("is_mpim")
  }

  private companion object {
    const val CANNOT_SEND_TO_PRIVATE_CHANNEL = ":face_with_cowboy_hat: *Howdy!* I can’t send to this channel just " +
        "yet; invite me and try emojifying again! ╰(✿˙ᗜ˙)੭━☆ﾟ.*･｡ﾟ"

    val LOG: Logger = LoggerFactory.getLogger(SlackClient::class.java)

    val errorHandler = { response: Response ->
      if (response.statusCode !in 200..299 || response.slackSentError) {
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

    val Response.slackSentError
      get() = !jsonObject.getBoolean("ok")

    fun buildCache(cacheSeed: String?) = cacheSeed?.let {
      val reader = if (it.isHttp) getRemoteCacheReader(it) else {
        LOG.info("using provided userCache seed at $it")
        File(cacheSeed).reader()
      }
      val seed = Klaxon().parseJsonObject(reader)
      seed.keys.map { userId ->
        val blob = seed.obj(userId) ?: throw Exception("failed parsing users, key $userId not mapped to an object")
        userId to User(
            userId,
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

data class User(
    val id: String,
    val realName: String,
    val imageUrl: String)
