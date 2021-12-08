package controllers

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import play.api.Logging
import play.api.mvc.Results.Unauthorized
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc.{ActionBuilder, Request, RequestHeader, Result, WrappedRequest}

import java.time.Instant
import java.util.Date
import scala.concurrent.ExecutionContext.Implicits.global
case class UserCertificate(username:String, token:String)
case class UserRequest[A](userCertificate:UserCertificate, request: Request[A]) extends WrappedRequest(request)

object JWTAuthentication extends Logging {
  val secret = "hCpcm;L]1FmtwNyHPoHKzwWy/;oXjGJ9wmXJv@cDjEU7?VO0e1pSmLBa[KAe`Hcx"
  val issuer= "WECC"
  val algorithm = Algorithm.HMAC256(secret)
  val verifier = JWT.require(algorithm).withIssuer(issuer).build()

  def signToken(): String ={
    try {
      val now = Instant.now()
      JWT.create()
        .withIssuer(issuer)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plusSeconds(60*30)))
        .sign(algorithm)
    } catch {
      case ex: Exception =>
        logger.error("Failed to sign token", ex)
          throw ex;
    }
  }

  def verifyToken(token:String): Boolean = {
    try{
      val jwt: DecodedJWT = verifier.verify(token)
      true
    }catch {
      case ex:Exception=>
        logger.error("failed to verify token", ex)
        false
    }
  }

  def getUserinfo(request: RequestHeader):Option[UserCertificate] = {
    None
  }

  def onUnauthorized(request: RequestHeader): Result = {
    Unauthorized("")
  }

}
