//
//  Emitter.scala
//  over-layer
//
//  Created by d-exclaimation on 10:53 AM.
//


package io.github.dexclaimation.subpub.model

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import io.github.dexclaimation.subpub.implicits.SourceMiddleware
import io.github.dexclaimation.subpub.utils.FlatKeep

/**
 * Wrapper Object for ActorRef & Source
 *
 * @param actorRef ActorRef for pushing data into the source.
 * @param source   Source for data from the ActorRef.
 */
case class Cascade(
  private val actorRef: ActorRef[Any],
  private val source: Source[Any, NotUsed],
  private val killSwitch: KillSwitch,
) {
  /** Emit data to Actor Ref */
  def next(msg: Any): Unit = actorRef.tell(msg)

  /** Operator for emit */
  def !(msg: Any): Unit = {
    actorRef ! msg
  }

  /** Stop the stream */
  def shutdown(): Unit = killSwitch.shutdown()

  /** Source with a stoppable consumer source */
  def stream: Source[Any, NotUsed] = source
}

object Cascade {
  def apply(bufferSize: Int, onComplete: Graph[SinkShape[Any], _])(implicit mat: Materializer): Cascade = {
    val (actorRef, killSwitch, publisher) = ActorSource
      .actorRef[Any](
        completionMatcher = PartialFunction.empty,
        failureMatcher = PartialFunction.empty,
        bufferSize = bufferSize,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(Sink.asPublisher(true))(FlatKeep.bothL)
      .run()

    val source = Source
      .fromPublisher(publisher)
      .toBroadcastHub(bufferSize)
      .alsoTo(onComplete)

    new Cascade(actorRef, source, killSwitch)
  }
}
