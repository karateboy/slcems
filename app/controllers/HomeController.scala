package controllers

import javax.inject._
import play.api._
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
  
  def explore() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.explore())
  }
  
  def tutorial() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.tutorial())
  }

  case class LoginParam(username:String, password:String)
  case class LoginResp(token:String)
  def login(): Action[AnyContent] = Action { request: Request[AnyContent] =>
    val body: AnyContent          = request.body
    val jsonBody: Option[JsValue] = body.asJson
    implicit val reads = Json.reads[LoginParam]

    jsonBody
      .map { json =>
        val paramRet = json.validate[LoginParam]
        paramRet.fold(
          err=>Ok(JsError.toJson(err)),
          param=>{
            val token = LoginResp("1234567890")
            implicit val write = Json.writes[LoginResp]
            Ok(Json.toJson(token))
          }
        )
        Ok("Got: " + (json \ "name").as[String])
      }
      .getOrElse {
        BadRequest("Expecting application/json request body")
      }
  }
}
