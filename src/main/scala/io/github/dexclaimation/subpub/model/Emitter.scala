//
//  Emitter.scala
//  over-layer
//
//  Created by d-exclaimation on 10:53 AM.
//


package io.github.dexclaimation.subpub.model

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.Source

import java.time.{Duration, Instant}
import scala.concurrent.duration.FiniteDuration

/**
 * Wrapper Object for ActorRef & Source
 *
 * @param actorRef ActorRef for pushing data into the source.
 * @param source   Source for data from the ActorRef.
 */
class Emitter(
  val actorRef: ActorRef[Any],
  val source: Source[Any, NotUsed],
  val timeout: FiniteDuration
) {
  var lastCalled: Instant = Instant.now()

  def isOff: Boolean = {
    val keepAlive = timeout.toMillis
    val currIdle = Duration.between(lastCalled, Instant.now())
      .toMillis
    keepAlive <= currIdle
  }

  /** Emit data to Actor Ref */
  def emit(msg: Any) = {
    actorRef ! msg
    lastCalled = Instant.now()
  }

  /** Operator for emit */
  def !(msg: Any) = {
    actorRef ! msg
    lastCalled = Instant.now()
  }
}

object Emitter {
  def apply(
    actorRef: ActorRef[Any],
    source: Source[Any, NotUsed],
    timeout: FiniteDuration
  ): Emitter = new Emitter(actorRef, source, timeout)
}
