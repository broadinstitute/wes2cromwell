package com.verily.server

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.{ delete, get, post }
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.util.Timeout
import com.verily.server.WorkflowActor._

import scala.concurrent.Future
import scala.concurrent.duration._

// WorkflowRoutes implements the 'workflows' endpoint in WES
trait WorkflowRoutes extends JsonSupport {
  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[WorkflowRoutes])

  // other dependencies that UserRoutes use
  def workflowActor: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  lazy val workflowRoutes: Route =
    // TODO: factor the top of this into a path prefix in WesServer
    pathPrefix("ga4gh" / "wes" / "v1" / "workflows") {
      concat(
        pathEnd {
          concat(
            get {
              val workflowList: Future[WorkflowListResponse] =
                (workflowActor ? GetWorkflows).mapTo[WorkflowListResponse]
              complete(workflowList)
            },
            post {
              entity(as[WorkflowRequest]) { workflowRequest =>
                val requested: Future[String] =
                  (workflowActor ? PostWorkflow(workflowRequest)).mapTo[String]
                complete(requested)
              }
            }
          )
        },
        // workflows/{workflow_id}
        path(Segment) { workflowId =>
          concat(
            get {
              val maybeWorkflow: Future[Option[WorkflowLog]] =
                (workflowActor ? GetWorkflow(workflowId)).mapTo[Option[WorkflowLog]]
              rejectEmptyResponse {
                complete(maybeWorkflow)
              }
            },
            delete {
              val workspaceDeleted: Future[String] =
                (workflowActor ? DeleteWorkflow(workflowId)).mapTo[String]
              complete(workspaceDeleted)
            }
          )
        },
        // workflows/{workflow_id}/status
        path(Segment / "status") { workflowId =>
          get {
            val maybeUser: Future[Option[WorkflowDescription]] =
              (workflowActor ? GetWorkflowStatus(workflowId)).mapTo[Option[WorkflowDescription]]
            rejectEmptyResponse {
              complete(maybeUser)
            }
          }
        }
      )
    }
}
