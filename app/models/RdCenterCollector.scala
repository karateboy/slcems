package models

import akka.actor.{Actor, Cancellable, Props}
import play.api.Logging
import play.api.libs.json.{JsError, Json}
import play.api.libs.ws.WSClient

import java.time.{LocalDateTime, LocalTime}
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, MINUTES, SECONDS}
import scala.util.Failure

case class GeneralData(grid: String, pv: String, building: String, ess: String, ev: String)

case class GeneralStatus(status: Boolean, data: Seq[GeneralData])

object RdCenterCollector extends Logging {
  def props(wsClient: WSClient) = Props(classOf[RdCenterCollector], wsClient)

  implicit val r2 = Json.reads[GeneralData]
  implicit val r1 = Json.reads[GeneralStatus]

  case object ParseWebInfo
}

class RdCenterCollector @Inject()(wsClient: WSClient) extends Actor with Logging {

  import RdCenterCollector._

  val timer: Cancellable = {
    val now = LocalTime.now()
    val nextTime = now.withSecond(30).plusMinutes(1)
    context.system.scheduler.scheduleWithFixedDelay(FiniteDuration(now.until(nextTime, java.time.temporal.ChronoUnit.SECONDS), SECONDS),
      FiniteDuration(1, MINUTES), self, ParseWebInfo)
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
              if (status.status && status.data.nonEmpty) {
                val generating: Double = try {
                  status.data(0).pv.toDouble
                } catch {
                  case _: Exception =>
                    0d
                }
                val storing = try {
                  Some(status.data(0).ess.toDouble)
                } catch {
                  case _: Exception =>
                    None
                }

                val consuming: Double = try {
                  status.data(0).building.toDouble
                } catch {
                  case _: Exception =>
                    0
                }
                val now = LocalDateTime.now().withSecond(0).withNano(0)
                Record.upsertPowerRecord(TableType.Min)(PowerRecord(1, now, generating, storing, consuming))
                if(now.getMinute == 0){
                  val start = now.minusHours(1)
                  Record.recalculateHour(1, start)
                }
              }
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