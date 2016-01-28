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

  def parse[T](str: String, manifest: Manifest[_]): T = Json.parse(str, getJavaType(manifest))

  // Recursively parse a Manifest object to construct a JavaType object representing complex, nested types for
  // deserialization
  // For example: Map[String, List[SomeWrapper[A]]]
  def getJavaType(manifest: Manifest[_]): JavaType = {
    if (manifest.typeArguments.isEmpty) {
      return ScalaJson.TypeFactory.constructType(manifest.runtimeClass)
    }
    val typeArguments: Seq[JavaType] = manifest.typeArguments.map(getJavaType).toSeq
    ScalaJson.TypeFactory.constructParametrizedType(manifest.runtimeClass, manifest.runtimeClass, typeArguments: _*)
  }

}
