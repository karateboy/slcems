package models

import scalikejdbc.{AutoSession, DBSession}
import scalikejdbc._

import java.sql.Time
import java.time.Instant

case class User(username:String, password:String, loginTime:Option[Instant], token:Option[String])
object User {
  implicit val session: DBSession = AutoSession

  def get(username:String): Option[User] = {
    sql"""
         Select *
         From User
         Where username = ${username}
         """.map(rs=>{
      User(rs.string("username"),
        rs.string("password"),
        rs.timeOpt("loginTime").map(t=> t.toInstant),
        rs.stringOpt("token"))
    }).first().apply()
  }
}
