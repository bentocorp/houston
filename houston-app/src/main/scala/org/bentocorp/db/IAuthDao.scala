package org.bentocorp.db

trait IAuthDao {
  // Returns password & access token (if any)
  def getAuthenticationCredentials(username: String): (String, String)

  def getTokenByPrimaryKey(primaryKey: Long): Option[String]
}
