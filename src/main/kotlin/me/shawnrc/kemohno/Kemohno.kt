package me.shawnrc.kemohno

import com.beust.klaxon.Klaxon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.Request
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

    fun dumpQueryParams(request: Request): String {
      return buildString {
        append("{\n")
        for (key in request.queryParams()) {
          append("    \"$key\": \"${request.queryParams(key)}\",\n")
        }
        append('}')
      }.replace(",\n}","\n}")
    }

    post("/bepis") {
      System.err.println(dumpQueryParams(request))
      val userId = request.queryParams("user_id")
      val user = SlackClient.getUser(userId, config.oauthToken)
      SlackClient.sendMessage(
          text = request.,
          channel = request.queryParams("channel_id"),
          user = user,
          oauthToken = config.oauthToken)

      response.type("application/json")
      ""
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
