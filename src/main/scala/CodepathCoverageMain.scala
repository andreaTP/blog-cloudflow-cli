import commands.format

object CodepathCoverageMain extends App {


  OptionsParser(args)

  val logger: CliLogger = new CliLogger(Some("trace"))

  Setup.init()

  val cli = new PrintingCli()(logger)

  val version = cli.run(commands.Version())
  version.get.render(format.Default)
  version.get.render(format.Json)
  version.get.render(format.Yaml)

  val list = cli.run(commands.List())
  list.get.render(format.Default)
  list.get.render(format.Json)
  list.get.render(format.Yaml)

  System.exit(0)
}