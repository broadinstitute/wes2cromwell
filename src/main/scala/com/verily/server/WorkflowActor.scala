package com.verily.server

import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest }
import spray.json.JsObject

import scala.concurrent.ExecutionContext.Implicits.global

final case class WorkflowRequest(
  workflow_descriptor: Option[String],
  workflow_params: Option[JsObject],
  workflow_type: String,
  workflow_type_version: String,
  tags: Option[JsObject],
  workflow_engine_parameters: Option[JsObject],
  workflow_url: Option[String]
)

final case class WorkflowDescription(
  workflow_id: String,
  state: WorkflowState
)

final case class WorkflowTypeVersion(workflow_type_version: Seq[String])

final case class ErrorResponse(
  msg: String,
  status_code: Int
)

object WorkflowActor {
  final case class GetWorkflows(authHeader: HttpHeader)
  final case class PostWorkflow(workflowRequest: WorkflowRequest, authHeader: HttpHeader)
  final case class GetWorkflow(workflowId: String, authHeader: HttpHeader)
  final case class DeleteWorkflow(workflowId: String, authHeader: HttpHeader)
  final case class GetWorkflowStatus(workflowId: String, authHeader: HttpHeader)

  def props: Props = Props[WorkflowActor]
}

class WorkflowActor extends Actor with ActorLogging {
  import WorkflowActor._
  lazy val transmogriphy = new Transmogriphy()(context.system, global)

  def receive: Receive = {
    case GetWorkflows(authHeader) =>
      transmogriphy.getWorkflows(sender(), authHeader)
    case PostWorkflow(workflowRequest, authHeader) =>
      transmogriphy.postWorkflow(sender(), workflowRequest, authHeader)
    case GetWorkflow(workflowId, authHeader) =>
      transmogriphy.getWorkflow(sender(), workflowId, authHeader)
    case DeleteWorkflow(workflowId, authHeader) =>
      transmogriphy.deleteWorkflow(sender(), workflowId, authHeader)
    case GetWorkflowStatus(workflowId, authHeader) =>
      transmogriphy.getWorkflowStatus(sender(), workflowId, authHeader)
  }
}
