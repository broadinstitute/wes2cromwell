package com.verily.server

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.Multipart.FormData.BodyPart

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ ActorMaterializer, Materializer }
import com.verily.server.WorkflowState._
import spray.json.{ JsObject, JsonParser }

import scala.collection.immutable

class Transmogriphy(implicit system: ActorSystem, ec: ExecutionContext) {

  val cromwellPath = "http://localhost:8000/api/workflows/v1" // "https://cromwell.caas-dev.broadinstitute.org/api/workflows/v1"

  implicit val materializer: Materializer = ActorMaterializer()

  def getWorkflows(replyTo: ActorRef, authHeader: HttpHeader): Unit = {
    println("IN WORKFLOWACTOR GET WORKFLOW")
    val now = ZonedDateTime.now()
    val oneDayAgoString = now.minusDays(1).format(DateTimeFormatter.ISO_INSTANT).replace(":", "%3A")
    val nowString = now.format(DateTimeFormatter.ISO_INSTANT).replace(":", "%3A")

    val url = cromwellPath + s"/query?start=${oneDayAgoString}&end=${nowString}"
    val request = HttpRequest(method = HttpMethods.GET, uri = url, headers = List(authHeader))
    println("GET WORKFLOWS REQUEST: " + request)
    val responseFuture = Http().singleRequest(request)
    responseFuture.onComplete {
      case Success(response) => {
        println("GET WORKFLOWS RESPONSE: " + response)
        response.status match {
          case StatusCodes.OK => {
            val bodyDataFuture: Future[String] = Unmarshal(response.entity).to[String]

            bodyDataFuture map { bodyData =>
              val statusList: List[WesResponseStatus] = getWorkflowStatusList(bodyData)
              replyTo ! WesResponseWorkflowList(statusList)
            }
          }

          case StatusCodes.BadRequest => replyTo ! WesResponseError("The request is malformed", response.status.intValue())

          case StatusCodes.InternalServerError => replyTo ! WesResponseError("Cromwell server error", response.status.intValue())

          case _ => replyTo ! WesResponseError("Unexpected response status", response.status.intValue())
        }
      }
      case Failure(_) =>
        replyTo ! WesResponseError("Http error", StatusCodes.InternalServerError.intValue)
    }
  }

  def postWorkflow(replyTo: ActorRef, workflowRequest: WorkflowRequest, authHeader: HttpHeader): Unit = {
    println("IN POSTWORKFLOW")
    /*
     * See https://docs.google.com/document/d/11_qHPBbEg3Hr4Vs3lh3dvU0dLl1I2zt6rmNxEkW1U1U/edit#
     * for details on the workflow request mapping.
     */

    // Build the list of parts
    // TODO: would it be better to use a mutable list instead of re-creating each time?
    var parts = List(
      BodyPart("workflowType", makeTextEntity(workflowRequest.workflow_type)),
      BodyPart("workflowTypeVersion", makeTextEntity(workflowRequest.workflow_type_version))
    )

    if (workflowRequest.workflow_params.isDefined) {
      parts = BodyPart("workflowInputs", jsonObjectToEntity(workflowRequest.workflow_params.get)) :: parts
    }

    if (workflowRequest.workflow_engine_parameters.isDefined) {
      parts = BodyPart("workflowOptions", jsonObjectToEntity(workflowRequest.workflow_engine_parameters.get)) :: parts
    }

    if (workflowRequest.tags.isDefined) {
      parts = BodyPart("labels", jsonObjectToEntity(workflowRequest.tags.get)) :: parts
    }

    getWorkflowSourceBodyPart(workflowRequest).onComplete {
      case Success(WesResponseBodyPart(bodyPart)) => {
        parts = bodyPart :: parts
        postRequestToCromwell(replyTo, parts, authHeader)
      }
      case Success(WesResponseError(msg, code)) => replyTo ! WesResponseError(msg, code)
      case Success(_) => replyTo ! WesResponseError("Internal server error", StatusCodes.InternalServerError.intValue)
      case Failure(f) => replyTo ! WesResponseError(f.getMessage, StatusCodes.InternalServerError.intValue)
    }
  }

  def getWorkflow(replyTo: ActorRef, workflowId: String, authHeader: HttpHeader): Unit = {
    val url: String = cromwellPath + "/" + workflowId + "/metadata"
    val request = HttpRequest(method = HttpMethods.GET, uri = url, headers = List(authHeader))
    val responseFuture = Http().singleRequest(request)
    responseFuture.onComplete {
      case Success(response) => {
        response.status match {
          case StatusCodes.OK => {
            val bodyDataFuture: Future[String] = Unmarshal(response.entity).to[String]
            bodyDataFuture map { bodyData =>
              replyTo ! WesResponseWorkflowMetadata(cromwellMetadataToWesWorkflowLog(bodyData))
            }
          }

          case StatusCodes.BadRequest => replyTo ! WesResponseError("The request is malformed", response.status.intValue())

          case StatusCodes.InternalServerError => replyTo ! WesResponseError("Cromwell server error", response.status.intValue())

          case _ => replyTo ! WesResponseError("Unexpected response status", response.status.intValue())
        }
      }
      case Failure(_) => replyTo ! WesResponseError("Http error", StatusCodes.InternalServerError.intValue)
    }
  }

