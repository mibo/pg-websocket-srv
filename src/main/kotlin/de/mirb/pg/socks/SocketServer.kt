package de.mirb.pg.socks

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.Executors

class SocketServer(private val port: Int) {
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  var runServer = true
  val executorService = Executors.newCachedThreadPool()

  fun startBounce() {
    val handler = SocketBounceHandler()
    start(handler)
  }

  fun startForward(forwardHost: String, forwardPort: Int) {
    val handler = SocketForwardHandler(forwardHost, forwardPort, true, -1)
    start(handler)
  }

  private fun start(handler: SocketHandler) {
    val serverSocket = ServerSocketChannel.open()
    serverSocket.configureBlocking(true)
    serverSocket.bind(InetSocketAddress("localhost", port))

    while(runServer) {
      LOG.info("Waiting for connection on port {} (handler={}).", port, handler.javaClass.name)
      val socket = serverSocket.accept()
      executorService.submit({
        LOG.info("Connection accepted on port {} and proceed with handler.", port)
        handler.start(socket)
      })
    }

  }

  fun stop() {
    runServer = false
  }

}