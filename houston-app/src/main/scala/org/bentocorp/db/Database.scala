package org.bentocorp.db

import java.sql.{Statement, Connection, ResultSet}
import javax.annotation.PostConstruct

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource
import org.bentocorp.Config
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Database {

  final val Logger = LoggerFactory.getLogger(classOf[Database])

  @Autowired
  val config: Config = null

  var database: slick.driver.MySQLDriver.simple.Database = null

  var ds: MysqlDataSource = null

  @PostConstruct
  def init() {
    Logger.debug("Attempting to connect to database")
    database = slick.driver.MySQLDriver.simple.Database.forConfig("mysql", config())

    Logger.debug("Instantiating a data source")
    ds = new MysqlDataSource
    ds.setURL(config().getString("mysql.url"))
    ds.setUser(config().getString("mysql.user"))
    ds.setPassword(config().getString("mysql.password"))
  }

  def apply() = database

  // Need to fallback on DataSource because slick currently does not support "SELECT ... FOR UPDATE" queries
  def getDataSource = ds
}
