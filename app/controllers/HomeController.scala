package controllers

import models._
import play.api.Application
import play.api.libs.json._
import play.api.mvc._

import javax.inject._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, application: Application) extends AbstractController(cc) {

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
}
