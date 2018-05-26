package me.shawnrc.kemohno

import spark.kotlin.ignite

fun main(args: Array<String>) {
  val config = getConfig()
  ignite().apply {
    port(config.port)

    get("/hello") {
      "hello"
    }

    post("/bepis") {
      val userId = request.queryParams("user_id")
      response.type("application/json")
      mapOf(
          "response_type" to "in_channel",
          "text" to "wnelo",
          "as_user" to userId
      ).toJson()
    }
  }
}

fun getConfig(): Config {
  return Config(
      port=(System.getenv("PORT") ?: "4567").toInt()
  )
}

data class Config(val port: Int)

fun Map<String, String>.toJson(): String {
  return buildString {
    append('{')
    for ((key, value) in this@toJson) {
      append("\"$key\": \"$value\",")
    }
    append('}')
  }.replace(",}", "}")
}
