package org.coralprotocol.coralserver.agent.registry

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

class DirectoryWatcherAgentRegistrySource(
    val directory: Path,
    private val scope: CoroutineScope,
    private val restrictions: Set<RegistryAgentRestriction> = setOf()
) : AgentRegistrySource(AgentRegistrySourceIdentifier.Local), KoinComponent {

    private val logger by inject<Logger>(named(LOGGER_CONFIG))
    private val agentMap = ConcurrentHashMap<Path, RegistryAgent>()

    val registryAgents: Collection<RegistryAgent>
        get() = agentMap.values

    override val agents: List<RegistryAgentCatalog>
        get() = buildList {
            val catalogs = mutableMapOf<String, MutableList<String>>()
            agentMap.values.forEach { agent ->
                catalogs.getOrPut(agent.name) { mutableListOf() }.add(agent.version)
            }
            catalogs.forEach { (name, versions) ->
                add(RegistryAgentCatalog(name, versions))
            }
        }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                scan()
                watch()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.error(e) { "Error in directory watcher for $directory" }
            }
        }
    }

    private fun scan() {
        if (!Files.exists(directory)) {
            logger.warn { "Monitored directory $directory does not exist" }
            return
        }
        Files.walkFileTree(
            directory,
            mutableSetOf(FileVisitOption.FOLLOW_LINKS),
            200,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (file.fileName.toString() == AGENT_FILE) {
                        loadAgent(file)
                    }
                    return FileVisitResult.CONTINUE
                }
            }
        )
    }

    private fun loadAgent(file: Path) {
        try {
            val agent = resolveRegistryAgentFromStream(
                file = file.toFile(),
                context = RegistryResolutionContext(
                    path = directory,
                    registrySourceIdentifier = AgentRegistrySourceIdentifier.Local
                ),
                exportSettings = mapOf()
            )
            agentMap[file] = agent
            logger.info { "Loaded agent ${agent.name} (${agent.version}) from $file" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load agent from $file" }
        }
    }

    private suspend fun watch() {
        val watchService = FileSystems.getDefault().newWatchService()
        val keys = mutableMapOf<WatchKey, Path>()

        fun registerRecursive(start: Path) {
            if (!Files.exists(start) || !Files.isDirectory(start)) return
            Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        val key = dir.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY
                        )
                        keys[key] = dir
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to register watch for directory $dir" }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }

        registerRecursive(directory)

        if (keys.isEmpty() && !Files.exists(directory)) {
            logger.warn { "Monitored directory $directory does not exist and could not be watched" }
            watchService.close()
            return
        }

        watchService.use {
            while (scope.isActive) {
                val key = runInterruptible {
                    watchService.take()
                }

                val dir = keys[key] ?: continue

                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue

                    val name = event.context() as Path
                    val child = dir.resolve(name)

                    when (kind) {
                        StandardWatchEventKinds.ENTRY_CREATE -> {
                            if (Files.isDirectory(child)) {
                                registerRecursive(child)
                                Files.walkFileTree(child, object : SimpleFileVisitor<Path>() {
                                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                                        if (file.fileName.toString() == AGENT_FILE) {
                                            loadAgent(file)
                                        }
                                        return FileVisitResult.CONTINUE
                                    }
                                })
                            } else if (name.toString() == AGENT_FILE) {
                                loadAgent(child)
                            }
                        }
                        StandardWatchEventKinds.ENTRY_DELETE -> {
                            val toRemove = agentMap.keys.filter { it.startsWith(child) }
                            toRemove.forEach { path ->
                                val removed = agentMap.remove(path)
                                logger.info { "Removed agent ${removed?.name} because $path was deleted (or its parent)" }
                            }
                        }
                        StandardWatchEventKinds.ENTRY_MODIFY -> {
                            if (name.toString() == AGENT_FILE) {
                                loadAgent(child)
                            }
                        }
                    }
                }

                if (!key.reset()) {
                    keys.remove(key)
                    if (keys.isEmpty() && !Files.exists(directory)) break
                }
            }
        }
    }

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        val registryAgent = agentMap.values.find { it.identifier == agent }
            ?: throw RegistryException.AgentNotFoundException("Agent ${agent.name} not found in monitored directory $directory")

        return RestrictedRegistryAgent(registryAgent, restrictions)
    }
}