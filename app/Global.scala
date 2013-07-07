import models.{LiveUpdate}
import play.api.{Application, GlobalSettings}
import scala.concurrent.ExecutionContext

/**
 * Created with IntelliJ IDEA.
 * User: tohr-private
 * Date: 7/7/13
 * Time: 18:02
 * To change this template use File | Settings | File Templates.
 */
object Global extends GlobalSettings {
  implicit val ec = ExecutionContext.Implicits.global

  override def onStart(app: Application) {
    super.onStart(app)
    LiveUpdate.onStart(app)
  }
}
