package org.bentocorp

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object ScalaJson {

  Json.mapper.registerModule(DefaultScalaModule)

  val TypeFactory = Json.mapper.getTypeFactory

  def stringify[T](value: T): String = Json.stringify(value)

  def parse[T](str: String, clazz: Class[T]): T = Json.parse(str, clazz)

  def parse[T](str: String, `type`: TypeReference[_]): T = Json.parse(str, `type`)

  def parse[T](str: String, `type`: JavaType): T = Json.parse(str, `type`)

}
