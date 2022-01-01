package models

import scalikejdbc.{AutoSession, DBSession, scalikejdbcSQLInterpolationImplicitDef}

case class Monitor(id:Int, name:String, displayName:String, contractCapacity:Double)
object Monitor {
  implicit val session: DBSession = AutoSession
  def getList: List[Monitor] = {
    sql"""
          Select *
          From Monitor
         """.map(rs=>Monitor(rs.int(1), rs.string(2), rs.string(3), rs.double(4))).list().apply()
  }
  val idMap: Map[String, Int] =
    getList.map(m=>m.name -> m.id).toMap

  val map: Map[Int, Monitor] = getList.map(m=>m.id -> m).toMap
}
