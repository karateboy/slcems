package models

import akka.actor.{Actor, Cancellable, Props}
import models.RDbuildingCollector.ParseWebInfo
import org.jsoup.Jsoup
import play.api.{Logger, Logging}
import play.api.libs.ws.WSClient

import java.time.{Instant, LocalTime, Period}
import javax.inject.Inject
import scala.concurrent.duration.{FiniteDuration, MINUTES, SECONDS}
import scala.concurrent.ExecutionContext.Implicits.global


object RDbuildingCollector {
  def props() = Props[RDbuildingCollector]()
  case object ParseWebInfo
}

class RDbuildingCollector @Inject()() extends Actor with Logging {
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
      try{
        val doc = Jsoup.connect("https://stb.stpi.narl.org.tw/").get()
        val pv = doc.select("#pv")
        logger.info(pv.toString)
        val ess = doc.select("#ess")
        logger.info(ess.toString)
        val building = doc.select("#building")
        logger.info(building.toString)
      }catch{
        case ex:Exception=>
          logger.error("error", ex)
      }

  }
}