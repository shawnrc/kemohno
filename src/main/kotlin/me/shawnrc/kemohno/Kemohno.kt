package me.shawnrc.kemohno

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.json
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.kotlin.halt
import spark.kotlin.ignite
import java.awt.SystemColor.text
import java.io.File

const val CONFIG_PATH = "./config.json"
const val EMOJI_PATH = "./emoji.json"
const val APPLICATION_JSON = "application/json; charset=utf-8"

val LOG: Logger = LoggerFactory.getLogger("me.shawnrc.kemohno.KemohnoKt")
val JSON = Klaxon()

fun main(args: Array<String>) {
  val config = getConfig()
  val emojifier = if (args.isNotEmpty()) Emojifier(args[0]) else Emojifier(EMOJI_PATH)
  ignite().apply {
    port(config.port)

    get("/hello") {
      LOG.info("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")
      "hello"
    }

    post("/bepis") {
      LOG.info("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")
      if (request.queryParams("token") != config.verificationToken) {
        LOG.error("request had invalid token")
        halt(403)
      }
      val maybeText = request.queryParams("text")
      if (maybeText == null || maybeText.isEmpty()) {
        LOG.info("bad request, empty or nonexistent text field")
        response.type(APPLICATION_JSON)
        status(400)
        return@post json { obj(
            "response_type" to "ephemeral",
            "text" to "baka! I can't emojify an empty string! try again with some characters.")
        }.toJsonString()
      }

      val userId = request.queryParams("user_id")
      val user = SlackClient.getUserData(userId, config.oauthToken)
      SlackClient.sendMessage(
          text = emojifier.translate(request.queryParams("text")),
          channel = request.queryParams("channel_id"),
          user = user,
          oauthToken = config.oauthToken)

      status(204)
    }

    post("/action") {
      LOG.info("method=${request.requestMethod()} path=${request.pathInfo()} ip=${request.ip()}")
      if (request.queryParams("token") != config.verificationToken) halt(403)

      val payload = JSON.parseJsonObject(request.body().reader()).obj("payload")
      val channel = payload?.obj("channel")?.string("id")
      val message = payload?.obj("message")

      val userId = message?.string("user")
      val text = message?.string("text")

      if (text == null || userId == null || channel == null) {
        LOG.error("bizarre, slack sent a malformed message")
        LOG.error("body: ${request.body()}")
        response.type(APPLICATION_JSON)
        status(400)
        return@post json { obj(
            "response_type" to "ephemeral",
            "text" to "slack failed to send a valid message")
        }.toJsonString()
      }

      val user = SlackClient.getUserData(userId, config.oauthToken)
      SlackClient.sendMessage(
          text = emojifier.translate(text),
          channel = channel,
          user = user,
          oauthToken = config.oauthToken)
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
