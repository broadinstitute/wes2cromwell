package com.verily.server

object Transmogriphy {

  def getWorkflows(): WorkflowListResponse = {
    WorkflowListResponse(
      Seq(
        WorkflowDescription("wfid111", WorkflowState.INITIALIZING),
        WorkflowDescription("wfid222", WorkflowState.RUNNING)
      )
    )
  }

  def postWorkflow(workflowRequest: WorkflowRequest): String = {
    "wfid333"
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
