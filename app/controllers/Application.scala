package controllers

import akka.util.duration._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.Comet
import play.api.libs.json._
import play.api.libs.json.Json._

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import validation.Constraints._

import models.Log
import actors.StoryActor
import actors.StoryActor._

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index())
  }

  def listen(keywords: Option[String]) = Action {
    implicit val LogComet = Comet.CometMessage[Log](log => toJson(log).toString)
    val cometEnumeratee =  Comet( callback = "window.parent.session.observable.log.receive")
    val finalEnumeratee = keywords.map { k =>
      Enumeratee.filter[Log](log => log.message.toLowerCase().contains(k.toLowerCase())) ><> cometEnumeratee
    }.getOrElse(cometEnumeratee)

    AsyncResult {
      (StoryActor.ref ? (Listen(), 5.seconds)).mapTo[Enumerator[Log]].asPromise.map {
        chunks => Ok.stream(chunks &> finalEnumeratee)
      }
    }
  }

  // val toEventSource = Enumeratee.map[String] { msg => "data:" + toJson(msg)+""""""}
  // def listenSSE() = Action {
  //   SimpleResult(
  //     header = ResponseHeader(OK, Map(
  //       CONTENT_LENGTH -> "-1",
  //       CONTENT_TYPE -> "text/event-stream"
  //     )),
  //     Streams.getHeap &> toEventSource)
  // }

  def eval() = Action { implicit request =>
    request.body.asJson.get match {
      case log: JsObject => StoryActor.ref ! NewLog(Log.fromJsObject(log)); Ok
      case log: JsValue => Logger.warn("Log isnt an object: " + log); BadRequest
      case _ => Logger.warn("Invalid log format: " + request.body); BadRequest("Invalid Log format: " + request.body)
    }
  }
}
