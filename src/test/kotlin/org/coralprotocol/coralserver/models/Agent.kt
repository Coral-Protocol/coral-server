import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.source.decodeFromStream
import org.coralprotocol.coralserver.orchestrator.UnresolvedRegistryAgent
import java.io.File


fun main() {

    val toml = Toml(
        TomlInputConfig (ignoreUnknownNames = true, allowEmptyValues = true)
    )

    val agent = toml.decodeFromStream<UnresolvedRegistryAgent>(File("examples/camel-search-maths/interface/coral-agent.toml").inputStream())
    println(agent.resolve())
}