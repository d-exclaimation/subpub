//
//  package.scala
//  over-layer
//
//  Created by d-exclaimation on 10:16 AM.
//


package io.github.dexclaimation.subpub

import akka.NotUsed
import akka.stream.scaladsl.Source

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
  }
}
