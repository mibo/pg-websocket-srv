package de.mirb.pg.ws

import io.javalin.Javalin
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
  val ms = MainWs()
  ms.start()
}

class MainWs {
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)

  fun start() {
    Javalin.create().apply {
      port(7070)
      enableStaticFiles("/public")
      post("/post",  { context ->
        LOG.info("POST headers = {}", context.headerMap())
//        LOG.info("POST body = '{}'", context.request().inputStreamody())
        LOG.info("POST body = '{}'", context.body())

        context.response().outputStream.write(context.bodyAsBytes())
      })
      ws("/ws") { ws ->
        ws.onConnect { session ->
          LOG.info("OnConnect -> remote={}", session.remoteAddress)
        }
        ws.onClose { session, status, message ->
          LOG.info("OnClose (remote={}; status={}; message={})", session.remoteAddress, status, message)
        }
        ws.onMessage { session, message ->
          LOG.info("OnMessage (session.open={}): {}", session.isOpen, message)
          session.remote.sendString("Server received message: " + message)
          session.remote.flush()
          if("disconnect" == message.toLowerCase()) {
            session.remote.sendString(
                "Server received 'disconnect' command -> close session from remote: " + session.remoteAddress)
            session.remote.flush()
            session.close()
          }
        }
      }
    }.start()
  }
}