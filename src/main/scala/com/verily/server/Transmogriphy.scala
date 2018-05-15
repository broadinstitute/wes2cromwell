package com.verily.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object Transmogriphy {

  val cromwellPath = "http://localhost:8000/api/workflows/v1"

  def getWorkflows(): WorkflowListResponse = {
    WorkflowListResponse(
      Seq(
        WorkflowDescription("wfid111", WorkflowState.INITIALIZING),
        WorkflowDescription("wfid222", WorkflowState.RUNNING)
      )
    )
  }

  def postWorkflow(workflowRequest: WorkflowRequest): HttpResponse = {
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

    params.workflowOptions match {
      case Some(x) => parts = BodyPart("workflowOptions", makeJsonEntity(x)) :: parts
    }

    params.dependenciesZip() match {
      case Some(x) => parts = BodyPart("workflowOptions", makeTextEntity(x)) :: parts
    }

    val onHold: String = if (params.workflowOnHold.getOrElse(false)) "true" else "false"
    parts = BodyPart("workflowOnHold", makeTextEntity(onHold)) :: parts

    val formData = FormData(Source(parts))
    val request = HttpRequest(method = HttpMethods.POST, uri = cromwellPath, entity = formData.toEntity)
    val responseFuture = Http().singleRequest(request)

    responseFuture.onComplete {
      case Success(response) => {
        response.status match {
          case StatusCodes.Created => {
            Unmarshal(response.entity).to[CromwellPostResponse].onComplete {
              case Success(cromwellPostResponse) => {
                WesResponseWorkflowId(cromwellPostResponse.id)
              }
              case Failure(_) =>
                WesResponseError("Failed to parse Cromwell response", StatusCodes.InternalServerError.intValue)
            }
          }
          case StatusCodes.BadRequest =>
            WesResponseError("The request is malformed", response.status.intValue())

          case StatusCodes.InternalServerError =>
            WesResponseError("Cromwell server error", response.status.intValue())
        }
      }
      case Failure(_) =>
        WesResponseError("Http request error", StatusCodes.InternalServerError.intValue)
    }
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
