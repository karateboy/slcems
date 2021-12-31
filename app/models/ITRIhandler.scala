package models

import controllers.RecordList

import java.time.{LocalDateTime, ZoneId}

object ITRIhandler {
  def dataHandler(tableType: TableType.Value, dataList:Seq[RecordList]) = {
    dataList.foreach(data=>{
      def getGenerating(): Double = {
        val generatingMT=Seq("A_SOLAR_RT", "B_SOLAR_RT", "C_SOLAR_RT", "D_SOLAR_RT", "E1_SOLAR_RT",
          "E2_SOLAR_RT", "TRI_SOLAR_RT", "BUSSTOP_SOLAR_RT", "B2_SOLAR_RT")
        val rtList =
          for(mtData<-data.mtDataList if generatingMT.contains(mtData.mtName)) yield
            mtData.value

        rtList.sum
      }

      def getConsuming(): Double = {
        val valueOpt = data.mtDataList.find(p=>p.mtName == "DHB01_KW").map(_.value)
        valueOpt.getOrElse(0)
      }

      val dateTime = LocalDateTime.ofInstant(data.time.toInstant, ZoneId.systemDefault())
      Record.upsertPowerRecord(tableType)(PowerRecord(2, dateTime, Some(getGenerating()), None, Some(getConsuming())))
    })
  }
}
