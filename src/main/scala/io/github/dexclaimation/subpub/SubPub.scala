//
//  SubPub.scala
//  over-layer
//
//  Created by d-exclaimation on 10:16 AM.
//

package io.github.dexclaimation.subpub

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Props, SpawnProtocol}
import akka.stream.scaladsl.{Concat, Source}
import akka.util.Timeout
import akka.{Done, NotUsed}
import io.github.dexclaimation.subpub.model.SubIntent

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

/**
 * Pub/Sub system for topic based distributed streams
 *
 * @param bufferSize    Buffer size for the upstream producer.
 * @param idleDuration  The amount of time used to wait for the SubPub to set up.
 * @param awaitDuration The amount of time to wait for Source.
 */
class SubPub(
  bufferSize: Int = 100,
  idleDuration: FiniteDuration = 3.seconds,
  awaitDuration: FiniteDuration = 5.seconds,
)(implicit system: ActorSystem[SpawnProtocol.Command]) {

  // --- Implicit ---
  implicit private val keepAlive: Timeout = Timeout(idleDuration)
  implicit private val ex: ExecutionContext = system.executionContext

  /**
   * Runtime checker to check for faulty type being pushed.
   *
   * @tparam T The Type to be checked with an implicit class tag.
   * @return Boolean
   */
  private def runtimeChecker[T: ClassTag]: Any => Boolean = {
    case _: T => true
    case _ => false
  }

  /** SubPub Engine */
  private val engine = {
    val spawn = (rep: ActorRef[ActorRef[SubIntent]]) => SpawnProtocol.Spawn(
      behavior = SubEngine.behavior(bufferSize),
      name = "SubPubEngine",
      props = Props.empty,
      replyTo = rep
    )
    Await.result(system.ask(spawn), awaitDuration)
  }

  /**
   * Asynchronously request for an existing topic stream or create a new one.
   *
   * @param topic The topic used to differentiate streams.
   * @tparam T The type for the Source.
   * @return Future of the Source with the type of T.
   */
  def asyncSource[T: ClassTag](topic: String): Future[Source[T, NotUsed]] = engine
    .ask(SubIntent.Fetch(topic, _))
    .map(s => s.filter(runtimeChecker[T]).map(_.asInstanceOf[T]))

  /**
   * Synchronously request for an existing topic stream or create a new one.
   *
   * @param topic The topic used to differentiate streams.
   * @tparam T The type for the Source
   * @return Source of type T.
   */
  def source[T: ClassTag](topic: String): Source[T, NotUsed] = Await
    .result(asyncSource[T](topic), awaitDuration)

  /**
   * Synchronously request for an existing topic stream or create a new one.
   *
   * @param topic     The topic used to differentiate streams.
   * @param initValue The initial value pushed into the stream.
   * @tparam T The type for the Source
   * @return Source of type T.
   */
  def source[T: ClassTag](topic: String, initValue: T): Source[T, NotUsed] =
    combineSource(source[T](topic),
      init = Some(Source.single(initValue)),
      end = None
    )

  /**
   * Synchronously request for an existing topic stream or create a new one.
   *
   * @param topic    The topic used to differentiate streams.
   * @param endValue The ending value pushed into the stream.
   * @tparam T The type for the Source
   * @return Source of type T.
   */
  def source[T: ClassTag](topic: String, endValue: => T): Source[T, NotUsed] =
    combineSource(source[T](topic),
      init = None,
      end = Some(Source.single(endValue))
    )

  /**
   * Synchronously request for an existing topic stream or create a new one.
   *
   * @param topic     The topic used to differentiate streams.
   * @param initValue The initial value pushed into the stream.
   * @param endValue  The ending value pushed into the stream.
   * @tparam T The type for the Source
   * @return Source of type T.
   */
  def source[T: ClassTag](topic: String, initValue: T, endValue: T): Source[T, NotUsed] =
    combineSource(source[T](topic),
      init = Some(Source.single(initValue)),
      end = Some(Source.single(endValue))
    )

  /**
   * Combine source with an initial and/or an ending one
   *
   * @param stream Core downstream.
   * @param init   Initial downstream values.
   * @param end    Ending downstream values.
   * @return Combined source that push the initial (if exist) first, then core, then ending (if exist).
   */
  private def combineSource[T: ClassTag](
    stream: Source[T, NotUsed],
    init: Option[Source[T, NotUsed]],
    end: Option[Source[T, NotUsed]]
  ): Source[T, NotUsed] = (init, end) match {
    case (Some(stream1), Some(stream2)) =>
      Source.combine(stream1, stream, stream2)(Concat(_))
    case (Some(stream1), None) =>
      Source.combine(stream1, stream)(Concat(_))
    case (None, Some(stream2)) =>
      Source.combine(stream, stream2)(Concat(_))
    case _ => stream
  }

  /**
   * Subscribe for a specific topic.
   *
   * @param topic      The topic for what to subscribe to.
   * @param onSnapshot Action perform on data.
   * @return Future of Done, notifying when stream is over.
   */
  def subscribe[T: ClassTag](topic: String)(onSnapshot: T => Unit): Future[Done] =
    source[T](topic)
      .runForeach(onSnapshot)

  /**
   * Publish a data to a specific topic.
   *
   * ''Invalid data format will be omitted / ignored''
   *
   * @param topic   The topic used to find the proper stream.
   * @param payload The data payload.
   */
  def publish[T](topic: String, payload: T): Unit =
    engine ! SubIntent.Publish(topic, payload)

  /**
   * Close a topic source.
   *
   * @param topic The topic to be ended
   */
  def close(topic: String): Unit =
    engine ! SubIntent.AcidPill(topic)
}

object SubPub {

  /**
   * Pub/Sub system for topic based distributed streams
   *
   * @param bufferSize    Buffer size for the upstream producer.
   * @param idleDuration  The amount of time used to wait for the SubPub to set up.
   * @param awaitDuration The amount of time to wait for Source.
   */
  def apply(
    bufferSize: Int = 100,
    idleDuration: FiniteDuration = 3.seconds,
    awaitDuration: FiniteDuration = 5.seconds,
  )(implicit system: ActorSystem[SpawnProtocol.Command]): SubPub = new SubPub(bufferSize, idleDuration, awaitDuration)
}