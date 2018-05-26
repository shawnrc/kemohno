package me.shawnrc.kemohno

import spark.kotlin.ignite
import spark.kotlin.port

fun main(args: Array<String>) {
  println("PORT: ${System.getenv("PORT")}")
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
  return Config(
      port=(System.getenv("PORT") ?: "4567").toInt()
  )
}

data class Config(val port: Int)