  def getWorkflowStatus(replyTo: ActorRef, workflowId: String, authHeader: HttpHeader): Unit = {
    val url: String = cromwellPath + "/" + workflowId + "/status"
    val request = HttpRequest(method = HttpMethods.GET, uri = url, headers = immutable.Seq(authHeader))
    val responseFuture = Http().singleRequest(request)

    responseFuture.onComplete {
      case Success(response) => {
        println("RESPONSE IS: " + response)
        response.status match {
          case StatusCodes.OK => {
            val bodyDataFuture: Future[String] = Unmarshal(response.entity).to[String]

            bodyDataFuture map { bodyData =>
              val cromwellStatusResponse: CromwellStatusResponse = CromwellStatusResponse.toCromwellStatusResponse(bodyData)
              replyTo ! WesResponseStatus(cromwellStatusResponse.id, cromwellToWesStatus(cromwellStatusResponse.status))
            }
          }
          case StatusCodes.BadRequest => replyTo ! WesResponseError("The request is malformed", response.status.intValue())

          case StatusCodes.InternalServerError => replyTo ! WesResponseError("Cromwell server error", response.status.intValue())

          case _ => replyTo ! WesResponseError("Unexpected response status", response.status.intValue())
        }
      }
      case Failure(_) => replyTo ! WesResponseError("Http error", StatusCodes.InternalServerError.intValue)
    }
  }

  def deleteWorkflow(replyTo: ActorRef, workflowId: String, authHeader: HttpHeader): Unit = {
    val url: String = cromwellPath + "/" + workflowId + "/abort"
    val request = HttpRequest(method = HttpMethods.POST, uri = url, headers = List(authHeader))
    val responseFuture = Http().singleRequest(request)
    responseFuture.onComplete {
      case Success(response) => {
        response.status match {
          case StatusCodes.OK => {
            val bodyDataFuture: Future[String] = Unmarshal(response.entity).to[String]

            bodyDataFuture map { bodyData =>
              val cromwellStatusResponse: CromwellStatusResponse = CromwellStatusResponse.toCromwellStatusResponse(bodyData)
              replyTo ! WesResponseDeleteWorkflowId(cromwellStatusResponse.id)
            }
          }
          case StatusCodes.NotFound => replyTo ! WesResponseError("The requested workflow was not found", response.status.intValue())

          case StatusCodes.BadRequest => replyTo ! WesResponseError("The request is malformed", response.status.intValue())

          case StatusCodes.InternalServerError => replyTo ! WesResponseError("Cromwell server error", response.status.intValue())

          case _ => replyTo ! WesResponseError("Unexpected response status", response.status.intValue())
        }
      }
      case Failure(_) => replyTo ! WesResponseError("Http error", StatusCodes.InternalServerError.intValue)
    }
  }

  def makeJsonEntity(content: String): HttpEntity.Default = {
    val bytes = ByteString(content)
    HttpEntity.Default(ContentTypes.`application/json`, bytes.length, Source.single(bytes))
  }

  def jsonObjectToEntity(content: JsObject): HttpEntity.Default = {
    val bytes = ByteString(content.toString())
    HttpEntity.Default(ContentTypes.`application/json`, bytes.length, Source.single(bytes))
  }

  def makeTextEntity(content: String): HttpEntity.Default = {
    val bytes = ByteString(content)
    HttpEntity.Default(ContentTypes.`text/plain(UTF-8)`, bytes.length, Source.single(bytes))
  }

  def cromwellToWesStatus(cromwellState: String): WorkflowState = {
    cromwellState match {
      case "On Hold" => PAUSED
      case "Submitted" => QUEUED
      case "Running" => RUNNING
      case "Aborting" => CANCELED
      case "Aborted" => CANCELED
      case "Succeeded" => COMPLETE
      case "Failed" => EXECUTOR_ERROR
      case _ => UNKNOWN
    }
  }

