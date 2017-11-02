package de.mirb.pg.socks

/**
 * Sample
 *  - Start first (bounce server) with parameter (server port=19382)
 *  - Start second (forward server) with parameter (server port=19381 forward forwardPort=19382)
 *  - Start third (client) without parameters
 */
fun main(args: Array<String>) {
  if(args.contains("server")) {
    val port = getPort(args)
    val server = SocketServer(port)
    if(args.contains("forward")) {
      val fwdPort = getForwardPort(args)
      val forwardHost = getHost(args)
      server.startForward(forwardHost, fwdPort)
    } else {
      server.startBounce()
    }
  } else {
    val port = getPort(args, "19381")
    val host = getHost(args, "localhost")
    val client = SocketClient(host, port)
    client.send("Some content")
    client.send("More content")
    client.close()
  }
}

fun getHost(args: Array<String>, default: String = "localhost"): String {
  return getArgumentValue(args, "host") ?: return default
}

fun getPort(args: Array<String>, default: String = "19381"): Int {
  val portValue = getArgumentValue(args, "port") ?: default
  return Integer.parseInt(portValue)
}

fun getForwardPort(args: Array<String>, default: String = "19381"): Int {
  val portValue = getArgumentValue(args, "forwardPort") ?: default
  return Integer.parseInt(portValue)
}

fun getArgumentValue(args: Array<String>, startsWith: String): String? {
  val starts = if(startsWith.endsWith("=")) {
    startsWith
  } else {
    startsWith + "="
  }
  val argument = args.singleOrNull { a -> a.startsWith(starts) } ?: return null
  return argument.substring(starts.length)
}
