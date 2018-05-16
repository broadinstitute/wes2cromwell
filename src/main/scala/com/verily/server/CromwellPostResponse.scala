package com.verily.server

import spray.json.DefaultJsonProtocol.jsonFormat2
import spray.json.{DefaultJsonProtocol, JsonFormat, JsonParser}

case class CromwellPostResponse(id: String, status: String)

object CromwellPostResponse {
  import DefaultJsonProtocol._
  implicit val cromwellPostResponseFormat: JsonFormat[CromwellPostResponse] = jsonFormat2(CromwellPostResponse.apply)

  def toCromwellPostResponse(json: String): CromwellPostResponse = {
    val jsonAst = JsonParser(json)
    jsonAst.convertTo[CromwellPostResponse]
  }
}