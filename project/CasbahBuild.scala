import sbt._
import Keys._
import Project.Initialize

object CasbahBuild extends Build {
  import Dependencies._
  import Resolvers._

  lazy val buildSettings = Seq(
    organization := "org.mongodb",
    version      := "2.5.0-SNAPSHOT",
    crossScalaVersions := Seq("2.9.2", "2.9.1", "2.9.0-1", "2.9.0")
  )

  val allSourceDirectories = SettingKey[Seq[Seq[File]]]("all-source-directories")

  def sxrOpts(baseDir: File, sourceDirs: Seq[Seq[File]], scalaVersion: String): Seq[String] = {
    if (scalaVersion.startsWith("2.10"))
      Seq()
    else
      Seq("-P:sxr:base-directory:" + sourceDirs.flatten.mkString(";").replaceAll("\\\\","/"))
  }

  override lazy val settings = super.settings ++ buildSettings

  lazy val baseSettings = Defaults.defaultSettings  ++ Seq(
      resolvers ++= Seq(sonatypeRels, sonatypeSnaps, sonatypeSTArch, mavenOrgRepo),
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "console", "junitxml"),
      autoCompilerPlugins := true,
      addCompilerPlugin("org.scala-tools.sxr" % "sxr_2.9.0" % "0.2.7"),
      allSourceDirectories <<= projects.map(sourceDirectories in Compile in _).join,
      scalacOptions in (Compile, doc) <++= (baseDirectory, allSourceDirectories, scalaVersion) map sxrOpts,
      scalacOptions in (Compile, doc) <++=  (baseDirectory, scalaVersion, version, baseDirectory in LocalProject("casbah")).map {
        (bd, sv, v, rootBase) =>
         val tagOrBranch = if (v.endsWith("-SNAPSHOT")) "dev" else "v" + v
         val docSourceUrl = "http://{{WEBSITE_ROOT}}api.sxr/€{FILE_PATH}.scala.html"
          Seq("-sourcepath", rootBase.getAbsolutePath, "-doc-source-url", docSourceUrl)
      },
      testOptions in Test += Tests.Setup( () => {

        "mongoimport -d casbahIntegration -c yield_historical.in --drop ./casbah-core/src/test/resources/yield_historical_in.json" !

        "mongoimport -d casbahIntegration -c books --drop ./casbah-core/src/test/resources/bookstore.json" !

        "mongoimport -d casbahIntegration -c artilces --drop ./casbah-core/src/test/resources/articles.json" !

        } )
    )

  lazy val parentSettings = baseSettings ++ Seq(
    publishArtifact := false
  )

  lazy val defaultSettings = baseSettings ++ Seq(
    libraryDependencies ++= Seq(
      scalatest(scalaVersion), scalatime(scalaVersion), specs2(scalaVersion),
      slf4j, slf4jJCL, junit
    ),
    autoCompilerPlugins := true,
    parallelExecution in Test := true,
    testFrameworks += TestFrameworks.Specs2
  )


  lazy val casbah = Project(
    id        = "casbah",
    base      = file("."),
    settings  = parentSettings ++ Unidoc.settings,
    aggregate = Seq(commons, core, query, gridfs)
  ) dependsOn(commons, core, query, gridfs)

  lazy val commons = Project(
    id       = "casbah-commons",
    base     = file("casbah-commons"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Seq(mongoJavaDriver, slf4j, slf4jJCL)
    )
  )

  lazy val core = Project(
    id       = "casbah-core",
    base     = file("casbah-core"),
    settings = defaultSettings ++ Seq(parallelExecution in Test := false)
  ) dependsOn(commons, query)

  lazy val query = Project(
    id       = "casbah-query",
    base     = file("casbah-query"),
    settings = defaultSettings
  ) dependsOn(commons)

  lazy val gridfs = Project(
    id       = "casbah-gridfs",
    base     = file("casbah-gridfs"),
    settings = defaultSettings
  ) dependsOn(core)

}

object Dependencies {

  val mongoJavaDriver  = "org.mongodb" % "mongo-java-driver" % "2.10.0"
  val slf4j            = "org.slf4j" % "slf4j-api" % "1.6.0"
  val junit            = "junit" % "junit" % "4.10" % "test"
  val slf4jJCL         = "org.slf4j" % "slf4j-jcl" % "1.6.0" % "test"

  def scalaVersionString(scalaVer: sbt.SettingKey[String]): String = {
    var result = ""
    scalaVersion { sv => result = sv }
    if (result == "") result = "2.9.2"
    result
  }

  def scalatest(scalaVer: sbt.SettingKey[String]) =
    scalaVersionString(scalaVer) match {
      case _ => "org.scalatest" % "scalatest_2.9.2" % "1.8" % "provided"
    }

  def scalatime(scalaVer: sbt.SettingKey[String]) =
      scalaVersionString(scalaVer) match {
        case "2.9.2" => "org.scala-tools.time" % "time_2.9.1" % "0.5"
        case _ => "org.scala-tools.time" %% "time" % "0.5"
      }

  def specs2(scalaVer: sbt.SettingKey[String]) =
      scalaVersionString(scalaVer) match {
          case "2.9.0"   => "org.specs2" % "specs2_2.9.0" % "1.7.1"
          case "2.9.0-1" => "org.specs2" % "specs2_2.9.0" % "1.7.1"
          case "2.9.1"   => "org.specs2" % "specs2_2.9.1" % "1.12.2"
          case "2.9.2"   => "org.specs2" % "specs2_2.9.2" % "1.12.2"
      }
}

object Resolvers {
  val sonatypeSnaps = "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  val sonatypeRels = "releases" at "https://oss.sonatype.org/content/repositories/releases"
  val sonatypeSTArch = "scalaTools Archive" at "https://oss.sonatype.org/content/groups/scala-tools/"
  val mavenOrgRepo = "Maven.Org Repository" at "http://repo1.maven.org/maven2/org/"
  val typeSafe = "typesafe" at "http://repo.typesafe.com/typesafe/releases/"
}

