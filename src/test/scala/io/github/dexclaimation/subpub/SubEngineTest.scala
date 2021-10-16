//
//  SubEngineTest.scala
//  subpub
//
//  Created by d-exclaimation on 9:19 PM.
//

package io.github.dexclaimation.subpub

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import io.github.dexclaimation.subpub.model.SubIntent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class SubEngineTest extends AnyWordSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  val base = ActorTestKit()

  implicit val system: ActorSystem[Nothing] = base.system
  implicit val ex: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(20.seconds)

  var engine = base.spawn(SubEngine.behavior())

  "SubEngine" when {

    "given Fetch intent" should {
      "return a proper source" in {
        val res = Await.result(
          engine.ask[Source[Any, NotUsed]](
            SubIntent.Fetch("test-topic", _)
          ),
          timeout.duration
        )

        res match {
          case _: Source[Any, NotUsed] => ()
        }

        engine.tell(SubIntent.AcidPill("test-topic"))
      }

      "be able to push to the sources" in {
        def get() = Await.result(
          engine.ask[Source[Any, NotUsed]](
            SubIntent.Fetch("test-topic", _)
          ),
          timeout.duration
        )

        val res0 = get().runWith(Sink.seq)
        val res1 = get().runWith(Sink.seq)

        engine.tell(SubIntent.Publish("test-topic", "Hello"))
        Thread.sleep(25)
        engine.tell(SubIntent.AcidPill("test-topic"))

        engine.tell(SubIntent.Publish("test-topic", "Not received"))
        val combined = Future.reduceLeft(res0 :: res1 :: Nil)(_ ++ _)
        Await.result(combined, 10.seconds) match {
          case Seq("Hello", "Hello") => succeed
          case x => fail(s"Does not receive proper message, instead got ($x)")
        }
      }
    }
  }

  override def beforeEach(): Unit = {
    engine = base.spawn(SubEngine.behavior())
  }

  override def afterEach(): Unit = {
    base.stop(engine)
  }

  override def afterAll(): Unit = base.shutdownTestKit()
}
