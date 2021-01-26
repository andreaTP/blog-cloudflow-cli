Global / cancelable := true

val getMuslBundle = taskKey[Unit]("Fetch Musl bundle")
val winPackageBin = taskKey[Unit]("PackageBin Graal on Windows")

lazy val lp =
  Project(id = "lp", base = file("."))
    .settings(name := "kubectl-lp")
    .settings(
      scalaVersion := "2.13.3",
      libraryDependencies := Seq(
        "io.fabric8" % "kubernetes-client" % "5.0.0",
        "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.11.4",
        "com.github.scopt" %% "scopt" % "4.0.0",
        "org.wvlet.airframe" %% "airframe-log" % "20.10.0",
        "de.vandermeer" % "asciitable" % "0.3.2",
        "ch.qos.logback" % "logback-classic" % "1.2.3"
      ),
      Compile / mainClass := Some("Main"),
      Compile / discoveredMainClasses := Seq(),
      run / fork := true,
      getMuslBundle := {
        if (!((ThisProject / baseDirectory).value / "src" / "graal" / "bundle").exists && graalVMNativeImageGraalVersion.value.isDefined) {
          TarDownloader.downloadAndExtract(
            new URL("https://github.com/gradinac/musl-bundle-example/releases/download/v1.0/musl.tar.gz"),
            (ThisProject / baseDirectory).value / "src" / "graal")
        }
      },
      GraalVMNativeImage / packageBin := {
        if (graalVMNativeImageGraalVersion.value.isDefined) {
          (GraalVMNativeImage / packageBin).dependsOn(getMuslBundle).value
        } else {
          (GraalVMNativeImage / packageBin).value
        }
      },
      graalVMNativeImageOptions := Seq(
          "--verbose",
          "--no-server",
          "--enable-http",
          "--enable-https",
          "--enable-url-protocols=http,https,file,jar",
          "--enable-all-security-services",
          "-H:+JNI",
          "-H:IncludeResourceBundles=com.sun.org.apache.xerces.internal.impl.msg.XMLMessages",
          "-H:+ReportExceptionStackTraces",
          "--no-fallback",
          "--initialize-at-build-time",
          "--report-unsupported-elements-at-runtime",
          // TODO: possibly to be removed
          "--allow-incomplete-classpath",
          "--initialize-at-run-time" + Seq(
            "com.typesafe.config.impl.ConfigImpl",
            "com.typesafe.config.impl.ConfigImpl$EnvVariablesHolder",
            "com.typesafe.config.impl.ConfigImpl$SystemPropertiesHolder",
            "com.typesafe.config.impl.ConfigImpl$LoaderCacheHolder",
            "io.fabric8.kubernetes.client.internal.CertUtils$1").mkString("=", ",", "")),
      GraalVMNativeImage / winPackageBin := {
        val targetDirectory = target.value
        val binaryName = name.value
        val nativeImageCommand = graalVMNativeImageCommand.value
        val className = (Compile / mainClass).value.getOrElse(sys.error("Could not find a main class."))
        val classpathJars = scriptClasspathOrdering.value
        val extraOptions = graalVMNativeImageOptions.value
        val streams = Keys.streams.value
        val dockerCommand = DockerPlugin.autoImport.dockerExecCommand.value

        targetDirectory.mkdirs()
        val temp = IO.createTemporaryDirectory

        try {
          classpathJars.foreach {
            case (f, _) =>
              IO.copyFile(f, (temp / f.getName))
          }

          val command = {
            val nativeImageArguments = {
              Seq("--class-path", s""""${(temp / "*").getAbsolutePath}"""", s"-H:Name=$binaryName") ++ extraOptions ++ Seq(
                className)
            }
            Seq(nativeImageCommand) ++ nativeImageArguments
          }

          (sys.process.Process(command, targetDirectory).!) match {
            case 0 => targetDirectory / binaryName
            case x => sys.error(s"Failed to run $command, exit status: " + x)
          }
        } finally {
          temp.delete()
        }
      })
    .enablePlugins(BuildInfoPlugin, GraalVMNativeImagePlugin)

// makePom fails, often with: java.lang.StringIndexOutOfBoundsException: String index out of range: 0
addCommandAlias(
  "winGraalBuild",
  s"""set makePom / publishArtifact := false; set graalVMNativeImageCommand := "${sys.env
    .get("JAVA_HOME")
    .getOrElse("")
    .replace("""\""", """\\\\""")}\\\\bin\\\\native-image.cmd"; graalvm-native-image:winPackageBin""")

addCommandAlias(
  "linuxStaticBuild",
  """set graalVMNativeImageGraalVersion := Some("20.1.0-java11"); set graalVMNativeImageOptions ++= Seq("--static", "-H:UseMuslC=/opt/graalvm/stage/resources/bundle/"); graalvm-native-image:packageBin""")

addCommandAlias(
  "regenerateGraalVMConfig",
  s"""set run / fork := true; set run / javaOptions += "-agentlib:native-image-agent=config-output-dir=${file(
    ".").getAbsolutePath}/src/main/resources/META-INF/native-image"; runMain CodepathCoverageMain""")
