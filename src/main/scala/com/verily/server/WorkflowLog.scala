package com.verily.server

import spray.json.{ DefaultJsonProtocol, JsObject, JsonFormat }

final case class WorkflowLogEntry(
  name: Option[String],
  cmd: Option[Seq[String]],
  start_time: Option[String],
  end_time: Option[String],
  stdout: Option[String],
  stderr: Option[String],
  exit_code: Option[Int]
)

//object WorkflowLogEntry {
//  import DefaultJsonProtocol._
//  implicit val workflowLogEntryFormat: JsonFormat[WorkflowLogEntry] = jsonFormat7(WorkflowLogEntry.apply)
//}

final case class WorkflowLog(
  workflow_id: String,
  request: WorkflowRequest,
  state: WorkflowState,
  workflow_log: Option[WorkflowLogEntry],
  task_logs: Option[Seq[WorkflowLogEntry]],
  outputs: Option[JsObject] /* WesObject */ )

//object WorkflowLog {
//  import DefaultJsonProtocol._
//  implicit val workflowLogFormat: JsonFormat[WorkflowLog] = jsonFormat6(WorkflowLog.apply)
//}
