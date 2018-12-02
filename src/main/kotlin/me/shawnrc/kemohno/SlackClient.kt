package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import khttp.responses.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Reader

class SlackClient(
    private val oauthToken: String,
    botToken: String,
    cacheSeed: String? = null) {
  private val userCache: MutableMap<String, User> = cacheSeed?.let(::buildCache) ?: mutableMapOf()
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
    responseHandler(response)

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

  fun sendToChannelAsUser(
      text: String,
      channel: String,
      user: User,
      fallbackUrl: String? = null,
      threadTimestamp: String? = null) {
    LOG.debug("hitting chat.postMessage")
    val body = mutableMapOf(
        "icon_url" to user.imageUrl,
        "username" to user.realName)
    if (threadTimestamp != null) body["thread_ts"] = threadTimestamp

    postMessage(text, channel, optionalParams = body) {
      if (fallbackUrl != null && statusCode == 200 && hasSlackError) {
        LOG.debug("failed to send to channel $channel")
        when (jsonObject.getString("error")) {
          "channel_not_found" -> {
            LOG.info("could not send to private channel, letting caller know")
            respondEphemeral(CANNOT_SEND_TO_PRIVATE_CHANNEL, fallbackUrl)
          }
          "restricted_action" -> {
            LOG.info("user attempted to send to restricted channel, sending direct message")
            postMessage(
                text = "I'm sorry, but bots cannot send to that channel. Regardless, here is your emojified message:",
                channel = user.id,
                optionalParams = mapOf("attachments" to arrayOf(mapOf(
                    "fallback" to "Kemohno: Something has gone wrong.",
                    "text" to text
                ))),
                onResponse = responseHandler)
          }
          else -> responseHandler(this)
        }
      } else responseHandler(this)
    }
  }

  private fun postMessage(
      text: String,
      channel: String,
      asUser: Boolean = false,
      responseType: String = "in_channel",
      optionalParams: Map<String, Any> = mapOf(),
      onResponse: Response.() -> Unit) {
    val body = mutableMapOf(
        "text" to text,
        "channel" to channel,
        "as_user" to asUser,
        "response_type" to responseType)
    body.putAll(optionalParams)
    khttp.async.post(
        url = "https://slack.com/api/chat.postMessage",
        headers = apiPostHeaders,
        json = body,
        onResponse = onResponse)
  }

  fun respondEphemeral(text: String, responseUrl: String) =
      respond(text, responseUrl, responseType = "ephemeral")

  fun respondInChannel(text: String, responseUrl: String) =
      respond(text, responseUrl, responseType = "in_channel")

  private fun respond(text: String, responseUrl: String, responseType: String) = khttp.async.post(
      url = responseUrl,
      onResponse = responseHandler,
      json = mapOf(
          "response_type" to responseType,
          "text" to text))

  fun isMpim(channel: String): Boolean {
    if (!channel.startsWith("G")) return false
    LOG.debug("checking to see if $channel is an mpim")
    val response = khttp.get(
        url = "https://slack.com/api/groups.info",
        params = mapOf("token" to oauthToken, "channel" to channel))
    responseHandler(response)
    return response.couldNotFindChannel ||
        response.jsonObject
            .getJSONObject("group")
            .getBoolean("is_mpim")
  }

  private companion object {
    const val CANNOT_SEND_TO_PRIVATE_CHANNEL = ":face_with_cowboy_hat: *Howdy!* I can’t send to this channel just " +
        "yet; invite me and try emojifying again! ╰(✿˙ᗜ˙)੭━☆ﾟ.*･｡ﾟ"

    val LOG: Logger = LoggerFactory.getLogger(SlackClient::class.java)

    val responseHandler: (Response) -> Unit = { response ->
      val responseType = response.headers["Content-Type"]
      LOG.debug("response received for request to ${response.endpoint}, " +
          "content-type: $responseType, status: ${response.statusCode}")
      if (responseType == APPLICATION_JSON) {
        val json = response.jsonObject
        if (response.hasSlackError) {
          LOG.error("error from slack API when hitting ${response.endpoint}")
          LOG.error(json.getString("error"))
        }
        if (json.has("warning")) LOG.warn("warning from api: ${json["warning"]}")
        LOG.debug("dumping json content:")
        LOG.debug(json.toString(2))
      } else {
        LOG.debug("got non-json response")
        when (val status = response.statusCode) {
          in 200..299 -> LOG.debug("success ($status) content: ${response.text} .")
          404 -> {
            LOG.error("${response.url} not found - maybe malformed or expired?")
            LOG.debug("dumping: ${response.text}")
          }
          in 400..499 -> LOG.error("client error $status: ${response.text}")
          in 500..599 -> {
            LOG.warn("slack server error (${response.statusCode}) ")
            LOG.debug("dumping: ${response.text}")
          }
        }
      }
    }

    val Response.endpoint: String
      get() = File(request.url).name.split('?')[0]

    val Response.hasSlackError
      get() = jsonObject.has("ok") && !jsonObject.getBoolean("ok")

    val Response.couldNotFindChannel
      get() = hasSlackError && jsonObject.getString("error") == "channel_not_found"

    fun buildCache(seed: String): MutableMap<String, User> {
      val reader = if (seed.isHttp) getRemoteCacheReader(seed) else {
        LOG.info("using provided userCache seed at $seed")
        File(seed).reader()
      }
      val parsed = Klaxon().parseJsonObject(reader)
      return parsed.keys.associateTo(mutableMapOf()) { userId ->
        val blob = parsed.obj(userId) ?: throw Exception("failed parsing users, key $userId not mapped to an object")
        userId to User(
            userId,
            blob.getString("realName"),
            blob.getString("imageUrl"))
      }
    }

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
