package com.verily.server

import akka.actor.{ Actor, ActorLogging, Props }

// TODO: WesObject - is supposed to be an arbitrarily structured object. I'm replacing it with String for now, until
// I learn how to deal with open-ended JSON in one of these structures.

final case class WorkflowDescription(
  workflow_id: String,
  state: WorkflowState
)

final case class WorkflowListResponse(workflows: Seq[WorkflowDescription])

final case class WorkflowLogEntry(
  name: String,
  cmd: Seq[String],
  start_time: String,
  end_time: String,
  stdout: String,
  stderr: String,
  exit_code: Int
)

final case class WorkflowRequest(
  workflow_descriptor: String, // this is the CWL or WDL document or base64 encded gzip??
  workflow_params: String /* WesObject */ , // workflow parameterization document
  workflow_type: String, // "CWL" or "WDL" or other
  workflow_type_version: String,
  tags: String, // TODO: type: object -> key value map of arbitrary metadata to tag the workflow. What are the valid types of tag keys? tag values?
  workflow_engine_parameters: String, // TODO: type: object -> optional parameters for the workflow engine - format vague/not-specified
  workflow_url: String
)

final case class WorkflowLog(
  workflow_id: String,
  request: WorkflowRequest,
  state: WorkflowState,
  workflow_log: WorkflowLogEntry,
  task_logs: Seq[WorkflowLogEntry],
  outputs: String /* WesObject */ )

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

  def receive: Receive = {
    case GetWorkflows =>
      sender() ! Transmogriphy.getWorkflows()
    case PostWorkflow(workflowRequest) =>
      sender() ! Transmogriphy.postWorkflow(workflowRequest)
    case GetWorkflow(workflowId) =>
      sender() ! Transmogriphy.getWorkflow(workflowId)
    case DeleteWorkflow(workflowId) =>
      sender() ! Transmogriphy.deleteWorkflow(workflowId)
    case GetWorkflowStatus(workflowId) =>
      sender() ! Transmogriphy.getWorkflowStatus(workflowId)
  }
}
