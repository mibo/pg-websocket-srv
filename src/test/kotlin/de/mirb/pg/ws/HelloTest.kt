package de.mirb.pg.ws

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

class HelloTest : StringSpec() {
  init {
    "length should return size of string" {
      val hw = HelloWorld()
      hw.sayHello("World") shouldBe "Hello World"
    }
  }
}