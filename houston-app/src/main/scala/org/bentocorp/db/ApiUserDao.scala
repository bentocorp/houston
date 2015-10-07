package org.bentocorp.db

import slick.driver.MySQLDriver.simple._
import slick.lifted.Tag

class TApiUser(tag: Tag) extends Table[(Long, Option[String], Option[String], Option[String], Option[String])](tag, "api_User") {
  def pk_api_User = column[Long]("pk_api_User", O.PrimaryKey, O.AutoInc)
  def api_username = column[Option[String]]("api_username")
  def api_password = column[Option[String]]("api_password")
  def email = column[Option[String]]("email")
  def api_token = column[Option[String]]("api_token")
  def * = (pk_api_User, api_username, api_password, email, api_token)
}

class ApiUserDao {

}
