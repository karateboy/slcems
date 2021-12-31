package models

import scalikejdbc.{AutoSession, DBSession, scalikejdbcSQLInterpolationImplicitDef}

case class Monitor(id:Int, name:String)
object Monitor {
  implicit val session: DBSession = AutoSession
  def getList: List[Monitor] = {
    sql"""
          Select *
          From Monitor
         """.map(rs=>Monitor(rs.int(1), rs.string(2))).list().apply()
  }
  val idMap: Map[String, Int] =
    getList.map(m=>m.name -> m.id).toMap

}
