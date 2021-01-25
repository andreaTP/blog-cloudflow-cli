import scala.util.{ Success, Try }
import scala.jdk.CollectionConverters._
import commands._
import buildinfo.BuildInfo
import io.fabric8.kubernetes.client.KubernetesClient

trait Execution[T] {
  def run(): Try[T]
}

final case class VersionExecution(v: Version) extends Execution[VersionResult] {
  def run(): Try[VersionResult] = {
    Success(VersionResult(BuildInfo.version))
  }
}

final case class ListExecution(l: List, client: KubernetesClient, logger: CliLogger) extends Execution[ListResult] {
  def run(): Try[ListResult] = {
    logger.info("Executing command List")

    Try {
      val podsStatuses = client
        .pods()
        .inAnyNamespace()
        .list()
        .getItems()
        .asScala
        .map { pod =>
          val name = pod.getMetadata.getName
          val namespace = pod.getMetadata.getNamespace
          val status = pod.getStatus.getPhase
          val message = Option(pod.getStatus.getMessage).getOrElse("")
          models.PodStatus(name = name, namespace = namespace, status = status, message = message)
        }
        .toList

      ListResult(podsStatuses)
    }
  }
}
