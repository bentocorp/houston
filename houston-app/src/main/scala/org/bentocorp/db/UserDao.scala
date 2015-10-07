package org.bentocorp.db

import slick.driver.MySQLDriver.simple._
import slick.lifted.Tag

class TUser(tag: Tag) extends Table[(Long, Option[String], Option[String], Option[String], Option[String],
  Option[String], Option[String])](tag, "User") {
  def pk_User = column[Long]("pk_User", O.PrimaryKey, O.AutoInc)
  def email = column[Option[String]]("email")
  def firstname = column[Option[String]]("firstname")
  def lastname = column[Option[String]]("lastname")
  def phone = column[Option[String]]("phone")
  def password = column[Option[String]]("password")
  def api_token = column[Option[String]]("api_token")
  def * = (pk_User, email, firstname, lastname, phone, password, api_token)
}

class UserDao {

}
