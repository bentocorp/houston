package org.bentocorp.db

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import slick.driver.MySQLDriver.simple._
import slick.lifted.Tag

class TAdminUser(tag: Tag) extends Table[(Long, Option[String], Option[String])](tag, "admin_User") {
  def pk_admin_User = column[Long]("pk_admin_User", O.PrimaryKey, O.AutoInc)
  def username = column[Option[String]]("username")
  def password = column[Option[String]]("password")
  def api_token = column[Option[String]]("api_token")
  def * = (pk_admin_User, username, password)
}

@Component
class AdminUserDao extends IAuthDao {

  final val logger = LoggerFactory.getLogger(classOf[AdminUserDao])

  @Autowired
  var database: Database = null

  val adminUsers = TableQuery[TAdminUser]

  def getAuthenticationCredentials(username: String): (String, String) = database() withSession { implicit sesion =>
    val res = adminUsers.filter(_.username === username).map(row => (row.password, row.api_token)).list.head
    (res._1.get, res._2.get)
  }

  def getTokenByPrimaryKey(primaryKey: Long): Option[String] = database() withSession { implicit session =>
    val resultSet = adminUsers.filter(_.pk_admin_User === primaryKey).map(_.api_token).list
    if (!resultSet.isEmpty) {
      resultSet.head
    } else {
      logger.error("Error - no token rows returned for primary key " + primaryKey)
      None
    }
  }
}
