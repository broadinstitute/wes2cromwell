package com.verily.server

import akka.actor.{Actor, ActorLogging, Props}
import spray.json.JsObject

import scala.concurrent.ExecutionContext.Implicits.global

final case class WorkflowRequest(workflow_descriptor: Option[String], // this is the CWL or WDL document or base64 encoded gzip??
                           workflow_params: Option[JsObject], // workflow parameterization document
                           workflow_type: String, // "CWL" or "WDL" or other
                           workflow_type_version: String,
                           tags: Option[JsObject], // TODO: type: object -> key value map of arbitrary metadata to tag the workflow. What are the valid types of tag keys? tag values?
                           workflow_engine_parameters: Option[JsObject], // TODO: type: object -> optional parameters for the workflow engine - format vague/not-specified
                           workflow_url: Option[String])

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
  final case object GetWorkflows
  final case class PostWorkflow(workflowRequest: WorkflowRequest)
  final case class GetWorkflow(workflowId: String)
  final case class DeleteWorkflow(workflowId: String)
  final case class GetWorkflowStatus(workflowId: String)

  def props: Props = Props[WorkflowActor]
}

class WorkflowActor extends Actor with ActorLogging {
  import WorkflowActor._
  lazy val transmogriphy = new Transmogriphy()(context.system, global)

  def receive: Receive = {
    case GetWorkflows =>
      transmogriphy.getWorkflows(sender())
    case PostWorkflow(workflowRequest) =>
      transmogriphy.postWorkflow(sender(), workflowRequest)
    case GetWorkflow(workflowId) =>
      transmogriphy.getWorkflow(sender(), workflowId)
    case DeleteWorkflow(workflowId) =>
      transmogriphy.deleteWorkflow(sender(), workflowId)
    case GetWorkflowStatus(workflowId) =>
      transmogriphy.getWorkflowStatus(sender(), workflowId)
  }
}
