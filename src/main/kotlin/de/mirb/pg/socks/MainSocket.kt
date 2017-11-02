package de.mirb.pg.socks

fun main(args: Array<String>) {
  if(args.contains("server")) {
    val port = getPort(args)
    val server = SocketServer(port)
    if(args.contains("forward")) {
      val fwdPort = getForwardPort(args)
      val forwardHost = "localhost"
      server.startForward(forwardHost, fwdPort)
    } else {
      server.startBounce()
    }
  } else {
    val client = SocketClient()
    client.sendTo("localhost", 19382, "Some content")
  }
}

fun getPort(args: Array<String>): Int {
  val portValue = getArgumentValue(args, "port")
  if (portValue == null) {
    return 19381
  }
  return Integer.parseInt(portValue)
}

fun getForwardPort(args: Array<String>): Int {
  val portValue = getArgumentValue(args, "forwardPort")
  if (portValue == null) {
    return 19381
  }
  return Integer.parseInt(portValue)
}

fun getArgumentValue(args: Array<String>, startsWith: String): String? {
  val starts = if(startsWith.endsWith("=")) {
    startsWith
  } else {
    startsWith + "="
  }
  val argument = args.singleOrNull { a -> a.startsWith(starts) }
  if (argument == null) {
    return null
  }
  return argument.substring(starts.length)
}
