package me.shawnrc.kemohno

import spark.kotlin.ignite

fun main(args: Array<String>) {
  val http = ignite()

  http.get("lad") {
    "bungus\n"
  }
  http.post("/bepis") {

  }
}
