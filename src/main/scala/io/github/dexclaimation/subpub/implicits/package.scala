//
//  package.scala
//  over-layer
//
//  Created by d-exclaimation on 10:16 AM.
//


package io.github.dexclaimation.subpub

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Concat, Keep, Source}

import java.time.Instant

package object implicits {
  /** Extensions for middleware capabilities */
  implicit class SourceMiddleware[T](source: Source[T, NotUsed]) {

    /** Tap each value to perform side effects */
    def tap(tapFn: T => Unit): Source[T, NotUsed] = source
      .map { value =>
        tapFn(value)
        value
      }

    /** Log data stream */
    def log(
      topic: String,
      predicate: T => Boolean = _ => true,
      logger: String => Unit = println
    ): Source[T, NotUsed] = source
      .tap { value =>
        if (predicate(value)) logger(s"[ $topic ] >> ${value.toString} on ${Instant.now().toString}")
      }

    /**
     * Make a source into a broadcast hub that acts as publisher to multiple consumer.
     *
     * @param bufferSize Buffer size for broadcast hub (in power of 2)
     * @param mat        Implicit materializer
     * @return A source of the same type
     */
    def toBroadcastHub(bufferSize: Int = 256)(implicit mat: Materializer): Source[T, NotUsed] = {
      val isPowerOf2 = (bufferSize & bufferSize - 1) == 0
      val isBetween = 0 < bufferSize && bufferSize < 4096
      val bs = if (isPowerOf2 && isBetween) bufferSize else 256
      source.toMat(BroadcastHub.sink[T](bs))(Keep.right).run()
    }

    /**
     * Concat Source in order where once the previous finished the next is fired.
     *
     * @param other         The compulsory next source.
     * @param otherInOrders The optionals next sources.
     * @return Combined source.
     */
    def concatWith(other: Source[T, _], otherInOrders: Source[T, _]*): Source[T, NotUsed] =
      Source.combine(source, other, otherInOrders: _*)(Concat(_))
  }
}
