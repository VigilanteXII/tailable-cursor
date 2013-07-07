package controllers

import play.api._
import play.api.mvc._
import play.modules.reactivemongo.{ReactiveMongoPlugin}
import play.modules.reactivemongo.json.collection.JSONCollection
import play.api.libs.json.{JsArray, JsValue, Json}
import models.LiveUpdate
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import play.api.Play.current

object Application extends Controller {
  val itemCollection = ReactiveMongoPlugin.db.collection[JSONCollection]("items")

  def index = Action {
    Ok(views.html.main("Your new application is ready."))
  }

  def addItem = Action {
    Async {
      itemCollection.insert(Json.obj(
        "bla" -> "blubb"
      )).map { result =>
        Ok
      }
    }
  }

  def getItems = Action {
    Async {
      itemCollection.find(Json.obj()).cursor[JsValue].collect[List].map { result =>
        Ok(JsArray(result))
      }
    }
  }

  def addLiveUpdate = Action {
    Async {
      LiveUpdate.insert(LiveUpdate(
        event = "test"
      )).map { result =>
        Ok
      }
    }
  }

  def getLiveUpdates = Action { request =>
    Ok.stream(LiveUpdate.enumerate).as("text/event-stream")
  }
}