package com.verily.server

// The WesResponse provides a trait for all possible responses to requests to the WES REST API
sealed trait WesResponse

case class WesResponseError(msg: String, status_code: Int) extends WesResponse

case class WesResponseWorkflowId(workflow_id: String) extends WesResponse

