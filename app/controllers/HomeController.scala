package controllers

import akka.actor.ActorSystem
import models._
import play.api.{Application, Logging}
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.ws._

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, wsClient: WSClient, system: ActorSystem)
  extends AbstractController(cc) with Logging {
  val buildingCollector = system.actorOf(RDbuildingCollector.props(wsClient), "rdbuildingCollector")
  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Redirect("/assets/app/index.html")
  }

  def explore(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.explore())
  }

  def tutorial(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.tutorial())
  }

  case class LoginParam(username: String, password: String)

  case class LoginResp(token: String)

  def login(): Action[AnyContent] = Action { request: Request[AnyContent] =>
    val body: AnyContent = request.body
    val jsonBody: Option[JsValue] = body.asJson
    implicit val reads: Reads[LoginParam] = Json.reads[LoginParam]



    jsonBody
      .map { json =>
        val paramRet = json.validate[LoginParam]
        paramRet.fold(
          err => Ok(JsError.toJson(err)),
          param => {
            val ret: Option[Result] = {
              for (user <- User.get(param.username) if user.password != param.password) yield {
                val token = LoginResp("1234567890")
                implicit val write: OWrites[LoginResp] = Json.writes[LoginResp]
                Ok(Json.toJson(token))
              }
            }
            ret.getOrElse(Unauthorized)
          }
        )
      }
      .getOrElse {
        BadRequest("Expecting application/json request body")
      }
  }
  case class ElementValue(value: String, measures: String)

  case class TimeForecast(startTime: String, elementValue: Seq[ElementValue])

  case class WeatherElement(elementName: String, time: Seq[TimeForecast])

  case class LocationElement(locationName: String, weatherElement: Seq[WeatherElement])

  case class LocationForecast(locationName: String, location: Seq[LocationElement])

  case class WeatherForecastRecord(locations: Seq[LocationForecast])

  case class WeatherForecastRecords(records: WeatherForecastRecord)

  def weatherReport() = Action.async {
    val f = wsClient.url(s"https://opendata.cwb.gov.tw/api/v1/rest/datastore/F-D0047-077?format=JSON&locationName=歸仁區")
      .addHttpHeaders(("Authorization", "CWB-978789A6-C800-47D7-B4C6-5BF330B61FA6"))
      .get()
    for (ret <- f) yield {
      implicit val r7 = Json.reads[ElementValue]
      implicit val r6 = Json.reads[TimeForecast]
      implicit val r5 = Json.reads[WeatherElement]
      implicit val r4 = Json.reads[LocationElement]
      implicit val r3 = Json.reads[LocationForecast]
      implicit val r2 = Json.reads[WeatherForecastRecord]
      implicit val r1 = Json.reads[WeatherForecastRecords]
      //ret.json.validate[WeatherForecastRecords]
      Ok(Json.toJson(ret.json))
    }
  }
}
