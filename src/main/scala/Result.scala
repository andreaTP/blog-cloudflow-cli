import java.text.SimpleDateFormat
import scala.jdk.CollectionConverters._
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.vandermeer.asciitable._
import de.vandermeer.asciithemes.a7.A7_Grids
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment
import commands.format
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature

trait Result {
  def render(fmt: format.Format): String
}

@JsonCreator
final case class VersionResult(version: String) extends Result {

  def render(fmt: format.Format): String = {
    fmt match {
      case format.Default => {
        val table = new AsciiTable()

        table.setTextAlignment(TextAlignment.LEFT)

        table.addRule()
        table.addRow("VERSION", version)
        table.addRule()

        table.setPaddingLeftRight(1)
        table.getRenderer.setCWC(new CWC_LongestWordMin(3))
        table.getContext.setGrid(A7_Grids.minusBarPlus())

        table.render()
      }
      case format.Json => JsonHelper.objectMapper.writeValueAsString(this)
      case format.Yaml => YamlHelper.objectMapper.writeValueAsString(this)
    }
  }
}

@JsonCreator
final case class ListResult(pods: List[models.PodStatus]) extends Result {

  def render(fmt: format.Format): String = {
    fmt match {
      case format.Default => renderDefault()
      case format.Json    => JsonHelper.objectMapper.writeValueAsString(this)
      case format.Yaml    => YamlHelper.objectMapper.writeValueAsString(this)
    }
  }

  private def renderDefault() = {
    val table = new AsciiTable()

    table.addRule()
    table.addRow("NAMESPACE", "NAME", "STATUS", "MESSAGE")
    table.addRule()

    pods
      .sortWith { (pod1, pod2) =>
        val namespaceComp = pod1.namespace.compareTo(pod2.namespace)
        if (namespaceComp == 0) {
          (pod1.name.compareTo(pod2.name) < 0)
        } else {
          (namespaceComp < 0)
        }
      }
      .foreach { p =>
        table.addRow(p.namespace, p.name, p.status, p.message)
      }

    table.addRule()
    table.setPaddingLeftRight(1)
    table.setTextAlignment(TextAlignment.LEFT)

    table.getRenderer.setCWC(new CWC_LongestWordMin(3))
    table.getContext.setGrid(A7_Grids.minusBarPlus())

    table.render()
  }
}

object JsonHelper {
  val objectMapper = new ObjectMapper().registerModule(new DefaultScalaModule())
}

object YamlHelper {
  val objectMapper =
    new ObjectMapper(new YAMLFactory().disable(Feature.WRITE_DOC_START_MARKER)).registerModule(new DefaultScalaModule())
}
