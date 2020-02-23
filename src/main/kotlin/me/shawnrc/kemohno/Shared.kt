package me.shawnrc.kemohno

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject

internal const val APPLICATION_JSON = "application/json; charset=utf-8"

internal val String.isHttp: Boolean
  get() = startsWith("http")

internal fun JsonObject.getString(field: String): String = fieldAccess(field, JsonObject::string)

internal fun JsonObject.getObject(field: String): JsonObject = fieldAccess(field, JsonObject::obj)

internal fun <T> JsonObject.getArray(field: String): JsonArray<T> = fieldAccess(field, JsonObject::array)

private inline fun <reified T> JsonObject.fieldAccess(field: String, accessor: JsonObject.(String) -> T?): T =
    accessor(field) ?: throw NoSuchElementException("missing ${T::class.simpleName} field \"$field\"")
