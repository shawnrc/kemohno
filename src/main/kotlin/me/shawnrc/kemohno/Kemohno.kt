package me.shawnrc.kemohno

import spark.kotlin.ignite
import spark.kotlin.port

fun main(args: Array<String>) {
  val config = getConfig()
  port(config.port)
  val http = ignite()

  http.get("lad") {
    "bungus\n"
  }
  http.post("/bepis") {

  }
}

fun getConfig(): Config {
  val port = (System.getenv("PORT") ?: "4567").toInt()
  return Config(port=port)
}

data class Config(val port: Int)
