name := "Cloud Simulator"

version := "0.1"

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  // Typesafe Config
  "com.typesafe" % "config" % "1.4.0",

  //CloudSim Plus
  "org.cloudsimplus" % "cloudsim-plus" % "5.1.0",

  //junit testing framework
  "com.novocode" % "junit-interface" % "0.8" % "test->default",

)
