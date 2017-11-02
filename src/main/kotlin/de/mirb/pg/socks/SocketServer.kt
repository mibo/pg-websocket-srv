package de.mirb.pg.socks

import org.slf4j.LoggerFactory
import java.net.ServerSocket

class SocketServer(private val port: Int) {
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  var runServer = true

  fun startBounce() {
    val handler = SocketBounceHandler()
    start(handler)
  }

  fun startForward(forwardHost: String, forwardPort: Int) {
    val handler = SocketForwardHandler(forwardHost, forwardPort)
    start(handler)
  }

  private fun start(handler: SocketHandler) {
    val serverSocket = ServerSocket(port)

    while(runServer) {
      LOG.info("Waiting for connection on port {} (handler={}).", port, handler.javaClass.name)
      val socket = serverSocket.accept()
      LOG.info("Connection accepted on port {} and proceed with handler.", port)
      handler.start(socket)
    }

  }

  fun stop() {
    runServer = false
  }

}