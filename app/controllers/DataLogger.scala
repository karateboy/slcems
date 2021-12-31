package controllers
import models._
import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import java.time.ZoneId
import java.util.Date
import javax.inject.{Inject, Singleton}

case class LatestRecordTime(time: Long)
case class MtRecord(mtName: String, value: Double, status: String)

case class RecordList(time: Date, var mtDataList: Seq[MtRecord], monitor: String) {
  def mtMap: Map[String, MtRecord] = {
    val pairs =
      mtDataList map { data => data.mtName -> data }
    pairs.toMap
  }
}

@Singleton
class DataLogger @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) with Logging {
  implicit val latestRecordTimeWrite = Json.writes[LatestRecordTime]
  implicit val mtRecordRead = Json.reads[MtRecord]
  implicit val RecordListRead = Json.reads[RecordList]

  def getRecordRange(tabType: TableType.Value)(monitorStr: String): Action[AnyContent] = Action {
    val monitorID = Monitor.idMap(monitorStr)
    val timeOpt = Record.getLatestMonitorRecordTime(tabType, monitorID)

    val latestRecordTime = timeOpt.map {
      time =>
        LatestRecordTime(time.atZone(ZoneId.systemDefault()).toEpochSecond * 1000)
    }.getOrElse(LatestRecordTime(0))

    Ok(Json.toJson(latestRecordTime))
  }

  def getHourRecordRange: String => Action[AnyContent] = getRecordRange(TableType.Hour) _

  def getMinRecordRange: String => Action[AnyContent] = getRecordRange(TableType.Min) _

  def insertDataRecord(tabType: TableType.Value)(monitorStr: String) = Action {
    implicit request =>
      val jsonBody: Option[JsValue] = request.body.asJson
      jsonBody
        .map { json =>
          val paramRet = json.validate[Seq[RecordList]]
          paramRet.fold(err => {
            logger.error(JsError(err).toString())
            BadRequest(Json.obj("ok" -> false, "msg" -> JsError(err).toString().toString()))
          },
            recordListSeq => {
              monitorStr match {
                case "ITRI" =>
                  ITRIhandler.dataHandler(tabType, recordListSeq)
                case other:String =>
                  logger.warn(s"unexpected monitor ${other}")
              }
              Ok(Json.obj("ok" -> true))
            })
        }
        .getOrElse {
          BadRequest("Expecting application/json request body")
        }
  }

  def insertHourRecord = insertDataRecord(TableType.Hour) _

  def insertMinRecord = insertDataRecord(TableType.Min) _

}
