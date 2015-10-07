package org.bentocorp.db

import slick.driver.MySQLDriver.simple._
import slick.lifted.Tag

class TAdminUser(tag: Tag) extends Table[(Long, Option[String], Option[String])](tag, "admin_User") {
  def pk_admin_User = column[Long]("pk_admin_User", O.PrimaryKey, O.AutoInc)
  def username = column[Option[String]]("username")
  def password = column[Option[String]]("password")
  def api_token = column[Option[String]]("api_token")
  def * = (pk_admin_User, username, password)
}

class AdminUserDao {

}
