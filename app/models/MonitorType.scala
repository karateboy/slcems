package models

case class MonitorType(id:String, desp:String, unit:String)
object MonitorType {
  val list: List[MonitorType] = List(
    MonitorType("generating", "發電量", "KW"),
    MonitorType("storing", "儲能量", "KW"),
    MonitorType("consuming", "用電量", "KW"),
    MonitorType("consumingPercent", "契約容量占比", "%"),
    MonitorType("greenPercent", "綠能滲透率", "%")
  )

  val map: Map[String, MonitorType] = list.map(mt=>mt.id->mt).toMap
}
