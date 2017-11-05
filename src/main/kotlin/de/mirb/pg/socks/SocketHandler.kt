package de.mirb.pg.socks

import java.nio.channels.SocketChannel

interface SocketHandler {
  fun start(socket: SocketChannel)
  fun stop()
}