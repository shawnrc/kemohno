package me.shawnrc.kemohno

import com.beust.klaxon.JsonObject

const val APPLICATION_JSON = "application/json; charset=utf-8"

internal val String.sanitized: String
  get() = replace(Regex("""<@\S{9}>"""), "")
      .replace(Regex("""<([@#])\S{9}\|(\S+)>"""), "$1$2")

internal val String.isHttp: Boolean
  get() = startsWith("http")

internal fun JsonObject.getString(field: String): String =
    string(field) ?: throw NoSuchElementException("missing field $field")

internal fun JsonObject.getObject(field: String): JsonObject =
    obj(field) ?: throw NoSuchElementException("missing field $field")
