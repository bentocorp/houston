package org.bentocorp.db

import java.sql.Timestamp

import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

class TSettings(tag: Tag) extends Table[(String, Option[String], Option[Timestamp], Option[Byte],
  Option[String])](tag, "settings") {

  def key = column[String]("key")

  def value = column[Option[String]]("value")

  def updated_at = column[Option[Timestamp]]("updated_at")

  def public = column[Option[Byte]]("public")

  def comment = column[Option[String]]("comment")

  def * = (key, value, updated_at, public, comment)
}

@Component
class SettingsDao {

  val logger: Logger = LoggerFactory.getLogger(classOf[SettingsDao])

  @Autowired
  var database: Database = null

  val settings = TableQuery[TSettings]

  def select(key: String) = database() withSession { implicit session =>
    settings.filter(_.key === key).list.head
  }

  def update(key: String, value: String): Int = database() withSession { implicit session =>
    val row = for { s <- settings if s.key === key } yield s.value
    row.update(Some(value))
  }
}
