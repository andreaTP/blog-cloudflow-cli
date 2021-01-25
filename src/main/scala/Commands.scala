import java.io.File
import buildinfo.BuildInfo
import scopt.{ OParser, Read }

import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }
import io.fabric8.kubernetes.client.KubernetesClient

case class Options(logLevel: Option[String] = None, command: Option[commands.Command[_]] = None) {
  def loggingLevel = logLevel.getOrElse("error")
}

object OptionsParser {
  import commands.format

  private val builder = OParser.builder[Options]
  import builder._

  def fromStringToFormat(str: String) = {
    str.toLowerCase() match {
      case "default" => format.Default
      case "json"    => format.Json
      case "yaml"    => format.Yaml
      case _         => throw new IllegalArgumentException(s"'${str}' is not a valid output format.")
    }
  }

  private val outputFmt = {
    implicit val outputfmtRead: Read[format.Format] = Read.reads(fromStringToFormat)

    import scala.language.existentials
    opt[format.Format]('o', "output")
      .text(s"available options are: 'default', 't' or 'table', 'json', 'yaml'")
      .action((fmt, o) => {
        val cmd = o.command match {
          case Some(v: commands.Command[_]) => Some(v.withOutput(fmt))
          case _                            => None
        }
        o.copy(command = cmd)
      })
  }

  private val commonOptions = {
    OParser.sequence(
      opt[String]('v', "log-level")
        .action((l, o) => o.copy(logLevel = Some(l)))
        .text("the logging level"),
      help("help").text("prints this usage text"),
      note("Availble commands: " + sys.props("line.separator")))
  }

  private val versionCommand = {
    cmd("version")
      .action((_, o) => o.copy(command = Some(commands.Version())))
      .text("show the current version")
      .children(outputFmt)
  }

  private val listCommand = {
    cmd("list")
      .action((_, o) => o.copy(command = Some(commands.List())))
      .text("list pods")
      .children(outputFmt)
  }

  private val finalValidation = {
    checkConfig(
      o =>
        if (o.command.isEmpty) failure("Command not provided")
        else success)
  }

  val optionParser = {
    import builder._
    OParser.sequence(
      programName("kubectl-lp"),
      head("kubectl-lp", BuildInfo.version),
      commonOptions,
      versionCommand,
      listCommand,
      finalValidation)
  }

  def apply(args: Array[String]): Option[Options] =
    OParser.parse(optionParser, args, Options())
}

object commands {
  object format {
    sealed trait Format
    final case object Default extends Format
    final case object Json extends Format
    final case object Yaml extends Format

  }

  sealed trait Command[T] {
    val output: format.Format

    def execution(kubeClient: KubernetesClient, logger: CliLogger): Execution[T]

    def render(result: T): String

    def withOutput(fmt: format.Format): Command[T]
  }

  case class Version(output: format.Format = format.Default) extends Command[VersionResult] {

    def execution(kubeClient: KubernetesClient, logger: CliLogger): Execution[VersionResult] = {
      VersionExecution(this)
    }

    def render(vr: VersionResult) = {
      vr.render(output)
    }

    def withOutput(fmt: format.Format) = this.copy(output = fmt)
  }

  case class List(output: format.Format = format.Default) extends Command[ListResult] {

    def execution(kubeClient: KubernetesClient, logger: CliLogger): Execution[ListResult] = {
      ListExecution(this, kubeClient, logger)
    }

    def render(lr: ListResult) = {
      lr.render(output)
    }

    def withOutput(fmt: format.Format) = this.copy(output = fmt)
  }

}
