package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit

class SocketForwardHandler(
    private val host: String,
    private val port: Int,
    private val webSocketSupportEnabled: Boolean = true,
    private val timeoutSeconds: Long = 10) : SocketHandler {

  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  private val sleepInMs = 200L
  private val daemonMode: Boolean = timeoutSeconds < 0
  private var run = true
  private val wsHandler = if(webSocketSupportEnabled) { WebSocketHandler() } else { null }

  override fun start(socket: SocketChannel) {
    val localConnection = socket.localAddress
    val remoteConnection = socket.remoteAddress
//    socket.configureBlocking(false)
    LOG.info("Connection (blocking={}) accepted from '{}' on '{}'. Start forwarding to {}:{}.",
            socket.isBlocking, remoteConnection, localConnection, host, port)
//    val inChannel = Channels.newChannel(socket.getInputStream())
//    val outChannel = Channels.newChannel(socket.getOutputStream())
    run = true

    val client = SocketChannelClient(host, port, false)
    LOG.trace("Forward client created for '{}'...", client.connection())

    var emptyCounter = TimeUnit.SECONDS.toMillis(timeoutSeconds) / sleepInMs
    val inBuffer = ByteBuffer.allocate(1024)
    LOG.debug("Wait for input data (run={})...", run)
    while(run) {
      LOG.debug("Wait (blocking={}) for input data (on {})...", socket.isBlocking, localConnection)
      val amount = socket.read(inBuffer)
      if(amount == -1) {
        // EOF
        LOG.trace("Received EOF.")
        run = false
      } else if(amount == 0) {
        if(!daemonMode && emptyCounter-- <= 0L) {
          LOG.debug("Server timeout reached. Set run='false'")
          run = false
          continue
        }
//        LOG.trace("Waiting for data (daemon={}, counter={}, sleep={})...", daemonMode, emptyCounter, sleepInMs)
        Thread.sleep(sleepInMs)
      } else {
        LOG.trace("Received '{}' bytes of data", amount)
        inBuffer.flip()
        val stream = ContentHelper.toStream(inBuffer)
        LOG.trace("Received: '{}'; start forward to {}", stream.asString(), client.connection())
        val response: ByteBuffer
        if(wsHandler != null) {
          if(wsHandler.isOpen()) {
            val inContent = wsHandler.unwrap(inBuffer)
            val fwdResponse = client.send(inContent.content)
            response = wsHandler.wrap(inContent.binary, fwdResponse)
            LOG.trace("Successful forwarded: '{}' (to {})", ContentHelper.asString(inContent.content), client.connection())
            LOG.trace("Got response (from: {}): {}", client.connection(), ContentHelper.asString(fwdResponse))
            LOG.trace("Wrapped response (content='{}' from forward connection {}) and write back to connection ({})",
                    ContentHelper.asString(response), client.connection(), localConnection)
          } else if(wsHandler.isWebSocketRequest(stream)) {
            LOG.trace("Received web socket upgrade request '{}'", localConnection)
            response = wsHandler.createWebSocketResponse(stream)
            LOG.trace("Successful wrote ws upgrade response back for connection {} with response content \n'{}'",
                localConnection, ContentHelper.toStream(response).asString())
          } else {
            response = wsHandler.createBadRequestResponse()
          }
        } else {
          response = client.send(inBuffer)
          LOG.trace("Successful forwarded: '{}' (to {})", stream.asString(), client.connection())
          val responseStream = ContentHelper.toStream(response)
          LOG.trace("Got response (from: {}): {}", client.connection(), responseStream.asString())
          LOG.trace("Write response (from {}) back to forward connection ({})", localConnection, client.connection())
        }
        socket.write(response)
        inBuffer.clear()
        // flush?
//        socket.getOutputStream().flush()
        //
        LOG.trace("Successful wrote response back to {} from forward connection {}", remoteConnection, client.connection())
      }
    }

    LOG.info("Close channels/client/socket for forward handler")
//    inChannel.close()
//    outChannel.close()
    socket.close()
    client.close()
    wsHandler?.close()
  }

  override fun stop() {
    run = false
  }
}