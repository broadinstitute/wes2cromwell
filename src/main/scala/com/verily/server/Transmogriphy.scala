package com.verily.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Transmogriphy(implicit system: ActorSystem, ec: ExecutionContext) extends JsonSupport {

  val cromwellPath = "http://localhost:8000/api/workflows/v1"

  def getWorkflows(): WorkflowListResponse = {
    WorkflowListResponse(
      Seq(
        WorkflowDescription("wfid111", WorkflowState.INITIALIZING),
        WorkflowDescription("wfid222", WorkflowState.RUNNING)
      )
    )
  }

  def postWorkflow(workflowRequest: WorkflowRequest): WesResponse = {
    /*
     * See https://docs.google.com/document/d/11_qHPBbEg3Hr4Vs3lh3dvU0dLl1I2zt6rmNxEkW1U1U/edit#
     * for details on the workflow request mapping.
     */
    val params = WorkflowParams.toWorkflowParams(workflowRequest.workflow_params)

    // Build the list of parts
    // TODO: would it be better to use a mutable list instead of re-creating each time?
    var parts = List(
      BodyPart("workflowType", makeTextEntity(workflowRequest.workflow_type)),
      BodyPart("workflowTypeVersion", makeTextEntity(workflowRequest.workflow_type_version)),
      BodyPart("workflowSource", makeJsonEntity(workflowRequest.workflow_descriptor))
    )

    if (params.workflowOptions.isDefined) {
      parts = BodyPart("workflowOptions", makeJsonEntity(params.workflowOptions.get)) :: parts
    }

    if (params.workflowDependencies.isDefined) {
      parts = BodyPart("workflowOptions", makeTextEntity(params.dependenciesZip().get)) :: parts
    }

    val onHold: String = if (params.workflowOnHold.getOrElse(false)) "true" else "false"
    parts = BodyPart("workflowOnHold", makeTextEntity(onHold)) :: parts

    val formData = FormData(Source(parts))
    val request = HttpRequest(method = HttpMethods.POST, uri = cromwellPath, entity = formData.toEntity)
    val responseFuture = Http().singleRequest(request)

    // TODO: this is ugly, but onComplete returns Unit, so brute force way to get the right result out
    var wesResponse: WesResponse = WesResponseError("Http request error", StatusCodes.InternalServerError.intValue)
    responseFuture.onComplete {
      case Success(response) => {
        response.status match {
          case StatusCodes.Created => {
            val json: String = response.entity.toString
            val cromwellPostResponse: CromwellPostResponse = CromwellPostResponse.toCromwellPostResponse(json)
            wesResponse = WesResponseWorkflowId(cromwellPostResponse.id)
          }

          case StatusCodes.BadRequest =>
            wesResponse = WesResponseError("The request is malformed", response.status.intValue())

          case StatusCodes.InternalServerError =>
            wesResponse = WesResponseError("Cromwell server error", response.status.intValue())

          case _ =>
            wesResponse = WesResponseError("Unexpected response status", response.status.intValue())
        }
      }
      case Failure(_) =>
    }
    wesResponse
  }

  def makeJsonEntity(content: String): HttpEntity.Default = {
    val bytes = ByteString(content)
    HttpEntity.Default(ContentTypes.`application/json`, bytes.length, Source.single(bytes))
  }

  def makeTextEntity(content: String): HttpEntity.Default = {
    val bytes = ByteString(content)
    HttpEntity.Default(ContentTypes.`text/plain(UTF-8)`, bytes.length, Source.single(bytes))
  }

  def getWorkflow(workflowId: String): Option[WorkflowLog] = {
    Some(WorkflowLog(
      workflowId,
      WorkflowRequest(
        "Say hello family",
        "params",
        "WDL",
        "1.2.3",
        "tags are keys and values in some format",
        "engine params",
        "url"
      ),
      WorkflowState.EXECUTOR_ERROR,
      WorkflowLogEntry("Workflow Family", Seq("hello world"), "start", "end", "stdout", "stderr", 99),
      Seq(
        WorkflowLogEntry("Joe", Seq("hello Joe"), "start", "end", "stdout", "stderr", 98),
        WorkflowLogEntry("Jone", Seq("hello Jane"), "start", "end", "stdout", "stderr", 97)
      ),
      "outputs"
    ))
  }

  def getWorkflowStatus(workflowId: String): Option[WorkflowDescription] = {
    Some(WorkflowDescription(workflowId, WorkflowState.SYSTEM_ERROR))
  }

  def deleteWorkflow(workflowId: String): String = {
    "wfid444"
  }
}
