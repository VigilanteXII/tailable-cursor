package models

import scala.concurrent.{Future, ExecutionContext}

import play.api.libs.json._

import scala.language.implicitConversions
import scala.language.reflectiveCalls
import play.api.libs.iteratee.{Enumeratee, Iteratee, Concurrent}
import play.api.libs.EventSource
import reactivemongo.api.QueryOpts
import play.api.libs.iteratee.Input.El
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.language.postfixOps
import play.api.{Application, Logger}
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection


case class LiveUpdate (
  _id: BSONObjectID = BSONObjectID.generate,
  event: String,
  include: Set[BSONObjectID] = Set.empty,
  exclude: Set[BSONObjectID] = Set.empty,
  data: JsObject = Json.obj()
)

object LiveUpdate {
  protected def collectionName: String = "liveUpdates"
  implicit val ec = ExecutionContext.Implicits.global

  val futureCollection: Future[JSONCollection] = {
    val db = ReactiveMongoPlugin.db
    val collection = db.collection[JSONCollection](collectionName)
    collection.stats().flatMap {
      case stats if !stats.capped =>
        // the collection is not capped, so we convert it
        println("converting to capped")
        collection.convertToCapped(1024 * 1024, None)
      case _ => Future(collection)
    }.recover {
      // the collection does not exist, so we create it
      case _ =>
        println("creating capped collection...")
        collection.createCapped(1024 * 1024, None)
    }.map { _ =>
      println("the capped collection is available")
      collection
    }
  }

  implicit def format: Format[LiveUpdate] = Json.format[LiveUpdate]

  private val (enumerator, channel) = Concurrent.broadcast[LiveUpdate]

  def insert(m: LiveUpdate)(implicit ec: ExecutionContext) = {
    futureCollection.flatMap(_.insert(m))
  }

  def enumerate = {
    enumerator &> Enumeratee.map { update => Json.obj(
      "event" -> update.event,
      "data" -> update.data
    ).as[JsValue]} &> EventSource()
  }

  def onStart(app: Application) {
    implicit val ec = ExecutionContext.Implicits.global

    futureCollection.map{ collection =>
      val cursor = collection
        .find(Json.obj())
        .options(QueryOpts().tailable.awaitData)
        .cursor[LiveUpdate]

      val enumerator = cursor.enumerate()

      //enumerator |>> Iteratee.skipToEof

      enumerator |>> Iteratee.foreach { liveUpdate =>
        channel push El(liveUpdate)
      }
    }
  }
}