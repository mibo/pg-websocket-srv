package de.mirb.pg.ws

import io.javalin.Javalin
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
  val LOG = LoggerFactory.getLogger("de.mirb.pg.ws.MainWs")

  Javalin.create().apply {
    port(7070)
    enableStaticFiles("/public")
    ws("/ws") { ws ->
      ws.onConnect { session ->
        LOG.info("OnConnect")
      }
      ws.onClose { session, status, message ->
        LOG.info("OnClose (status={}; message={})", status, message)
      }
      ws.onMessage { session, message ->
        LOG.info("OnMessage (session.open={}): {}", session.isOpen, message)
        session.remote.sendString("Server received message: " + message)
        session.remote.flush()
        session.close()
      }
    }
  }.start()
}