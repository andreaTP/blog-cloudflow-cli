addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.6")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")

libraryDependencies ++= Seq(
  "org.codehaus.plexus" % "plexus-container-default" % "2.1.0",
  "org.codehaus.plexus" % "plexus-archiver" % "4.2.3")
