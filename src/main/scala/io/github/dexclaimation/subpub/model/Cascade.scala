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

  /** Source with a wireTap-like Sink */
  def source(
    that: Graph[SinkShape[Any], _]
  ): Source[Any, NotUsed] = source.alsoTo(that)
}

object Cascade {
  def apply(bufferSize: Int)(implicit mat: Materializer): Cascade = {
    val (actorRef, killSwitch, publisher) = ActorSource
      .actorRef[Any](
        completionMatcher = PartialFunction.empty,
        failureMatcher = PartialFunction.empty,
        bufferSize = bufferSize,
        overflowStrategy = OverflowStrategy.dropHead
      )
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(Sink.asPublisher(true))(flatKeep)
      .run()

    val source = Source.fromPublisher(publisher)

    new Cascade(actorRef, source, killSwitch)
  }

  private def flatKeep[R1, R2, L]: ((R1, R2), L) => (R1, R2, L) = (r, l) => (r._1, r._2, l)
}