  def cromwellMetadataToWesWorkflowLog(json: String): WorkflowLog = {
    // FIXME: this seems to go on way too long
    val metadata = CromwellMetadata.toCromwellMetadata(json)

    val workflowParams = metadata.submittedFiles.inputs map { JsonParser(_).asJsObject }

    val workflowTags = metadata.submittedFiles.labels map { JsonParser(_).asJsObject }

    val workflowEngineParams = metadata.submittedFiles.options map { JsonParser(_).asJsObject }

    val workflowRequest = WorkflowRequest(
      workflow_descriptor = metadata.submittedFiles.workflow,
      workflow_params = workflowParams,
      workflow_type = metadata.submittedFiles.workflowType,
      workflow_type_version = metadata.submittedFiles.workflowTypeVersion,
      tags = workflowTags,
      workflow_engine_parameters = workflowEngineParams,
      workflow_url = None
    )

    val workflowLogData = WorkflowLogEntry(
      name = metadata.workflowName,
      cmd = None,
      start_time = metadata.start,
      end_time = metadata.end,
      stdout = None,
      stderr = None,
      exit_code = None
    )

    val taskLogs = for {
      callsArray <- metadata.calls.toSeq
      (taskName, metadataEntries) <- callsArray
      metadataEntry <- metadataEntries
      logEntry = cromwellCallsMetadataEntryToLogEntry(taskName, metadataEntry)
    } yield logEntry

    WorkflowLog(
      workflow_id = metadata.id,
      request = workflowRequest,
      state = cromwellToWesStatus(metadata.status),
      workflow_log = Option(workflowLogData),
      task_logs = Option(taskLogs),
      outputs = metadata.outputs
    )
  }

  def cromwellCallsMetadataEntryToLogEntry(taskName: String, callsMetadata: CromwellCallsMetadata): WorkflowLogEntry = {
    val newTaskName = callsMetadata.shardIndex map {
      case -1 => taskName
      case notMinusOne => s"$taskName.$notMinusOne"
    } getOrElse taskName

    WorkflowLogEntry(
      name = Option(newTaskName),
      cmd = None,
      start_time = callsMetadata.start,
      end_time = callsMetadata.end,
      stdout = callsMetadata.stdout,
      stderr = callsMetadata.stderr,
      exit_code = callsMetadata.returnCode
    )
  }

  def getWorkflowStatusList(bodyData: String): List[WesResponseStatus] = {
    val cromwellQueryResponse: CromwellQueryResponse = CromwellQueryResponse.toCromwellQueryResponse(bodyData)
    val cromwellList: List[CromwellStatusResponse] = cromwellQueryResponse.results
    cromwellList.map(x => WesResponseStatus(x.id, cromwellToWesStatus(x.status)))
  }

  def postRequestToCromwell(replyTo: ActorRef, bodyPartList: List[BodyPart], authHeader: HttpHeader) = {
    val formData = FormData(Source(bodyPartList))
    val request = HttpRequest(method = HttpMethods.POST, uri = cromwellPath, entity = formData.toEntity, headers = List(authHeader))
    println("SUBMITTING THIS TO CROMIAM: " + request)
    val responseFuture = Http().singleRequest(request)

    responseFuture.onComplete {
      case Success(response) => {
        println("RECEIVED THIS FROM CROMIAM: " + response)
        response.status match {
          case StatusCodes.Created => {
            val bodyDataFuture: Future[String] = Unmarshal(response.entity).to[String]

            bodyDataFuture map { bodyData =>
              val cromwellPostResponse: CromwellStatusResponse = CromwellStatusResponse.toCromwellStatusResponse(bodyData)
              replyTo ! WesResponseCreateWorkflowId(cromwellPostResponse.id)
            }
          }

          case StatusCodes.BadRequest => replyTo ! WesResponseError("The request is malformed", response.status.intValue())

          case StatusCodes.InternalServerError => replyTo ! WesResponseError("Cromwell server error", response.status.intValue())

          case _ => replyTo ! WesResponseError("Unexpected response status", response.status.intValue())
        }
      }
      case Failure(_) => replyTo ! WesResponseError("Http error", StatusCodes.InternalServerError.intValue)
    }
  }

  def getWorkflowSourceBodyPart(workflowRequest: WorkflowRequest): Future[WesResponse] = {
    if (workflowRequest.workflow_descriptor.isDefined) {
      Future.successful(WesResponseBodyPart(BodyPart("workflowSource", makeJsonEntity(workflowRequest.workflow_descriptor.get))))
    } else if (workflowRequest.workflow_url.isDefined) {
      val request = HttpRequest(method = HttpMethods.GET, uri = workflowRequest.workflow_url.get)
      val responseFuture = Http().singleRequest(request)

      responseFuture.flatMap { response =>
        response.status match {
          case StatusCodes.OK => {
            val bodyDataFuture: Future[String] = Unmarshal(response.entity).to[String]
            bodyDataFuture map { bodyData =>
              WesResponseBodyPart(BodyPart("workflowSource", makeJsonEntity(bodyData)))
            }
          }
          case StatusCodes.BadRequest => Future.successful(WesResponseError("The request is malformed", response.status.intValue()))

          case StatusCodes.InternalServerError => Future.successful(WesResponseError("Cromwell server error", response.status.intValue()))

          case _ => Future.successful(WesResponseError("Unexpected response status", response.status.intValue()))
        }
      } recoverWith {
        case _ => Future.successful(WesResponseError("Http error", StatusCodes.InternalServerError.intValue))
      }
    } else Future.successful(WesResponseError("Workflow source not provided", StatusCodes.BadRequest.intValue))
  }
}
