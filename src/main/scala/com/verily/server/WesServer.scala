package com.verily.server

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{ concat, pathPrefix }
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

// MAIN
object WesServer extends App with WorkflowRoutes {
  val port = 9090

  // set up ActorSystem and other dependencies here
  implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val workflowActor: ActorRef = system.actorOf(WorkflowActor.props, "workflowRegistryActor")

  // from the UserRoutes trait
  val routes: Route = workflowRoutes

  Http().bindAndHandle(routes, "localhost", port)

  println(s"Server online at http://localhost:${port}/")

  Await.result(system.whenTerminated, Duration.Inf)
}
