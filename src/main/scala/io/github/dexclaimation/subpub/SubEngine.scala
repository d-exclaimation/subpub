//
//  SubPubEngine.scala
//  over-layer
//
//  Created by d-exclaimation on 10:16 AM.
//

package io.github.dexclaimation.subpub


import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.stream.Materializer.createMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{Materializer, OverflowStrategy}
import io.github.dexclaimation.subpub.model.{Emitter, SubIntent}

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
  bufferSize: Int = 100
) extends AbstractBehavior[SubIntent](context) {

  implicit private val mat: Materializer = createMaterializer(context)

  /** Topic -> Emitter */
  private val eq = mutable.Map.empty[
    String, Emitter
  ]

  /**
   * On Message Handler
   *
   * @param msg Incoming message from the mailbox
   */
  def onMessage(msg: SubIntent): Behavior[SubIntent] = receive(msg) {
    case SubIntent.Fetch(topic, rep) => safe {
      val emitter = eq.getOrElse(topic, createSource())
      eq.update(topic, emitter)
      rep.!(emitter.source)
    }

    case SubIntent.Publish(topic, payload) => safe {
      eq.get(topic)
        .foreach(_ ! payload)
    }

    case SubIntent.AcidPill(topic) => safe {
      eq.get(topic)
        .foreach(_ ! null)
      eq.remove(topic)
    }
  }

  /** Handle receive and return self */
  private def receive(msg: SubIntent)(effect: SubIntent => Unit): Behavior[SubIntent] = {
    effect(msg)
    this
  }

  /** Safely executor a code block and ignore exception */
  private def safe(fallible: => Unit): Unit = Try(fallible)

  /** Fallible Matcher */
  private val nullable: PartialFunction[Any, Throwable] = {
    case null => new Error("Cannot send in null")
  }

  /** Completion Matcher */
  private val completion: PartialFunction[Any, Unit] = {
    case null => ()
  }

  /** Create a source for a topic */
  private def createSource(): Emitter = {
    val (actorRef, publisher) = ActorSource
      .actorRef[Any](
        completionMatcher = completion,
        failureMatcher = nullable,
        bufferSize = bufferSize,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .toMat(Sink.asPublisher(true))(Keep.both)
      .run()

    val source = Source.fromPublisher(publisher)

    Emitter(actorRef, source)
  }
}

object SubEngine {
  /** Create a Actor Behavior for SubEngine */
  def behavior(bufferSize: Int = 100): Behavior[SubIntent] =
    Behaviors.setup(new SubEngine(_, bufferSize))
}