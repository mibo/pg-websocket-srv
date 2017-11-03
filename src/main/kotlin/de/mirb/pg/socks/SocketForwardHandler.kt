package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import javax.xml.bind.DatatypeConverter
import javax.xml.crypto.dsig.DigestMethod

class SocketForwardHandler(
    private val host: String,
    private val port: Int,
    private val webSocketSupportEnabled: Boolean = true,
    private val timeoutSeconds: Long = 10) : SocketHandler {

  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  private val sleepInMs = 200L
  private val daemonMode: Boolean = timeoutSeconds < 0
  private var run = true

  override fun start(socket: Socket) {
    val localConnection = socket.inetAddress.hostName + ":" + socket.port
    LOG.info("Connection accepted on '{}'. Start forwarding to {}:{}.", localConnection, host, port)
    val inChannel = Channels.newChannel(socket.getInputStream())
    val outChannel = Channels.newChannel(socket.getOutputStream())
    run = true

    val client = SocketChannelClient(host, port, false)
    LOG.trace("Forward client created for '{}'...", client.connection())

    var emptyCounter = TimeUnit.SECONDS.toMillis(timeoutSeconds) / sleepInMs
    val inBuffer = ByteBuffer.allocate(1024)
    LOG.debug("Wait for input data (run={})...", run)
    while(run) {
      LOG.debug("Wait (blocking) for input data (on {})...", localConnection)
      val amount = inChannel.read(inBuffer)
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
        LOG.trace("Waiting for data (daemon={}, counter={}, sleep={})...", daemonMode, emptyCounter, sleepInMs)
        Thread.sleep(sleepInMs)
      } else {
        LOG.trace("Received '{}' bytes of data", amount)
        inBuffer.flip()
        val stream = ContentHelper.toStream(inBuffer)
        LOG.trace("Received: '{}'; start forward to {}", stream.asString(), client.connection())
        if(webSocketSupportEnabled && isWebSocketRequest(stream)) {
          LOG.trace("Received web socket upgrade request '{}'", localConnection)
          val wsResponse = createWebSocketResponse(stream)
          outChannel.write(wsResponse)
          inBuffer.clear()
          LOG.trace("Successful wrote ws upgrade response back for connection {} with response content \n'{}'",
              localConnection, ContentHelper.toStream(wsResponse).asString())
        } else {
          val response = client.send(inBuffer)
          LOG.trace("Successful forwarded: '{}' (to {})", stream.asString(), client.connection())
          val responseStream = ContentHelper.toStream(response)
          LOG.trace("Got response (from: {}): {}", client.connection(), responseStream.asString())
          LOG.trace("Write response (from {}) back to forward connection ({})", localConnection, client.connection())
          outChannel.write(response)
          inBuffer.clear()
          LOG.trace("Successful wrote response back for connection {}", client.connection())
        }

      }
    }

    LOG.info("Close channels/client/socket for forward handler")
    inChannel.close()
    outChannel.close()
    socket.close()
    client.close()
  }

  private fun isWebSocketRequest(stream: ContentHelper.Stream): Boolean {
    if(stream.asString().startsWith("GET")) {
      return true
    }
    return false
  }

  private val SEC_WS_KEY_HEADER = "Sec-WebSocket-Key"

  private fun createWebSocketResponse(stream: ContentHelper.Stream): ByteBuffer {
    val lines = stream.asString().lines()
    val secWebSocketKey = lines.singleOrNull { l -> l.startsWith(SEC_WS_KEY_HEADER) }
    // handle failure
    if(secWebSocketKey == null) {
      return ByteBuffer.wrap("HTTP/1.1 400 Bad Request".toByteArray())
    }
    val value = secWebSocketKey.substring(SEC_WS_KEY_HEADER.length+1).trim() + WS_GUID
    val md = MessageDigest.getInstance("SHA1")
    val digest = md.digest(value.toByteArray(StandardCharsets.ISO_8859_1))
    val secWsAcceptValue = String(Base64.getEncoder().encode(digest))
    val res = WS_RESPONSE_TEMPLATE.replace("<accept>", secWsAcceptValue)
    val tmp = res.toByteArray(StandardCharsets.US_ASCII)
    return ByteBuffer.wrap(tmp)
  }

  // https://tools.ietf.org/html/rfc6455.html#section-1.3
  private val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
  private val EOL = "\r\n"
  private val WS_RESPONSE_TEMPLATE =
      "HTTP/1.1 101 Switching Protocols" + EOL +
      "Server: mibo pg websocket" + EOL +
      "Connection: Upgrade" + EOL +
      "Upgrade: websocket" + EOL +
      "Sec-WebSocket-Version: 13" + EOL +
      "Sec-WebSocket-Accept: <accept>" + EOL + EOL

  override fun stop() {
    run = false
  }
}