package me.shawnrc.kemohno

import spark.kotlin.ignite

fun main(args: Array<String>) {
  val config = getConfig()
  val http = ignite()
  http.port(config.port)

  http.get("lad") {
    "bungus\n"
  }
  http.post("/bepis") {

  }
}

fun getConfig(): Config {
  return Config(
      port=(System.getenv("PORT") ?: "4567").toInt()
  )
}

data class Config(val port: Int)
