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
  bufferSize: Int,
) extends AbstractBehavior[SubIntent](context) {

  implicit private val mat: Materializer = createMaterializer(context)

  /** Subscriber Collection */
  private val subscribers = mutable.Map
    .empty[String, Cascade]

  /**
   * On Message Handler
   *
   * @param msg Incoming message from the mailbox
   */
  def onMessage(msg: SubIntent): Behavior[SubIntent] = receive(msg) {
    case SubIntent.Fetch(topic, rep) => safe {
      val sub = subscribers.getOrElse(topic,
        Cascade(bufferSize, onComplete = pipeToSelf(topic))
      )
      val source = sub.stream

      rep ! source

      subscribers.update(topic, sub)
    }

    case SubIntent.Publish(topic, payload) => safe {
      subscribers.get(topic).foreach(_ ! payload)
    }

    case SubIntent.AcidPill(topic) => safe {
      subscribers.get(topic).foreach { cas =>
        cas.shutdown()
        subscribers.remove(topic)
      }
    }

    case SubIntent.Timeout(topic) => safe {
      if (subscribers.contains(topic)) {
        subscribers.remove(topic)
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
  private def pipeToSelf(topic: String): Sink[Any, NotUsed] = Sink.onComplete { _ =>
    context.self ! SubIntent.Timeout(topic)
  }
}

object SubEngine {
  /** Create a Actor Behavior for SubEngine */
  def behavior(bufferSize: Int = 256): Behavior[SubIntent] =
    Behaviors.setup(new SubEngine(_, bufferSize))

}