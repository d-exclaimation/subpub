<p align="center">
<img src="./icon.png" width="175" alt="logo" style="margin: 1rem"/>
</p>
<p align="center"> <h1>SubPub</h1></p>


A lightweight Akka Stream PubSub engine for distributing data to multiple consumers.

## Setup

**Latest Version**: `0.1.2`

```sbt
"io.github.d-exclaimation" % "subpub" % latestVersion
```

## Feature

SubPub main goals are to:

- Handle creation, distribution, management of both the outgoing stream of data and incoming published data.
- Differentiate streams based on topic, which can used to push the proper data into the proper streams.
- Also handles creation in a lazy and concurrent safe way, no need to worry about race conditions.
- All of the above on lightweight code embedded implementation.

### Consideration

For more complex implementation i.e. connecting to a PubSub / Distributed streaming platform like Kafka. Consider using [Alpakka](https://doc.akka.io/docs/alpakka/current/index.html) instead.

## Examples

<details>
<summary><b>REST + Websocket Realtime API</b></summary>

An example using this for HTTP + Websocket Realtime API

```scala
import io.github.dexclaimation.subpub.SubPub

object Main extends SprayJsonSupport {
  // ...

  val pubsub = new SubPub()

  val route: Route = {
    (path("send" / Segment) & post & entity(as[JsValue])) { path =>
      entity(as[JsValue]) { 
        case JsObject(body) => sendMessage(path, body)
        case _ => complete(BadRequest -> JsString("Bad message"))
      }
    } ~ path("websocket" / Segment) { path =>
      handleWebSocketMessages(websocketMessage(path))
    }
  }

  // Handle HTTP Post and emit to websocket
  def sendMessage(path: String, body: Map[String, JsValue]): Route = {
    try {
      val content = body("content")
      val name = body("name")
      val msg = JsObject(
        "content" -> content,
        "name" -> name,
        "createdAt" -> JsString(Instant.now().toString) 
      )
      // Push message to subpub
      pubsub.publish(s"chat::$path", msg)
      complete(OK -> msg)
    } catch {
      case NonFatal(_) => 
        complete(BadRequest -> "Bad message")
    }
  }

  // Handle Websocket Flow using the topic based Source
  def websocketMessage(path: String): Flow[Message, TextMessage.Strict, _] = {
    val source = pubsub
      .source[JsValue](s"chat::$path")
      .map(_.compactPrint) 
      .map(TextMessage.Strict)

    val sink = Flow[Message]
      .map(_ => ()) // On Websocket Message
      .to(Sink.onComplete(_ => ())) // on Websocket End

    Flow.fromSinkAndSource(sink, source)
  }

  // ...
}
```
</details>
<details>
<summary><b>Realtime GraphQL API</b></summary>

Using with a Realtime GraphQL API with Subscription using [Sangria](https://sangria-graphql.github.io/)
and [OverLayer](https://overlayer.netlify.app).

```scala
import io.github.dexclaimation.subpub.SubPub

object Main extends SprayJsonSupport {
  // ...

  val MessageType = ???
  val (roomArg, stringArg, nameArg) = ???

  val QueryType = ???
  val MutationType = ObjectType(
    "Mutation",
    fields[SubPub, Unit](
      // GraphQL Mutation to send message
      Field("send", MessageType,
        arguments = roomArg :: stringArg :: nameArg :: Nil,
        resolve = { c =>
          val msg = Message(c arg stringArg, c arg nameArg, Instant.now().toString)
          // Publish data into subscription
          c.ctx.publish[Message](c arg roomArg, msg)
          msg
        }
      )
    )
  )

  val SubscriptionType = ObjectType(
    "Subscription",
    field[SubPub, Unit](
      // GraphQL Subscription to get realtime data stream
      Field.subs("room", MessageType,
        arguments = roomArg :: Nil,
        // Use the Source from SubPub and map it to Action for Sangria
        resolve = c => c.ctx.source[Message](c arg roomArg).map(Action(_))
      )
    )
  )
  val schema = Schema(QueryType, Some(MutationType), Some(SubscriptionType))

  // OverLayer for handling GraphQL over Websocket
  val layer = OverTransportLayer(schema, ())
  val pubsub = new SubPub()

  val route: Route = {
    (post & path("graphql") & entity(as[JsValue])) { req =>
      graphQLEndpoint(req, pubsub)
    } ~ path("graphql" / "websocket") {
      layer.ws(pubsub)
    }
  }

  // Handle GraphQL over HTTP
  private def graphQLEndpoint(requestJson: JsValue, context: SubPub) = ???

  // ...
}
```

</details>

## Feedback

If you have any feedback, please reach out to me through the issues tab or
Twitter [@d_exclaimation](https://twitter.com/d_exclaimation)
