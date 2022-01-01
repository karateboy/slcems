package controllers

import akka.actor.ActorSystem
import models._
import play.api.Logging
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._

import java.time.{Duration, Instant, LocalDateTime, Period, ZoneId}
import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import Highchart._

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAmount

case class PowerStatusSummary(summary: Seq[PowerRecord])
/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents, wsClient: WSClient, system: ActorSystem)
  extends AbstractController(cc) with Logging {
  val buildingCollector = system.actorOf(RdCenterCollector.props(wsClient), "rdbuildingCollector")
  implicit val w1 = Json.writes[PowerRecord]
  implicit val w2 = Json.writes[PowerStatusSummary]
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

  def realtimeStatus: Action[AnyContent] = Action {
    val monitorIdList = Monitor.idMap.values.toList.sorted
    val powerRecordList =
      for(monitorId<-monitorIdList) yield
        Record.getLatestMonitorRecord(TableType.Min, monitorId).getOrElse(PowerRecord(monitorId, LocalDateTime.now(), 0, None, 0))
    Ok(Json.toJson(PowerStatusSummary(summary = powerRecordList)))
  }

  def getPeriods(start: LocalDateTime, endTime: LocalDateTime, d: TemporalAmount): List[LocalDateTime] = {
    import scala.collection.mutable.ListBuffer

    val buf = ListBuffer[LocalDateTime]()
    var current = start
    while (current.isBefore(endTime)) {
      buf.append(current)
      current = current.plus(d)
    }

    buf.toList
  }

  def getPeriodReportMap(monitor: String, mtList: Seq[String],
                         tabType: TableType.Value)
                        (start: LocalDateTime, end: LocalDateTime): Map[String, Map[LocalDateTime, Option[Double]]] = {
    val mtRecordListMap = Record.getRecordMap(tabType)(monitor, start, end)

    val mtRecordPairs =
      for (mt <- mtList) yield {
        val recordList = mtRecordListMap(mt)
        val pairs =
            recordList.map { r => r._1 -> r._2 }

        mt -> pairs.toMap
      }
    mtRecordPairs.toMap
  }

  def trendHelper(monitors: Seq[String], monitorTypes: Seq[String], tabType: TableType.Value,
                  start: LocalDateTime, end: LocalDateTime, showActual: Boolean) = {
    val period: Duration =
      tabType match {
        case TableType.Min =>
          Duration.ofMinutes(1)
        case TableType.Hour =>
          Duration.ofHours(1)
      }

    val timeSeq = getPeriods(start, end, period)

    val downloadFileName = {
      val startName = start.format(DateTimeFormatter.ofPattern("yyMMdd"))
      val mtNames = monitorTypes.map {
        MonitorType.map(_).desp
      }
      startName + mtNames.mkString
    }

    val title = s"${start.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))}~${end.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))}"

    def getAxisLines(mt: String) = {
      val std_law_line = None

      val lines = Seq(std_law_line, None).filter {
        _.isDefined
      }.map {
        _.get
      }
      if (lines.length > 0)
        Some(lines)
      else
        None
    }

    val yAxisGroup: Map[String, Seq[(String, Option[Seq[AxisLine]])]] = monitorTypes.map(mt => {
      (MonitorType.map(mt).unit, getAxisLines(mt))
    }).groupBy(_._1)
    val yAxisGroupMap = yAxisGroup map {
      kv =>
        val lines: Seq[AxisLine] = kv._2.map(_._2).flatten.flatten
        if (lines.nonEmpty)
          kv._1 -> YAxis(None, AxisTitle(Some(Some(s"${kv._1}"))), Some(lines))
        else
          kv._1 -> YAxis(None, AxisTitle(Some(Some(s"${kv._1}"))), None)
    }
    val yAxisIndexList = yAxisGroupMap.toList.zipWithIndex
    val yAxisUnitMap = yAxisIndexList.map(kv => kv._1._1 -> kv._2).toMap
    val yAxisList = yAxisIndexList.map(_._1._2)

    def getSeries(): Seq[seqData] = {

      val monitorReportPairs =
        for {
          monitor <- monitors
        } yield {
          monitor -> getPeriodReportMap(monitor, monitorTypes, tabType)(start, end)
        }

      val monitorReportMap = monitorReportPairs.toMap
      for {
        m <- monitors
        mt <- monitorTypes
        valueMap = monitorReportMap(m)(mt)
      } yield {
        val timeData =
          if (showActual) {
            timeSeq.map { time =>
              if (valueMap.contains(time))
                (time.atZone(ZoneId.systemDefault()).toEpochSecond()*1000, valueMap(time))
              else {
                (time.atZone(ZoneId.systemDefault()).toEpochSecond()*1000, None)
              }
            }
          } else {
            for (time <- valueMap.keys.toList.sorted) yield {
              (time.atZone(ZoneId.systemDefault()).toEpochSecond()*1000, valueMap(time))
            }
          }
        val timeValues = timeData.map{t=>(t._1, t._2)}
        val mID = Monitor.idMap(m)
        seqData(name = s"${Monitor.map(mID).displayName}_${MonitorType.map(mt).desp}",
          data = timeValues, yAxis = yAxisUnitMap(MonitorType.map(mt).unit))
      }
    }

    val series = getSeries()

    val xAxis = {
      val duration = Duration.between(start, end)
      if (duration.getSeconds > 2*86400)
        XAxis(None, gridLineWidth = Some(1), None)
      else
        XAxis(None)
    }

    val chart =
      if (monitorTypes.length == 1) {
        val mt = monitorTypes(0)
        val mtCase = MonitorType.map(monitorTypes(0))

        HighchartData(
          Map("type" -> "line"),
          Map("text" -> title),
          xAxis,

          Seq(YAxis(None, AxisTitle(Some(Some(s"${mtCase.desp} (${mtCase.unit})"))), getAxisLines(mt))),
          series,
          Some(downloadFileName))
      } else {
        HighchartData(
          Map("type" -> "line"),
          Map("text" -> title),
          xAxis,
          yAxisList,
          series,
          Some(downloadFileName))
      }

    chart
  }

  def historyTrendChart(monitorStr: String, monitorTypeStr: String, tableTypeStr: String,
                        startNum: Long, endNum: Long) = Action {
    implicit request =>
      val monitors = monitorStr.split(':')
      val monitorTypeStrArray = monitorTypeStr.split(':')
      val monitorTypes = monitorTypeStrArray

      val (tabType, start, end) =
          (TableType.withName(tableTypeStr), Instant.ofEpochSecond(startNum/1000).atZone(ZoneId.systemDefault()).toLocalDateTime,
            Instant.ofEpochSecond(endNum/1000).atZone(ZoneId.systemDefault()).toLocalDateTime)

      val chart = trendHelper(monitors.toIndexedSeq, monitorTypes.toIndexedSeq, tabType, start, end, false)

        Results.Ok(Json.toJson(chart))

  }
}
