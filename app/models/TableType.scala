package models

object TableType extends Enumeration {
  val Min = Value("Min")
  val Hour = Value("Hour")
  val defaultMap = Map((Min -> "分鐘資料"), (Hour -> "小時資料"))
}
