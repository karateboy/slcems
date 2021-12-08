lazy val root = (project in file("."))
  .enablePlugins(PlayScala, SwaggerPlugin)
  .settings(
    name := """slcems""",
    organization := "com.wecc",
    version := "1.0-0",
    scalaVersion := "2.13.6",
    libraryDependencies ++= Seq(
      guice,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
    ),
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings"
    )
  )
libraryDependencies ++= Seq(
  "org.scalikejdbc" %% "scalikejdbc"                  % "3.5.0",
  "org.scalikejdbc" %% "scalikejdbc-config"           % "3.5.0",
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % "2.8.0-scalikejdbc-3.5"
)
// https://mvnrepository.com/artifact/com.microsoft.sqlserver/mssql-jdbc
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "9.4.0.jre8"

// https://mvnrepository.com/artifact/org.scalikejdbc/scalikejdbc-play-fixture
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc-play-fixture" % "2.8.0-scalikejdbc-3.5"

// https://mvnrepository.com/artifact/com.iheart/play-swagger
libraryDependencies += "com.iheart" %% "play-swagger" % "0.10.6-PLAY2.8"

libraryDependencies += "org.webjars" % "swagger-ui" % "3.43.0"

// https://mvnrepository.com/artifact/com.auth0/java-jwt
libraryDependencies += "com.auth0" % "java-jwt" % "3.18.2"

swaggerDomainNameSpaces := Seq("models")