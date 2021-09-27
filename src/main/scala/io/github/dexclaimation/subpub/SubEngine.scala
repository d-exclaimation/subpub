//
//  SubPubEngine.scala
//  over-layer
//
//  Created by d-exclaimation on 10:16 AM.
//

package io.github.dexclaimation.subpub


import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.stream.Materializer
import akka.stream.Materializer.createMaterializer
import akka.stream.scaladsl.Sink
import io.github.dexclaimation.subpub.implicits._
import io.github.dexclaimation.subpub.model.Subtypes.{ID, cuid}
import io.github.dexclaimation.subpub.model.{Cascade, SubIntent}

import scala.collection.mutable
import scala.util.Try

/**
 * SubPub Actor Engine
 *
 * @param context    Actor Context
 * @param bufferSize Buffer size for the Source.
 */
class SubEngine(
  override val context: ActorContext[SubIntent],
  bufferSize: Int = 100,
) extends AbstractBehavior[SubIntent](context) {

  implicit private val mat: Materializer = createMaterializer(context)

  /** Subscriber Collection */
  private val subscribers = mutable.Map
    .empty[ID, Cascade]

  /** Topics Index Collection */
  private val topics = mutable.Map
    .empty[String, Seq[ID]]

  /**
   * On Message Handler
   *
   * @param msg Incoming message from the mailbox
   */
  def onMessage(msg: SubIntent): Behavior[SubIntent] = receive(msg) {
    case SubIntent.Fetch(topic, rep) => safe {
      val id = cuid()
      val sub = Cascade(bufferSize)
      val source = sub.source(pipeToSelf(id))

      rep ! source

      subscribers.update(id, sub)
      topics.update(topic,
        topics.getOrElse(topic, Seq.empty) :+ id
      )
    }

    case SubIntent.Publish(topic, payload) => safe {
      topics.includes(topic, subscribers)
        .foreach(_.next(payload))
    }

    case SubIntent.AcidPill(topic) => safe {
      topics.includes(topic, subscribers)
        .foreach(_.shutdown())
      topics.remove(topic)
    }

    case SubIntent.Reinitialize(topic) => safe {
      topics.includes(topic, subscribers)
        .foreach(_.shutdown())
    }

    case SubIntent.Timeout(id) => safe {
      subscribers.get(id).foreach { sub =>
        sub.shutdown()
        subscribers.remove(id)
      }
    }
  }

  /** Handle receive and return self */
  private def receive(msg: SubIntent)(effect: SubIntent => Unit): Behavior[SubIntent] = {
    effect(msg)
    this
  }

  /** Safely executor a code block and ignore exception */
  private def safe(fallible: => Unit): Unit = Try(fallible)


  /** End a subscriber */
  private def pipeToSelf(id: ID): Sink[Any, NotUsed] = Sink.onComplete { _ =>
    context.self ! SubIntent.Timeout(id)
  }
}

object SubEngine {
  /** Create a Actor Behavior for SubEngine */
  def behavior(bufferSize: Int = 100): Behavior[SubIntent] =
    Behaviors.setup(new SubEngine(_, bufferSize))

}