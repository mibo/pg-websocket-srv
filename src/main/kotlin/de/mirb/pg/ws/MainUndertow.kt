package de.mirb.pg.ws

import de.mirb.pg.util.ContentHelper
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.util.Headers
import io.undertow.websockets.core.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

fun main(args: Array<String>) {
  val server = MainUndertow()
//  server.startHttp()
  server.startWs()
}

class MainUndertow {
  private val log = LoggerFactory.getLogger(this.javaClass.name)

  fun startHttp() {
    val server = Undertow.builder()
        .addHttpListener(8080, "localhost")
        .setHandler({ exchange ->
            exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/plain")
            exchange.responseSender.send("Hello World")
          })
        .build()
    server.start()
  }

  fun startWs() {
    val wsHandler = Handlers.websocket({ _, channel ->
      log.debug("WS connection started...")
      channel.receiveSetter.set(object: AbstractReceiveListener() {
        override fun onFullBinaryMessage(channel: WebSocketChannel?, message: BufferedBinaryMessage?) {
          log.trace("Received binary message.")
          // bounce
          if(message?.data?.resource?.size == 1) {
            val stream = ContentHelper.toStream(message.data.resource[0])
            log.trace("Received binary content='{}'.", stream.asString())
          }
          val data = ByteBuffer.wrap("BOUNCE".toByteArray())
          channel?.peerConnections?.forEach { WebSockets.sendBinary(data, it, null) }
        }
        override fun onFullTextMessage(channel: WebSocketChannel?, message: BufferedTextMessage?) {
          val messageData = message?.data
          log.trace("Received text message with content='{}'.", messageData)
          // bounce
          val response = "Bounce::" + messageData
          channel?.peerConnections?.forEach { WebSockets.sendText(response, it, null) }
        }
      })
      channel.resumeReceives()
      log.debug("WS connection established...")
    })

    val server = Undertow.builder()
        .addHttpListener(8080, "localhost")
        .setHandler(Handlers.path()
            .addPrefixPath("/", { exchange ->
              exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/plain")
              exchange.responseSender.send("Hello WS World (=> ws://host:port/ws)")
            })
            .addPrefixPath("/ws", wsHandler))
        .build()
    server.start()
  }
}