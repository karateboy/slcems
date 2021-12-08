package models

import scalikejdbc._

case class User(username: String, password: String)

object User {
  implicit val session: DBSession = AutoSession

  def get(username: String) = {
    sql"""
         Select *
         From User
         Where username = ${username}
         """.map(rs => User(rs.string(1), rs.string(2))).first().apply()
  }
}
