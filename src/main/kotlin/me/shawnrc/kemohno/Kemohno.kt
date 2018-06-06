package me.shawnrc.kemohno

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.kotlin.halt
import spark.kotlin.ignite
import java.io.File
import java.net.URLDecoder

const val CONFIG_PATH = "./config.json"
const val EMOJI_PATH = "./emoji.json"
const val APPLICATION_JSON = "application/json; charset=utf-8"
const val MAX_MESSAGE_SIZE = 8000

val LOG: Logger = LoggerFactory.getLogger("me.shawnrc.kemohno.KemohnoKt")
val JSON = Klaxon()

fun main(args: Array<String>) {
  val config = getConfig()
  val emojifier = if (args.isNotEmpty()) Emojifier(args[0]) else Emojifier(EMOJI_PATH)
  val userCacheSeed = if (args.size > 1) args[1] else null
  val slackClient = if (userCacheSeed != null) {
    SlackClient(config.oauthToken, userCacheSeed)
  } else {
    SlackClient(config.oauthToken)
  }

  ignite().apply {
    port(config.port)

    get("/hello") {
      LOG.info("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")
      "hello"
    }

    get("/healthcheck") {
      LOG.debug("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")
      "OK"
    }

    post("/bepis") {
      LOG.info("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")
      if (request.queryParams("token") != config.verificationToken) {
        LOG.error("request had invalid token")
        halt(403)
      }
      val maybeText = request.queryParams("text")
      if (maybeText.isNullOrBlank()) {
        LOG.info("bad request, empty or nonexistent text field")
        response.type(APPLICATION_JSON)
        status(400)
        return@post json { obj(
            "response_type" to "ephemeral",
            "text" to "baka! I can't emojify an empty string! try again with some characters."
        )}.toJsonString()
      }

      val userId = request.queryParams("user_id")
      val user = slackClient.getUserData(userId)
      val translated = emojifier.translate(maybeText)

      if (translated.length > MAX_MESSAGE_SIZE) {
        LOG.error("user sent a string way too large")
        response.type(APPLICATION_JSON)
        status(400)
        return@post json { obj(
            "response_type" to "ephemeral",
            "text" to "that string was too large after emojification, try a smaller one."
        )}.toJsonString()
      }

      slackClient.sendMessage(
          text = translated,
          channel = request.queryParams("channel_id"),
          user = user)

      status(204)
    }

    post("/action") {
      LOG.info("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")

      val blob = URLDecoder.decode(request.queryParams("payload"), "utf-8")
      val payload = JSON.parseJsonObject(blob.reader())
      if (payload.string("token") != config.verificationToken) {
        LOG.error("request had invalid token")
        halt(403)
      }

      val channel = payload.obj("channel")?.string("id")
      val userId = payload.obj("user")?.string("id")
      val text = payload.obj("message")?.string("text")

      if (text == null || userId == null || channel == null) {
        LOG.error("bizarre, slack sent a malformed message")
        LOG.error("body: ${request.body()}")
        khttp.async.post(payload.getString("response_url"), json = mapOf(
            "response_type" to "ephemeral",
            "text" to "Slack sent a malformed action :( try again?"
        ))
        status(400)
        return@post ""
      }

      val translated = emojifier.translate(text)
      if (translated.length > 8000) {
        LOG.error("user sent a string way too large")
        khttp.async.post(payload.getString("response_url"), json = mapOf(
            "response_type" to "ephemeral",
            "text" to "bad string"
        ))
        halt(400)
      }

      val user = slackClient.getUserData(userId)
      LOG.info("sending emojified message")
      slackClient.sendMessage(
          text = translated,
          channel = channel,
          user = user)

      status(204)
    }
  }
}

data class Config(
    val appId: String,
    val clientId: String,
    val clientSecret: String,
    val oauthToken: String,
    val port: Int,
    val verificationToken: String)

fun getEnv(string: String): String =
    System.getenv(string) ?: throw Exception("missing env var: $string")

fun getConfig(): Config {
  val handle = File(CONFIG_PATH)
  return if (handle.exists()) {
    LOG.info("using config file")
    JSON.parse<Config>(handle) ?: throw Exception("config file existed, but failed to 🅱️arse :/")
  } else Config(
      appId = getEnv("APP_ID"),
      clientId = getEnv("CLIENT_ID"),
      clientSecret = getEnv("CLIENT_SECRET"),
      oauthToken = getEnv("SLACK_OAUTH_TOKEN"),
      port = System.getenv("PORT")?.toInt() ?: 4567,
      verificationToken = getEnv("VERIFY_TOKEN"))
}

fun JsonObject.getString(field: String): String =
    string(field) ?: throw NoSuchElementException()
