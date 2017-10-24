package de.mirb.pg.ws

class HelloWorld {
  fun sayHello(name: String): String {
    return "Hello " + name
  }
}

fun main(args: Array<String>) {
  val h = HelloWorld()
  println(h.sayHello("world!"))
}
