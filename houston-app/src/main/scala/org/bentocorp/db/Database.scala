package org.bentocorp.db

import java.sql.{Statement, Connection, ResultSet}
import javax.annotation.PostConstruct

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource
import org.bentocorp.houston.config.BentoConfig
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

object Database {

  object Map {
    def apply(kvs: (String, Any)*) = {
      val instance = new Map()
      kvs foreach { instance += _ }
      instance
    }
  }

  class Map extends scala.collection.mutable.HashMap[String, Any] {

    def apply[T](key: String)(implicit manifest: Manifest[T]): T = {
      super.apply(key).asInstanceOf[T]
    }

    def get[T](key: String)(implicit m: Manifest[T]): Option[T] = {
      super.get(key) match {
        case Some(o) => Some(o.asInstanceOf[T])
        case _ => None
      }
    }
  }

}

@Component
class Database {

  final val Logger = LoggerFactory.getLogger(classOf[Database])

  @Autowired
  val config: BentoConfig = null

  var database: slick.driver.MySQLDriver.simple.Database = null

  var ds: MysqlDataSource = null

  @PostConstruct
  def init() {
    Logger.debug("Attempting to connect to database")
    database = slick.driver.MySQLDriver.simple.Database.forConfig("mysql", config.toTypesafeConfig)

    Logger.debug("Instantiating a data source")
    ds = new MysqlDataSource
    ds.setURL(config.getString("mysql.url"))
    ds.setUser(config.getString("mysql.user"))
    ds.setPassword(config.getString("mysql.password"))
  }

  def apply() = database

  // Need to fallback on DataSource because slick currently does not support "SELECT ... FOR UPDATE" queries
  def getDataSource = ds
}

// Use an abstract class instead of a trait because abstract classes are fully interoperable with Java & Updatable
// requires constructor parameters
abstract class Updatable(tableName: String) {

  private val logger: Logger = LoggerFactory.getLogger(classOf[Updatable])

  var database: Database

  // Map keys must match column names exactly!
  def update(pk: Long, cols: Map[String, String]): Int = {
    var con: Connection = null
    try {
      // Try to obtain a connection from the pool
      con = database.getDataSource.getConnection

      val sql = "UPDATE `%s` SET %s WHERE pk_%s = %s" format (
        tableName, cols.map({ case (k, v) => "`%s`='%s'" format (k, v.replaceAll("'", "\\\\'")) }).mkString(","), tableName, pk
      )

      logger.debug(sql)

      con.prepareStatement(sql).executeUpdate()
    } finally {
      if (con != null) con.close()
    }
  }

}
