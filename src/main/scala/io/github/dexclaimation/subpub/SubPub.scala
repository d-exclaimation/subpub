//
//  SubPub.scala
//  over-layer
//
//  Created by d-exclaimation on 10:16 AM.
//

package io.github.dexclaimation.subpub

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Props, SpawnProtocol}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.{Done, NotUsed}
import io.github.dexclaimation.subpub.model.SubIntent

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

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
   * @param topic    The topic to be ended
   * @param forceFul Kill forcefully or complete it.
   */
  def close(topic: String): Unit =
    engine ! SubIntent.AcidPill(topic)
}
