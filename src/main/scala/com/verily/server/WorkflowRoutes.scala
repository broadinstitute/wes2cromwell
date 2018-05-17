package com.verily.server

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, StandardRoute }
import akka.http.scaladsl.server.directives.MethodDirectives.{ delete, get, post }
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.util.Timeout
import com.verily.server.WorkflowActor._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

// WorkflowRoutes implements the 'workflows' endpoint in WES
trait WorkflowRoutes extends JsonSupport {
  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[WorkflowRoutes])

  // other dependencies that Routes use
  def workflowActor: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(15.seconds) // usually we'd obtain the timeout from the system's configuration

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
                implicit lazy val timeout = Timeout(5.minutes)
                val futureWes: Future[Any] = workflowActor.ask(PostWorkflow(workflowRequest))
                handleWesResponse(futureWes)
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
            val futureWes: Future[Any] = workflowActor.ask(GetWorkflowStatus(workflowId))
            handleWesResponse(futureWes)
          }
        }
      )
    }

  // Common handler for some Wes Responses
  def handleWesResponse(futureWes: Future[Any]) = {
    onComplete(futureWes.mapTo[WesResponse]) {
      case Success(wesResponse) => {
        wesResponse match {
          case WesResponseWorkflowId(workflow_id) =>
            complete(StatusCodes.Created, WesResponseWorkflowId(workflow_id))
          case WesResponseStatus(workflow_id, state) =>
            complete(StatusCodes.OK, WesResponseStatus(workflow_id, state))
          case WesResponseError(msg, status_code) =>
            complete(status_code, WesResponseError(msg, status_code))
        }
      }
      case Failure(ex) => {
        complete(
          StatusCodes.InternalServerError,
          WesResponseError(s"PostWorkflow exception: ${ex.getMessage}", StatusCodes.InternalServerError.intValue)
        )
      }
    }
  }

}
