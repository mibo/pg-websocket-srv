package de.mirb.pg.socks

import java.net.Socket

interface SocketHandler {
  fun start(socket: Socket)
  fun stop()
}