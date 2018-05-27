package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.kotlin.halt
import spark.kotlin.ignite
import java.io.File
import java.io.FileNotFoundException

const val CONFIG_PATH = "./config.json"
const val EMOJI_PATH = "./emoji.json"
val LOG: Logger = LoggerFactory.getLogger("Kemohno")
val JSON = Klaxon()

fun main(args: Array<String>) {
  val config = getConfig()
  val emojifier = if (args.size == 1) getEmojifier(args[0]) else getEmojifier()
  ignite().apply {
    port(config.port)

    get("/hello") {
      "hello"
    }

    post("/bepis") {
      if (request.queryParams("token") != config.verificationToken) halt(403)

      val userId = request.queryParams("user_id")
      val user = SlackClient.getUser(userId, config.oauthToken)
      SlackClient.sendMessage(
          text = emojifier.translate(request.queryParams("text")),
          channel = request.queryParams("channel_id"),
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

fun getEnv(string: String): String = System.getenv(string) ?: throw Exception("missing env var: $string")

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

fun getEmojifier(path: String = EMOJI_PATH): Emojifier {
  val handle = File(path)
  handle.exists() || throw FileNotFoundException("failed to find emojifile at $EMOJI_PATH")
  return Emojifier(handle)
}
