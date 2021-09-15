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

/**
 * Wrapper Object for ActorRef & Source
 *
 * @param actorRef ActorRef for pushing data into the source.
 * @param source   Source for data from the ActorRef.
 */
case class Emitter(
  actorRef: ActorRef[Any],
  source: Source[Any, NotUsed]
) {
  /** Emit data to Actor Ref */
  def emit(msg: Any) = actorRef ! msg

  /** Operator for emit */
  def !(msg: Any) = actorRef ! msg
}
