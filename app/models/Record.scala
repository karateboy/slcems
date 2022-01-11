package models

import play.api.Logging
import scalikejdbc.{AutoSession, DBSession, SQLSyntax, scalikejdbcSQLInterpolationImplicitDef}

import java.sql.Timestamp
import java.time.LocalDateTime

case class Stat(
                 avg: Option[Double],
                 min: Option[Double],
                 max: Option[Double],
                 count: Int,
                 total: Int,
                 overCount: Int) {
  val effectPercent: Option[Double] = if (total != 0) Some(count.toFloat * 100 / total) else None
  val overPercent: Option[Double] = if (total != 0) Some(overCount.toFloat * 100 / total) else None
}

case class MonitorTypeRecord(monitorType: String, dataList: List[(Long, Option[Float], Option[String])], stat: Stat)

case class PowerRecord(monitorID: Int, dateTime: LocalDateTime,
                       generating: Double, storing: Option[Double], consuming: Double)

object Record extends Logging {
  implicit val session: DBSession = AutoSession

  def getLatestMonitorRecordTime(tabType: TableType.Value, monitorID: Int): Option[LocalDateTime] = {
    val tabName = Record.getTabName(tabType)
    sql"""
      SELECT TOP 1 [DateTime]
      FROM ${tabName}
      Where [MonitorID] = $monitorID
      ORDER BY [DateTime] DESC
      """.map { r => r.localDateTime(1) }.single().apply()
  }

  def getLatestMonitorRecord(tableType: TableType.Value, monitorID: Int) = {
    val tabName = Record.getTabName(tableType)
    sql"""
      SELECT TOP 1 *
      FROM ${tabName}
      Where [MonitorID] = $monitorID
      ORDER BY [DateTime] DESC
      """.map { rs =>
      PowerRecord(rs.int(1), rs.localDateTime(2), rs.double(3),
        rs.doubleOpt(4), rs.double(5))
    }.single().apply()
  }

  def recalculateHour(monitorID: Int, start: LocalDateTime): Unit = {
    val minData = getRecord(TableType.Min)(monitorID, start, start.plusHours(1))
    if (minData.nonEmpty) {
      val size = minData.size
      val generating = minData.map(_.generating).sum / size
      val storing = minData.flatMap(_.storing).sum / size
      val consuming = minData.map(_.consuming).sum / size
      Record.upsertPowerRecord(TableType.Hour)(PowerRecord(monitorID, start, generating, Some(storing), consuming))
    } else {
      Record.upsertPowerRecord(TableType.Hour)(PowerRecord(monitorID, start, 0, Some(0), 0))
    }
  }

  def upsertPowerRecord(tabType: TableType.Value)(pwrRecord: PowerRecord): Int = {
    val tabName = Record.getTabName(tabType)
    val timestamp: java.sql.Timestamp = Timestamp.valueOf(pwrRecord.dateTime)
    sql"""
         UPDATE $tabName
              SET [generating] = ${pwrRecord.generating}
                  ,[storing] = ${pwrRecord.storing}
                  ,[consuming] = ${pwrRecord.consuming}
              WHERE [MonitorID]=${pwrRecord.monitorID} and [DateTime] = ${timestamp}

              IF(@@ROWCOUNT = 0)
              BEGIN
                INSERT INTO $tabName
                  ([MonitorID]
                    ,[DateTime]
                    ,[generating]
                    ,[storing]
                    ,[consuming])
                VALUES
                  (${pwrRecord.monitorID}
                    ,${timestamp}
                    ,${pwrRecord.generating}
                    ,${pwrRecord.storing}
                    ,${pwrRecord.consuming})
              END
         """.update().apply()
  }

  def getRecord(tabType: TableType.Value)(monitorID: Int, start: LocalDateTime, end: LocalDateTime): List[PowerRecord] = {
    val tabName = Record.getTabName(tabType)
    val startT: Timestamp = Timestamp.valueOf(start)
    val endT: Timestamp = Timestamp.valueOf(end)
    sql"""
         Select *
         From $tabName
         Where [DateTime] >= $startT and [DateTime] < $endT and [MonitorID] = $monitorID
         """.map(rs => PowerRecord(rs.int(1), rs.localDateTime(2),
      rs.double(3), rs.doubleOpt(4), rs.double(5))).list().apply()
  }

  def getRecordMap(tabType: TableType.Value)(monitor: String, start: LocalDateTime, end: LocalDateTime): Map[String, Seq[(LocalDateTime, Option[Double])]] = {
    val tabName = Record.getTabName(tabType)
    val monitorID = Monitor.idMap(monitor)
    val startT: Timestamp = Timestamp.valueOf(start)
    val endT: Timestamp = Timestamp.valueOf(end)
    val retList: Seq[PowerRecord] =
      sql"""
         Select *
         From $tabName
         Where [DateTime] >= $startT and [DateTime] < $endT and [MonitorID] = $monitorID
         Order by [DateTime] ASC
         """.map(rs => PowerRecord(rs.int(1), rs.localDateTime(2),
        rs.double(3), rs.doubleOpt(4), rs.double(5))).list().apply()

    val pairs =
      for {
        mt <- MonitorType.map.keys
      } yield {
        val list =
          for {
            doc <- retList
            dt = doc.dateTime
          } yield {
            mt match {
              case "generating"=>
                (dt, Some(Math.abs(doc.generating)))
              case "storing" =>
                (dt, doc.storing)
              case "consuming" =>
                (dt, Some(doc.consuming))
              case "consumingPercent" =>
                (dt, Some(doc.consuming*100/Monitor.map(monitorID).contractCapacity))
              case "greenPercent"=>
                if(doc.consuming == 0)
                  (dt, Some(0d))
                else
                  (dt, Some(doc.generating*100/doc.consuming))
            }
          }
        mt -> list
      }
    pairs.toMap
  }

  def getTabName(tab: TableType.Value) = {
    tab match {
      case TableType.Hour =>
        SQLSyntax.createUnsafely(s"[HourRecord]")
      case TableType.Min =>
        SQLSyntax.createUnsafely(s"[MinRecord]")
    }
  }
}
