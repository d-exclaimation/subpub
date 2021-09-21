//
//  SubPubIntent.scala
//  over-layer
//
//  Created by d-exclaimation on 10:34 AM.
//


package io.github.dexclaimation.subpub.model

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.Source

/** SubPub Intents */
sealed trait SubIntent

object SubIntent {
  /** Fetch a source with a specific topic */
  case class Fetch(topic: String, rep: ActorRef[Source[Any, NotUsed]]) extends SubIntent

  /** Publish a payload to a topic */
  case class Publish(topic: String, payload: Any) extends SubIntent

  /** Complete a source and register a new one */
  case class Reinitialize(topic: String) extends SubIntent

  /** Kill / Complete a topic source */
  case class AcidPill(topic: String) extends SubIntent
}
