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
                       generating: Option[Double], storing: Option[Double], consuming: Option[Double])

object Record extends Logging {
  implicit val session: DBSession = AutoSession

  def getLatestRecordTime(tabType: TableType.Value): Option[LocalDateTime] = {
    val tabName = Record.getTabName(tabType)
    sql"""
      SELECT TOP 1 DateTime
      FROM ${tabName}
      ORDER BY DateTime DESC
      """.map { r => r.localDateTime(1) }.single().apply()
  }

  def getLatestMonitorRecordTime(tabType: TableType.Value, monitorID: Int): Option[LocalDateTime] = {
    val tabName = Record.getTabName(tabType)
    sql"""
      SELECT TOP 1 DateTime
      FROM ${tabName}
      ORDER BY DateTime DESC
      Where MonitorID = $monitorID
      """.map { r => r.localDateTime(1) }.single().apply()
  }

  def recalculateHour(monitorID: Int, start: LocalDateTime): Unit = {
    val minData = getRecord(TableType.Min)(monitorID, start, start.plusHours(1))
    logger.info(s"recalculateHour ${start} with #=${minData.size}")
    if (minData.nonEmpty) {
      val size = minData.size
      val generating = minData.flatMap(_.generating).sum / size
      val storing = minData.flatMap(_.storing).sum / size
      val consuming = minData.flatMap(_.consuming).sum / size
      Record.upsertPowerRecord(TableType.Hour)(PowerRecord(monitorID, start, Some(generating), Some(storing), Some(consuming)))
    }else {
      Record.upsertPowerRecord(TableType.Hour)(PowerRecord(monitorID, start, Some(0), Some(0), Some(0)))
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
      rs.doubleOpt(3), rs.doubleOpt(4), rs.doubleOpt(5))).list().apply()
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
