import java.io.File

import commands._
import scala.util.{ Failure, Try }
import io.fabric8.kubernetes.client.DefaultKubernetesClient

abstract class Cli()(implicit logger: CliLogger) {
  private lazy val kubeClient = new DefaultKubernetesClient()

  def transform[T](cmd: Command[T], res: T): T

  def handleError[T](cmd: Command[T], ex: Throwable): Unit

  def run[T](cmd: Command[T]): Try[T] = {
    logger.trace(s"Cli run command: $cmd")
    (for {
      res <- cmd.execution(kubeClient, logger).run()
    } yield {
      transform(cmd, res)
    }).recoverWith {
      case ex =>
        logger.error("Failure", ex)
        handleError(cmd, ex)
        Failure[T](ex)
    }
  }

}

class PrintingCli()(implicit logger: CliLogger) extends Cli()(logger) {

  def transform[T](cmd: Command[T], res: T): T = {
    logger.info(s"Action executed successfully, result: $res")
    val output = cmd.render(res)
    if (!output.isEmpty) {
      Console.println(output)
    }
    res
  }

  def handleError[T](cmd: Command[T], ex: Throwable): Unit = {
    Console.err.println(s"Error: ${ex.getMessage()}")
  }

}
