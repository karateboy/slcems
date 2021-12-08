package controllers


import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import play.api.Logging

import java.time.Instant
import java.util.Date

class JwtAuthenticator extends Logging {
  val secrets = "t`7auO?6PRI6]pz@lwLVhbSp4E@yAW`hys:`iWrTrdzP9ZK14Nn@vprc[XQU]8ue"
  val algorithm = Algorithm.HMAC256(secrets)
  val issuer = "WECC"

  def getToken() = {
    import com.auth0.jwt.exceptions.JWTCreationException
    try {
      val now = Instant.now()
      JWT.create
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plusSeconds(30 * 60)))
        .withIssuer(issuer)
        .sign(algorithm)
    } catch {
      case exception: JWTCreationException =>
        logger.error("Failed at getToken", exception)
        throw exception
    }
  }

}
