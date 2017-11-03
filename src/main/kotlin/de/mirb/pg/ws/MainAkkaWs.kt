package de.mirb.pg.ws

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.model.ws.WebSocket
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Source
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object WebSocketCoreExample {

  //#websocket-handling
  fun handleRequest(request: HttpRequest): HttpResponse {
    System.out.println("Handling request to " + request.getUri())

    if (request.getUri().path().equals("/greeter")) {
      val greeterFlow = greeter()
      return WebSocket.handleWebSocketRequestWith(request, greeterFlow)
    } else {
      return HttpResponse.create().withStatus(404)
    }
  }
  //#websocket-handling

  @Throws(Exception::class)
  @JvmStatic
  fun main(args: Array<String>) {
    val system = ActorSystem.create()

    try {
      val materializer = ActorMaterializer.create(system)

      val handler = { request: HttpRequest -> handleRequest(request) }
      val serverBindingFuture = Http.get(system).bindAndHandleSync(
          handler, ConnectHttp.toHost("localhost", 8080), materializer)

      // will throw if binding fails
      serverBindingFuture.toCompletableFuture().get(1, TimeUnit.SECONDS)
      println("Press ENTER to stop.")
      BufferedReader(InputStreamReader(System.`in`)).readLine()
    } finally {
      system.terminate()
    }
  }

  //#websocket-handler

  /**
   * A handler that treats incoming messages as a name,
   * and responds with a greeting to that name
   */
  fun greeter(): Flow<Message, Message, NotUsed> {
    return Flow.create<Message>()


//    return Flow.create<Message>()
//        .collect(object: JavaPartialFunction<Message, Message>() {
//          @Throws(Exception::class)
//          override fun apply(msg: Message, isCheck: Boolean): Message? {
//            return if (isCheck) {
//              if (msg.isText()) {
//                null
//              } else {
//                throw noMatch()
//              }
//            } else {
//              handleTextMessage(msg.asTextMessage())
//            }
//          }
//        })
  }

  /*
  val partFun = object: JavaPartialFunction<Message, Message>() {
  @Throws(Exception::class)
  override fun apply(msg: Message, isCheck: Boolean): Message? {
    return if (isCheck) {
      if (msg.isText()) {
        null
      } else {
        throw JavaPartialFunction.noMatch()
      }
    } else {
      WebSocketCoreExample.handleTextMessage(msg.asTextMessage())
    }
  }
}

   */

  fun handleTextMessage(msg: TextMessage): TextMessage {
    return if (msg.isStrict())
    // optimization that directly creates a simple response...
    {
      TextMessage.create("Hello " + msg.getStrictText())
    } else
    // ... this would suffice to handle all text messages in a streaming fashion
    {
      TextMessage.create(Source.single("Hello ").concat(msg.getStreamedText()))
    }
  }
  //#websocket-handler
}


class MainAkkaWs {
}