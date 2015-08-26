package com.bento

object Preamble {
  final implicit def strToArray(str: String) = Array(str)
}
