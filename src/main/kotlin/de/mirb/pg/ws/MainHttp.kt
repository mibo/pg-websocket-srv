package de.mirb.pg.ws

import io.javalin.Javalin

fun MainHttp(args: Array<String>) {
  val app = Javalin.start(7000)
  app.get("/") { ctx -> ctx.result("Hello World") }
}