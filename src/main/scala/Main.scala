import commands._
import scala.util._

object Main {

  def main(args: Array[String]): Unit = {
    val exitValue =
      OptionsParser(args) match {
        case Some(Options(logLevel, Some(command))) =>
          run(logLevel, command)
        case _ => 1
      }

    System.exit(exitValue)
  }

  def run(logLevel: Option[String], command: Command[_]): Int = {
    val logger: CliLogger = new CliLogger(logLevel)
    try {
      Setup.init()
      logger.trace(s"Starting to execute ${command}")

      val cli = new PrintingCli()(logger)
      cli.run(command) match {
        case Success(_) => 0
        case Failure(_) => 1
      }
    } catch {
      case ex: Throwable =>
        logger.warn("Unexpected termination", ex)
        Console.err.println("Unexpected termination, please re-run with '--log-level=error' and file an issue")
        1
    } finally {
      logger.close()

      System.out.flush()
      System.out.close()

      System.err.flush()
      System.err.close()
    }
  }

}
