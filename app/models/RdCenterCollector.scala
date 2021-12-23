package models

import akka.actor.{Actor, Cancellable, Props}
import controllers.PowerStatus
import models.RdCenterCollector.ParseWebInfo
import play.api.Logging
import play.api.libs.json.{JsError, Json}
import play.api.libs.ws.WSClient

import java.time.LocalTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, MINUTES, SECONDS}
import scala.util.Failure

case class GeneralData(grid: String, pv: String, building: String, ess: String, ev: String)
case class GeneralStatus(status: Boolean, data: Seq[GeneralData])

object RdCenterCollector extends Logging {
  def props(wsClient: WSClient) = Props(classOf[RdCenterCollector], wsClient)
  implicit val r2 = Json.reads[GeneralData]
  implicit val r1 = Json.reads[GeneralStatus]

  def getGeneralPowerStatus(wsClient: WSClient) = {
    try {
      val f = wsClient.url("https://stb.stpi.narl.org.tw/general").get()

      for (ret <- f) yield {
        for(status<-ret.json.validate[GeneralStatus].asOpt if status.status && status.data.nonEmpty) yield {
            PowerStatus("聯合研究中心", status.data(0).pv.toDouble, status.data(0).ess.toDouble, status.data(0).building.toDouble)
        }
      }
    } catch {
      case ex: Exception =>
        Future{
          logger.error("error", ex)
          None
        }
    }
  }
  case object ParseWebInfo
}

class RdCenterCollector @Inject()(wsClient: WSClient) extends Actor with Logging {
  import RdCenterCollector._
  self ! ParseWebInfo
  val timer: Cancellable = {
    def getNextTime(period: Int) = {
      val now = LocalTime.now()
      val residual = now.getMinute % period
      now.plusMinutes(period - residual).withSecond(0).withNano(0)
      now.plusMinutes(period - residual).withSecond(0).withNano(0)
    }

    val period = 15
    val nextTime = getNextTime(period)
    logger.info(s"next logging time = ${nextTime.toString}")
    val now = LocalTime.now()
    context.system.scheduler.scheduleWithFixedDelay(FiniteDuration(now.until(nextTime, java.time.temporal.ChronoUnit.SECONDS), SECONDS),
      FiniteDuration(15, MINUTES), self, ParseWebInfo)
  }


  override def receive: Receive = {
    case ParseWebInfo =>
      try {
        val f = wsClient.url("https://stb.stpi.narl.org.tw/general").get()
        f onComplete ({
          case Failure(exception) =>
            logger.error("failed to get", exception)
          case _ =>

        })

        for (ret <- f) {
          val statusRet = ret.json.validate[GeneralStatus]
          statusRet.fold(
            err => {
              logger.error(JsError(err).toString)
            },
            status => {
              logger.info(status.toString)
            })
        }

      } catch {
        case ex: Exception =>
          logger.error("error", ex)
      }
  }

  override def postStop(): Unit = {
    timer.cancel()
    super.postStop()
  }
}