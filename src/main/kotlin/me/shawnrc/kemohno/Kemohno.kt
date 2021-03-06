package me.shawnrc.kemohno

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.Request
import spark.kotlin.halt
import spark.kotlin.ignite
import java.io.File
import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.text.Charsets.UTF_8

//region private static final
private const val CONFIG_PATH = "./config.json"
private const val EMOJI_PATH = "./emoji.json"
private const val EMPTY_MESSAGE_ERR = "baka! I can't emojify an empty string! try again with some characters."
private const val HASH_ALGORITHM = "HmacSHA256"
private const val SIGNATURE_VERSION = "v0"
private const val MAX_MESSAGE_LENGTH = 500000
private const val FIVE_MINUTES = 60 * 5

private val LOG: Logger = LoggerFactory.getLogger("Kemohno")
private val JSON = Klaxon()
//endregion

fun main(args: Array<String>) {
  val config = getConfig()
  val emojifier = Emojifier(args.firstOrNull() ?: config.emojiPath ?: EMOJI_PATH)
  val slackClient = SlackClient(
      config.oauthToken,
      config.botToken,
      cacheSeed = args.getOrNull(index = 1) ?: config.userSeedPath,
      channelBlacklistUri = args.getOrNull(index = 2) ?: config.channelBlacklistPath,
      userBlacklistUri = args.getOrNull(index = 3) ?: config.userBlacklistPath)
  val hasher = getHasher(key = config.signingSecret)

  ignite().apply {
    port(config.port)

    before {
      if (!(request.isHealthcheck || request.pathInfo() == "/hello")) {
        val timestamp: String? = request.headers("X-Slack-Request-Timestamp")
        val signature: String? = request.headers("X-Slack-Signature")
        val body = request.body()
        val computedSignature = timestamp?.let { hasher.buildSignature(it, body) }
        LOG.debug("BEFORE body: $body")
        if (timestamp == null
            || signature == null
            || timestamp.isNotRecentTimestamp
            || signature != computedSignature) {
          LOG.warn("failed to verify request was from slack")
          LOG.warn(("timestamp: $timestamp (not recent: ${timestamp?.isNotRecentTimestamp ?: "unknown"}) | " +
              "signature: $signature | " +
              "computed: ${computedSignature ?: "failed to compute signature"}"))
          halt(401)
        }
      }
    }

    after {
      val log: (String) -> Unit = if (request.isHealthcheck) LOG::debug else LOG::info
      log("${response.status()} ${request.requestMethod()} ${request.pathInfo()} ip=${request.ip()}")
    }

    get("/hello") {
      "hello"
    }

    get("/healthcheck") {
      "OK"
    }

    post("/bepis") {
      val requestParams = request.parseBodyParams()

      val maybeText = requestParams["text"]
      if (maybeText.isNullOrBlank()) {
        LOG.info("bad request, empty or nonexistent text field")
        response.type(APPLICATION_JSON)
        return@post buildResponse(EMPTY_MESSAGE_ERR)
      }

      val translated = emojifier.translate(maybeText)
      if (translated.length > MAX_MESSAGE_LENGTH) {
        LOG.error("user sent a string way too large")
        response.type(APPLICATION_JSON)
        return@post buildResponse(
            message = "that string was too large after emojification, try a smaller one.")
      }

      val channel = requestParams.getValue("channel_id")
      LOG.debug("preparing to send to channel $channel")
      if (channel.isDirectMessage || slackClient.isMpim(channel)) {
        LOG.debug("responding directly to slash command")
        response.type(APPLICATION_JSON)
        return@post buildResponse(message = translated, responseType = "in_channel")
      }

      val user = slackClient.getUserData(requestParams.getValue("user_id"))
      slackClient.sendToChannelAsUser(
          text = translated,
          channel = channel,
          user = user,
          fallbackUrl = requestParams.getValue("response_url"))

      status(204)
    }

    post("/action") {
      val payload = JSON.parseJsonObject(reader = request.parseBodyParams()
          .getValue("payload")
          .reader())

      val channel = payload.objString("channel", "id")
      val userId = payload.objString("user", "id")
      val text = payload.objString("message", "text")
      val responseUrl = payload.getString("response_url")

      if (text == null || userId == null || channel == null) {
        LOG.error("bizarre, slack sent a malformed message")
        LOG.error("body: ${request.body()}")
        slackClient.respondEphemeral(
            text = "Slack sent a malformed action :( try again?",
            responseUrl = responseUrl)
        return@post ""
      }

      if (text.isBlank()) {
        LOG.info("bad action request, empty message body")
        slackClient.respondEphemeral(
            text = "that message had no text! what, did you try emojifying an attachment-only message?",
            responseUrl = responseUrl)
        return@post ""
      }

      val translated = emojifier.translate(text.sanitized)
      if (translated.length > MAX_MESSAGE_LENGTH) {
        LOG.warn("user sent a string way too large")
        slackClient.respondEphemeral("bad string", responseUrl)
        return@post ""
      }

      if (channel.isDirectMessage || slackClient.isMpim(channel)) {
        LOG.debug("action responding to responseUrl")
        slackClient.respondInChannel(translated, responseUrl)
        status(204)
        return@post ""
      }

      val message = payload.obj("message")
      val threadTimestamp = message?.string("thread_ts")
          ?.takeUnless { it == message.string("ts") }
      val user = slackClient.getUserData(userId)
      LOG.debug("sending emojified message")
      slackClient.sendToChannelAsUser(
          text = translated,
          channel = channel,
          user = user,
          fallbackUrl = responseUrl,
          threadTimestamp = threadTimestamp)

      status(204)
    }

    post("/event") {
      val blob = JSON.parseJsonObject(request.body().reader())
      val maybeType = blob.string("type")

      if (maybeType == "url_verification") {
        LOG.info("returning challenge")
        blob.string("challenge") ?: ""
      } else {
        val event = blob.getObject("event")
        val type = event.getString("type")
        if (type == "user_change") {
          val userObject = event.getObject("user")
          val userId = userObject.getString("id")
          val profile = userObject.getObject("profile")
          slackClient.cacheUser(User(
              userId,
              profile.getString("real_name"),
              imageUrl = profile.string("image_original") ?: profile.getString("image_512")))
        } else LOG.error("no handler for event type $type")
        ""
      }
    }
  }
}

