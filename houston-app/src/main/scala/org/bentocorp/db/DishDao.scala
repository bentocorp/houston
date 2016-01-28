package org.bentocorp.db

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

class TDish(tag: Tag) extends Table[(Long, Option[String], Option[String], Option[String], Option[String])](tag, "Dish") {
  def pk_Dish = column[Long]("pk_Dish", O.PrimaryKey, O.AutoInc)
  def name = column[Option[String]]("name")
  def dishType = column[Option[String]]("type")
  def label = column[Option[String]]("label")
  def temp = column[Option[String]]("temp")
  def * = (pk_Dish, name, dishType, label, temp)
}

@Component
class DishDao {

  final val Logger = LoggerFactory.getLogger(classOf[DishDao])

  @Autowired
  var db: Database = null

  val dish = TableQuery[TDish]

  def selectAll = db() withSession { implicit session =>
    dish.list
  }
}
