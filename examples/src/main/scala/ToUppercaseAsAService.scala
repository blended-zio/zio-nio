package zio
package nio
package examples

import java.io.IOException

import zio.blocking.Blocking
import zio.console.Console
import zio.nio.core._
import zio.nio.core.channels.{ ServerSocketChannel, SocketChannel }
import zio.nio.core.charset.Charset
import zio.stream.ZTransducer

import scala.util.control.Exception._

/**
 * `toUpperCase` as a service.
 *
 * Using ZIO-NIO and ZIO streams to build a very silly TCP service.
 * Listens on port 7777 by default.
 *
 * Send it UTF-8 text and it will send back the uppercase version. Amazing!
 */
object ToUppercaseAsAService extends App {

  private val upperCaseIfier = ZTransducer.identity[Char].map(_.toUpper)

  private def handleConnection(socket: SocketChannel.Blocking): ZIO[Blocking with Console, IOException, Long] = {

    // this does the processing of the characters received over the channel via a transducer
    // the stream of bytes from the channel is transduced, then written back to the same channel's sink
    def transducer =
      Charset.Standard.utf8.newDecoder.transducer() >>>
        upperCaseIfier >>>
        Charset.Standard.utf8.newEncoder.transducer()
    console.putStrLn("Connection accepted") *>
      socket
        .stream()
        .transduce(transducer)
        .run(socket.sink())
        .tapBoth(
          e => console.putStrLn(s"Connection error: $e"),
          i => console.putStrLn(s"Connection ended, wrote $i bytes")
        )
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val port = args.headOption
      .flatMap(s => catching(classOf[IllegalArgumentException]).opt(s.toInt))
      .getOrElse(7777)

    val startServer = for {
      serverChannel <- ServerSocketChannel.Blocking.open
      socketAddress <- SocketAddress.inetSocketAddress(port)
      _             <- serverChannel.bind(socketAddress)
      _             <- console.putStrLn(s"Listening on $socketAddress")
    } yield serverChannel
    startServer
      .flatMap(c => c.accept.toManagedNio.useForked(handleConnection).forever)
      .exitCode
  }
}
