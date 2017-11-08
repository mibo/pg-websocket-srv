package de.mirb.pg.ws

import de.mirb.pg.socks.SocketChannelClient
import de.mirb.pg.util.ContentHelper
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.util.Headers
import io.undertow.websockets.core.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*

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
          val inBuffer = ByteBuffer.allocate(1024*64)
          message?.data?.resource?.forEach { inBuffer.put(it) }
          inBuffer.flip()
          val stream = ContentHelper.toStream(inBuffer)
          val client = grantForwardSocket()
          log.trace("Received binary content='{}'.", stream.asString())
          log.trace("Received: '{}'; start forward to {}", stream.asString(), client.connection())
          val response = client.send(inBuffer)
          log.trace("Successful forwarded: '{}' (to {})", stream.asString(), client.connection())
          val responseStream = ContentHelper.toStream(response)
          log.trace("Got response (from: {}): {}", client.connection(), responseStream.asString())
//          log.trace("Write response from forward connection ({}) back.", client.connection())
          //
          channel?.peerConnections?.forEach {
            log.trace("Write response from forward connection ({}) back.", client.connection())
            WebSockets.sendBinary(response, it, null, 2000)
//            WebSockets.sendBinaryBlocking(response, it)
          }
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

  // TODO: change double ctor usage
//  private var client: SocketChannelClient = SocketChannelClient("localhost", 35672, false)
  private var client: SocketChannelClient? = null
  private fun grantForwardSocket(): SocketChannelClient {
    if(client?.isClosed() != false) {
      client = SocketChannelClient("localhost", 35672, false)
    }
    return client ?: SocketChannelClient("localhost", 35672, false)
  }
}