package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import com.beust.klaxon.json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.Request
import spark.kotlin.halt
import spark.kotlin.ignite
import java.io.File
import java.net.URLDecoder

const val CONFIG_PATH = "./config.json"
const val EMOJI_PATH = "./emoji.json"
const val EMPTY_MESSAGE_ERR = "baka! I can't emojify an empty string! try again with some characters."
const val MAX_MESSAGE_SIZE = 500000

private val LOG: Logger = LoggerFactory.getLogger("me.shawnrc.kemohno.KemohnoKt")
private val JSON = Klaxon()

fun main(args: Array<String>) {
  val config = getConfig()
  val emojifier = Emojifier(args.firstOrNull()
      ?: config.emojiPath
      ?: EMOJI_PATH)
  val slackClient = SlackClient(
      config.oauthToken,
      config.botToken,
      cacheSeed = args.getOrNull(index = 1) ?: config.userSeedPath)

  ignite().apply {
    port(config.port)

    before {
      val log: (String) -> Unit = if (request.isHealthcheck) LOG::debug else LOG::info
      log("${request.requestMethod()} ${request.pathInfo()} ip=${request.ip()}")
    }

    get("/hello") {
      "hello"
    }

    get("/healthcheck") {
      LOG.debug("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")
      "OK"
    }

    post("/bepis") {
      if (request.queryParams("token") != config.verificationToken) {
        LOG.error("request had invalid token")
        halt(401)
      }
      val maybeText = request.queryParams("text")
      if (maybeText.isNullOrBlank()) {
        LOG.info("bad request, empty or nonexistent text field")
        response.type(APPLICATION_JSON)
        return@post buildEphemeral(EMPTY_MESSAGE_ERR)
      }

      val userId = request.queryParams("user_id")
      val user = slackClient.getUserData(userId)
      val translated = emojifier.translate(maybeText)

      if (translated.length > MAX_MESSAGE_SIZE) {
        LOG.error("user sent a string way too large")
        response.type(APPLICATION_JSON)
        return@post buildEphemeral(
            message = "that string was too large after emojification, try a smaller one.")
      }

      slackClient.sendMessage(
          text = translated,
          channel = request.queryParams("channel_id"),
          user = user,
          targetUrl = request.queryParams("response_url"))

      status(204)
    }

    post("/action") {
      val blob = URLDecoder.decode(request.queryParams("payload"), "utf-8")
      val payload = JSON.parseJsonObject(blob.reader())
      if (payload.string("token") != config.verificationToken) {
        LOG.error("request had invalid token")
        halt(401)
      }

      val channel = payload.obj("channel")?.string("id")
      val userId = payload.obj("user")?.string("id")
      val text = payload.obj("message")?.string("text")

      if (text == null || userId == null || channel == null) {
        LOG.error("bizarre, slack sent a malformed message")
        LOG.error("body: ${request.body()}")
        khttp.async.post(payload.getString("response_url"), json = mapOf(
            "response_type" to "ephemeral",
            "text" to "Slack sent a malformed action :( try again?"))
        return@post ""
      }

      if (text.isBlank()) {
        LOG.info("bad action request, empty message body")
        khttp.async.post(payload.getString("response_url"), json = mapOf(
            "response_type" to "ephemeral",
            "text" to "that message had no text! did you try emojifying an attachment-only message?"))
        return@post ""
      }

      val translated = emojifier.translate(text.sanitized)
      if (translated.length > MAX_MESSAGE_SIZE) {
        LOG.error("user sent a string way too large")
        khttp.async.post(payload.getString("response_url"), json = mapOf(
            "response_type" to "ephemeral",
            "text" to "bad string"
        ))
        halt(400)
      }

      val user = slackClient.getUserData(userId)
      LOG.debug("sending emojified message")
      slackClient.sendMessage(
          text = translated,
          channel = channel,
          user = user)

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
          slackClient.cacheUser(userId, User(
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

private data class Config(
    val port: Int,
    val botToken: String,
    val oauthToken: String,
    val verificationToken: String,
    val emojiPath: String? = null,
    val userSeedPath: String? = null)

private object Env {
  operator fun get(name: String): String =
      System.getenv(name) ?: throw Exception("missing env var: $name")
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
      verificationToken = Env["VERIFY_TOKEN"])
}

private fun buildEphemeral(message: String): String = json { obj(
    "response_type" to "ephemeral",
    "text" to message
)}.toJsonString()
