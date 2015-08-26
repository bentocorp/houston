package com.bento.core

import scala.collection.mutable

object Order {
  def apply(orderId: Long) = new Order(orderId)
}

class Order(val orderId: Long) {

  @JsonProperty
  case class Item(private val _itemId: Int, private val _label: String, name: String)

  val name: String = _
  val items: Map[String, Item] = _
  def size: Int = items.size

  val rejectors: mutable.Set[String] = mutable.Set.empty[String]

  def by(name: String) = {

  }

  def from(address: String) = {

  }

}