package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import com.beust.klaxon.json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.kotlin.ignite
import java.io.File

const val CONFIG_PATH = "./config.json"
val LOG: Logger = LoggerFactory.getLogger("Kemohno")
val JSON = Klaxon()

fun main(args: Array<String>) {
  val config = getConfig()
  ignite().apply {
    port(config.port)

    get("/hello") {
      "hello"
    }

    post("/bepis") {
      val userId = request.queryParams("user_id")
      LOG.info(request.queryParams().toString())
      val user = SlackClient.getUser(userId, config.oauthToken)
      response.type("application/json")
      json {
        obj(
            "text" to "wnelo",
            "as_user" to true,
            "icon_url" to user.imageUrl,
            "username" to user.realName,
            "response_type" to "in_channel")
      }.toJsonString()
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

fun getEnv(string: String): String {
  return System.getenv(string) ?: throw Exception("missing env var: $string")
}

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
