//
//  ModelTest.scala
//  subpub
//
//  Created by d-exclaimation on 7:41 PM.
//

package io.github.dexclaimation.subpub

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{KillSwitches, Materializer, OverflowStrategy}
import io.github.dexclaimation.subpub.model.Cascade
import io.github.dexclaimation.subpub.utils.FlatKeep
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ModelTest extends AnyWordSpec with Matchers {

  val base = ActorTestKit()

  implicit val materializer: Materializer = Materializer.createMaterializer(base.system)

  "FlatKeep" when {
    "given tuple on one side" should {
      "return a flatten tuple given a right tuple" in {
        val (l, r) = (1, (2, 3))
        FlatKeep.bothR(l, r) match {
          case (1, 2, 3) => succeed
          case _ => fail("Order is being tempered")
        }
      }

      "return a flatten tuple given a left tuple" in {
        val (l, r) = ((1, 2), 3)
        FlatKeep.bothL(l, r) match {
          case (1, 2, 3) => succeed
          case _ => fail("Order is being tempered")
        }
      }

      "return a flatten tuple given tuple on both side" in {
        val (l, r) = ((1, 2), (3, 4))
        FlatKeep.both(l, r) match {
          case (1, 2, 3, 4) => succeed
          case _ => fail("Order is being tempered")
        }
      }
    }
  }

  "Cascade" when {
    "directly constructed" should {
      val (ref, kill, publisher) = ActorSource
        .actorRef[Any](
          completionMatcher = PartialFunction.empty,
          failureMatcher = PartialFunction.empty,
          bufferSize = 256,
          overflowStrategy = OverflowStrategy.dropHead
        )
        .viaMat(KillSwitches.single)(Keep.both)
        .toMat(Sink.asPublisher(true))(FlatKeep.bothL)
        .run()

      val source = Source
        .fromPublisher(publisher)

      val controller = new Cascade(ref, source, kill)

      "be able to push data into the source" in {
        controller.next("Hello World!!")
      }

      "be able to be shutdowned" in {
        val fut = controller.stream.runWith(Sink.seq)
        controller.next("Hello World!!")
        controller.shutdown()
        val "Hello World!!" +: _ = Await.result(fut, 1.second)
      }
    }

    "applied" should {
      "return a functioning broadcast stream" in {
        val cas1 = Cascade(256, onComplete = Sink.ignore)

        cas1.stream match {
          case _: Source[Any, NotUsed] => ()
          case _ => fail("Wrong type")
        }

        val fut = cas1.stream.runWith(Sink.seq)
        cas1.next(1)
        cas1.next(2)
        cas1.next(3)
        Thread.sleep(25)
        cas1.shutdown()
        val Seq(1, 2, 3) = Await.result(fut, 1.seconds)
      }
    }
  }
}