private val Request.isHealthcheck
  get() = pathInfo() == "/healthcheck"

private val String.isDirectMessage
  get() = startsWith('D')

private val String.sanitized: String
  get() = replace(Regex("""<@\S{9}>"""), "")
      .replace(Regex("""<([@#])\S{9}\|(\S+)>"""), "$1$2")

private val String.isNotRecentTimestamp
  get() = abs(System.currentTimeMillis() / 1000 - toInt()) > FIVE_MINUTES

private data class Config(
    val port: Int,
    val botToken: String,
    val oauthToken: String,
    val signingSecret: String,
    val emojiPath: String? = null,
    val userSeedPath: String? = null,
    val channelBlacklistPath: String? = null,
    val userBlacklistPath: String? = null)

private object Env {
  operator fun get(name: String): String =
      System.getenv(name) ?: throw IllegalStateException("missing env var: $name")
}

private fun getConfig(): Config {
  val handle = File(CONFIG_PATH)
  return if (handle.exists()) {
    LOG.info("using config file")
    JSON.parse<Config>(handle) ?: throw Exception("config file existed, but failed to 🅱️arse :/")
  } else Config(
      port = System.getenv("PORT")?.toInt() ?: 8080,
      emojiPath = System.getenv("EMOJI_PATH"),
      userSeedPath = System.getenv("USER_SEED_PATH"),
      botToken = Env["BOT_TOKEN"],
      oauthToken = Env["SLACK_OAUTH_TOKEN"],
      signingSecret = Env["SLACK_SIGNING_SECRET"],
      channelBlacklistPath = System.getenv("CHANNEL_BLACKLIST"),
      userBlacklistPath = System.getenv("USER_BLACKLIST"))
}

private fun getHasher(key: String) = Mac.getInstance(HASH_ALGORITHM)
    .apply { init(SecretKeySpec(key.toByteArray(), HASH_ALGORITHM)) }

private fun buildResponse(message: String, responseType: String = "ephemeral"): String = json { obj(
    "response_type" to responseType,
    "text" to message
)}.toJsonString()

private fun Mac.buildSignature(timestamp: String, body: String): String {
  val messageBytes = "$SIGNATURE_VERSION:$timestamp:$body".toByteArray()
  val hashedBytes = doFinal(messageBytes)
  return "$SIGNATURE_VERSION=${hashedBytes.toHexString()}"
}

private fun ByteArray.toHexString() = joinToString(separator = "") {
  Integer.toHexString(it.toInt() and 0xFF).padStart(length = 2, padChar = '0')
}

private fun Request.parseBodyParams(): Map<String, String> = body()
    .split('&')
    .associate {
      val (key, value) = it.split('=')
      key to URLDecoder.decode(value, UTF_8.name())
    }

private fun JsonObject.objString(obj: String, string: String): String? = obj(obj)?.string(string)
