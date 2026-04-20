@file:OptIn(FlowPreview::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.coralprotocol.coralserver.util.isWindows
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A toml based agent registry source, matching toml files based on a given pattern.
 *
 * ### [pattern]
 * A basic path pattern is the path to a directory that contains a single coral-agent.toml file.
 *
 * Given the following structure:
 *
 * ```
 * my_agents/
 * ├─ agent1/
 * │  ├─ coral-agent.toml
 * ├─ agent2/
 * │  ├─ coral-agent.toml
 * ```
 *
 * A pattern of "my_agents/agent1" will load the agent that "my_agents/agent1/coral-agent.toml" describes into a
 * local registry source.
 *
 * More advanced patterns containing the `*` character can be used to add multiple agents at once.  Given the above
 * structure again, a pattern of "my_agents/&#42;" will match my_agents/agent1 and my_agents/agent2 including both:
 * - my_agent/agent1/coral-agent.toml
 * - my_agent/agent2/coral-agent.toml
 *
 * Patterns should be absolute paths and should not start with a '*' character.
 *
 * ### [watch]
 *
 * This class has the ability to register watchers on [watchCoroutineScope] to automatically update the registered
 * agents.  This is a useful development utility but has some considerations:
 *
 * 1. This should not be turned on in production.
 * 2. There are limitations on what the JVM allows when it comes to watches.  It is not technically possible to reach
 * 100% coverage with the JVM's [java.nio.file.WatchService] and as a back-up [scan] is provided.  Consider using
 * [scanOnInterval] to provide 100% coverage.
 *
 * Scanning will attempt to find:
 * 1. New agents that match the provided pattern
 * 2. Agents that have been deleted
 * 3. Modifications to existing agents
 *
 * Feature 1. has a chance of missing new agents, especially when the directories involved in the creation of the agent
 * were programmatically created - faster than the [java.nio.file.WatchService] is able to catch.
 */
class FileAgentRegistrySource(
    val registry: AgentRegistry,
    val pattern: String,
    val watch: Boolean = false,
    val watchCoroutineScope: CoroutineScope,
    restrictions: Set<RegistryAgentRestriction> = setOf()
) : ListAgentRegistrySource(name = "pattern [${normalizedPathString(pattern)}]", restrictions = restrictions) {

    data class WatchJobKey(
        val path: Path,
        val kinds: List<WatchEvent.Kind<*>>,
        val pattern: String = ""
    )

    private val logger by inject<Logger>(named(LOGGER_CONFIG))
    private val loadedAgentFiles = ConcurrentHashMap.newKeySet<String>()
    private val deletionWatchers = ConcurrentHashMap.newKeySet<String>()
    private val watchJobs = ConcurrentHashMap<WatchJobKey, Job>()

    private var sharedWatchService: WatchService? = null

    private data class HandlerRegistration(
        val kinds: Set<WatchEvent.Kind<*>>,
        val channel: kotlinx.coroutines.channels.Channel<WatchEvent<*>>,
        val job: Job
    )

    private val watchHandlers = ConcurrentHashMap<WatchKey, MutableMap<WatchJobKey, HandlerRegistration>>()
    private val watchKeysByPath = ConcurrentHashMap<Path, WatchKey>()
    private val uniqueWatchPaths = ConcurrentHashMap.newKeySet<Path>()
    private var isScanOnIntervalFallbackActive = false

    private val sensitivityModifier: WatchEvent.Modifier? by lazy {
        try {
            val modifierClass = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier")
            modifierClass.getField("HIGH").get(null) as? WatchEvent.Modifier
        } catch (_: Exception) {
            null
        }
    }

    private var parentPattern: String
    private var remainingPattern: String

    init {
        val parts = normalizedPathString(pattern).split("/")
        parentPattern = if (isWindows()) "${parts.first()}/" else "/${parts.first()}"
        remainingPattern = parts.slice(1..<parts.size).joinToString("/")

        scan()
    }

    private fun isExcluded(name: String): Boolean {
        return name.startsWith(".") || EXCLUDED_DIRECTORIES.contains(name)
    }

    fun scan() {
        loadedAgentFiles.clear()
        deletionWatchers.clear()
        clearAgents()

        watchJobs.forEach { (_, job) -> job.cancel() }
        watchJobs.clear()
        watchHandlers.clear()
        watchKeysByPath.clear()
        uniqueWatchPaths.clear()

        sharedWatchService?.close()
        if (watch) {
            val ws = FileSystems.getDefault().newWatchService()
            sharedWatchService = ws
            startPolling(ws)
        }

        addAgentsFromPattern(remainingPattern, parentPattern)
    }

    private fun startPolling(ws: WatchService) {
        watchCoroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val key = runInterruptible { ws.take() }
                    val registrations = watchHandlers[key]?.values?.toList()
                    if (registrations != null) {
                        val events = key.pollEvents()
                        for (event in events) {
                            registrations.forEach { reg ->
                                if (reg.kinds.contains(event.kind())) {
                                    reg.channel.trySend(event).isSuccess
                                }
                            }
                        }
                    }
                    if (!key.reset()) {
                        val removed = watchHandlers.remove(key)
                        if (removed != null) {
                            val path = key.watchable() as? Path
                            if (path != null) {
                                watchKeysByPath.remove(path)
                                uniqueWatchPaths.remove(path)
                            }
                        }
                    }
                } catch (_: ClosedWatchServiceException) {
                    break
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (isActive) {
                        logger.error(e) { "Error in watch polling loop" }
                    }
                }
            }
        }
    }

    fun scanOnInterval(interval: Duration) {
        watchCoroutineScope.launch {
            delay(interval)
            scan()
        }
    }

    private fun addAgentsFromPattern(pathPattern: String, parent: String) {
        val parts = pathPattern.split("/").filter { it.isNotEmpty() }
        var current = Path.of(parent).absolute()

        logger.debug { "Scanning for agents matching pattern \"$pathPattern\" in \"${normalizedPathString(current)}\"..." }
        parts.forEachIndexed { index, part ->
            val remainingParts = parts.slice(index..<parts.size)

            if (part == "*") {
                // watch this directory for any future items matching the remainder of the pattern (this function will
                // do nothing if watch = false)
                watchDirectory(current, remainingParts)

                // Scan existing subdirectories. Note that the recursion will handle deeper directories,
                // ensuring we don't scan too deep unless the pattern demands it
                current.toFile().listFiles { it.isDirectory && !isExcluded(it.name) }?.forEach {
                    logger.debug { "Found directory \"${normalizedPathString(it.toPath())}\" matching wildcard in pattern \"$pathPattern\"" }
                    addAgentsFromPattern(
                        if (index == parts.lastIndex) {
                            it.name
                        } else {
                            "${it.name}/${parts.slice(index + 1..<parts.size).joinToString("/")}"
                        },
                        normalizedPathString(current)
                    )
                }

                return@addAgentsFromPattern
            } else {
                val next = current.resolve(part)
                if (!next.isDirectory()) {
                    if (current.isDirectory()) {
                        watchDirectory(current, remainingParts)
                    }
                    return@addAgentsFromPattern
                }

                watchForDeletion(next, remainingParts.joinToString("/"), normalizedPathString(current))
                current = next
            }
        }

        // if the last part in this pattern is a wildcard, directories are expected here not agents
        if (parts.isNotEmpty() && parts.last() == "*")
            return

        val agentFile = current.resolve(AGENT_FILE).toFile()
        if (agentFile.exists()) {
            addAgentFromFile(agentFile)
        } else {

            // watching allows for us to wait for agent to be written to this directory
            waitForAgent(current)
            if (parts.isNotEmpty()) {
                watchForDeletion(current, parts.last(), normalizedPathString(current.parent))
            }
        }
    }

    private fun addAgentFromFile(agentFile: File) {
        /*
            There is a possible circumstance where a file is attempted to be loaded twice (especially) when agent files
            are programmatically written.  I have observed (on Windows) the following:

            1. A pattern is given that has no parts currently created, e.g "agents / * "
            2. Programmatically, "agents/agent1/coral-agent.toml" is written (all directories and the file)
            3. The watcher waiting for the "agents" directory to be created calls addAgentsFromPattern.  Because the
               pattern that matched "agents" is "*", a watcher is installed in "agents" to monitor for further
               directories.  addAgentsFromPattern will also immediately traverse the directory and find the full file
               "agents/agent1/coral-agent.toml" - adding it with a call to this function
            4. The installed watched from step 3. also reports that "agents/agent1/coral-agent.toml" was just created
               and calls this function again for the same file
         */
        try {
            val absolutePath = agentFile.absolutePath
            if (loadedAgentFiles.contains(absolutePath))
                return

            loadedAgentFiles.add(absolutePath)

            val agent = readAgent(agentFile)
            val existing = agentCache[agent.identifier]
            if (existing != null) {
                val sameSource =
                    existing.path?.toAbsolutePath()?.normalize() == agent.path?.toAbsolutePath()?.normalize()
                if (sameSource) {
                    removeAgent(existing)
                    addAgent(agent)
                    watchSingleAgent(agentFile, agent)
                    logger.info { "agent updated: ${agent.identifier} - ${normalizedPathString(agentFile)}" }
                    return
                } else {
                    logger.warn { "cannot add agent from file \"${normalizedPathString(agentFile)}\" because the identifier \"${agent.identifier}\" is already taken" }

                    // can still watch this agent though
                    watchSingleAgent(agentFile, null)
                    return
                }
            }

            addAgent(agent)
            watchSingleAgent(agentFile, agent)

            logger.info { "agent added: ${agent.identifier} - ${normalizedPathString(agentFile)}" }
        } catch (e: Exception) {
            watchSingleAgent(agentFile, null)
            logger.error(e) { "Error loading agent from file ${normalizedPathString(agentFile)}" }
        }
    }

    private fun eventStreamForPath(
        path: Path,
        vararg kinds: WatchEvent.Kind<*>,
        pattern: String = "",
        handler: suspend CoroutineScope.(WatchEvent<*>) -> Unit
    ): Job {
        if (!watch) return Job().apply { complete() }

        val watchJobKey = WatchJobKey(path, kinds.toList(), pattern)
        watchJobs[watchJobKey]?.cancel()

        val needNewKey = !watchKeysByPath.containsKey(path)
        if (needNewKey && uniqueWatchPaths.size >= WATCH_CAP) {
            logger.warn { "Watch cap ($WATCH_CAP) exceeded while trying to watch \"${normalizedPathString(path)}\". Falling back to scanOnInterval." }
            if (!isScanOnIntervalFallbackActive) {
                isScanOnIntervalFallbackActive = true
                scanOnInterval(30.seconds)
            }
            return Job().apply { complete() }
        }

        val ws = sharedWatchService ?: return Job().apply { complete() }

        return try {
            val key = if (needNewKey) {
                val kindsArray = arrayOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
                val k = sensitivityModifier?.let { path.register(ws, kindsArray, it) } ?: path.register(ws, *kindsArray)
                watchKeysByPath[path] = k
                uniqueWatchPaths.add(path)
                k
            } else {
                watchKeysByPath[path]!!
            }

            val handlersMap = watchHandlers.getOrPut(key) { ConcurrentHashMap() }
            val desiredKinds = kinds.toSet()

            val channel = kotlinx.coroutines.channels.Channel<WatchEvent<*>>(capacity = 64)
            val consumerJob = CoroutineScope(Dispatchers.IO + watchCoroutineScope.coroutineContext).launch {
                try {
                    for (event in channel) {
                        try {
                            // Receiver scope is this coroutine; cancel() inside handler will cancel this job
                            @Suppress("DeferredResultUnused")
                            handler(this, event)
                        } catch (_: CancellationException) {
                            break
                        } catch (e: Exception) {
                            logger.error(e) { "Error in watch handler for path ${normalizedPathString(path)}" }
                        }
                    }
                } finally {
                    channel.close()
                }
            }

            handlersMap[watchJobKey] = HandlerRegistration(desiredKinds, channel, consumerJob)

            consumerJob.invokeOnCompletion {
                channel.close()
                handlersMap.remove(watchJobKey)
                if (handlersMap.isEmpty()) {
                    watchHandlers.remove(key)
                    key.cancel()
                    val p = key.watchable() as? Path
                    if (p != null) {
                        watchKeysByPath.remove(p)
                        uniqueWatchPaths.remove(p)
                    }
                }
                watchJobs.remove(watchJobKey)
            }

            watchJobs[watchJobKey] = consumerJob
            consumerJob
        } catch (e: Exception) {
            logger.error(e) { "Error registering watch for path \"${normalizedPathString(path)}\"" }
            Job().apply { complete() }
        }
    }

    private fun watchSingleAgent(agentFile: File, agent: RegistryAgent?) {
        var agent = agent
        if (!watch)
            return

        val watchPath = agentFile.toPath().parent
        if (!watchPath.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(watchPath)}\"!" }
            return
        }

        val modificationFlow = MutableSharedFlow<File>(extraBufferCapacity = 64)
        val flowJob = modificationFlow
            .debounce(500.milliseconds)
            .onEach { agentFile ->
                try {
                    val newAgent = readAgent(agentFile)
                    when (val agent = agent) {
                        newAgent -> {
                            logger.trace {
                                "agent file updated but parsed contents did not change - \"${
                                    normalizedPathString(
                                        agentFile
                                    )
                                }\""
                            }
                        }

                        null -> {
                            val existing = agentCache[newAgent.identifier]
                            if (existing != null) {
                                val sameSource =
                                    existing.path?.toAbsolutePath()?.normalize() == newAgent.path?.toAbsolutePath()
                                        ?.normalize()
                                if (!sameSource) {
                                    logger.warn { "cannot add agent from file \"${normalizedPathString(agentFile)}\" because the identifier \"${newAgent.identifier}\" is already taken" }
                                    return@onEach
                                } else {
                                    removeAgent(existing)
                                }
                            }

                            addAgent(newAgent)
                            logger.info { "agent added: ${newAgent.identifier} - \"${normalizedPathString(agentFile)}\"" }
                        }

                        else -> {
                            val existing = agentCache[newAgent.identifier]
                            if (existing != null) {
                                val sameSource =
                                    existing.path?.toAbsolutePath()?.normalize() == newAgent.path?.toAbsolutePath()
                                        ?.normalize()
                                if (!sameSource) {
                                    logger.warn { "cannot update agent from file \"${normalizedPathString(agentFile)}\" because the new identifier \"${newAgent.identifier}\" is already taken" }
                                    return@onEach
                                }
                            }

                            removeAgent(agent)

                            val identifier = if (newAgent.identifier != agent.identifier) {
                                "${agent.identifier} (new identifier: ${newAgent.identifier})"
                            } else {
                                agent.identifier.toString()
                            }

                            addAgent(newAgent)

                            if (newAgent != agent) {
                                logger.info { "agent $identifier updated" }
                                registry.reportLocalDuplicates()
                            }
                        }
                    }

                    agent = newAgent
                } catch (e: Exception) {
                    logger.error(e) { "Error parsing new contents for agent file \"${normalizedPathString(agentFile)}\"" }
                }
            }
            .launchIn(watchCoroutineScope)

        eventStreamForPath(watchPath, ENTRY_MODIFY, ENTRY_DELETE) {
            val fileName = it.context() as Path
            if (fileName.name != agentFile.name)
                return@eventStreamForPath

            when (it.kind()) {
                ENTRY_MODIFY -> {
                    modificationFlow.tryEmit(agentFile)
                }

                ENTRY_DELETE -> {
                    if (agent != null) {
                        logger.warn { "agent deleted: ${agent.identifier} - \"${normalizedPathString(agentFile)}\"" }
                        loadedAgentFiles.remove(agentFile.absolutePath)
                        removeAgent(agent)

                        // if the user deletes and re-adds an agent, it will need this watcher
                        waitForAgent(agentFile.toPath().parent)

                        cancel()
                    }
                }
            }
        }.invokeOnCompletion {
            flowJob.cancel()
            if (agent != null)
                logger.trace { "watcher for agent ${agent.identifier} - \"${normalizedPathString(agentFile)}\" stopped" }
        }
    }

    private fun watchDirectory(directory: Path, remainingParts: List<String>) {
        if (!watch)
            return

        if (!directory.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(directory)}\"!" }
            return
        }

        val nextPart = remainingParts.first()
        val remainingStr = remainingParts.joinToString("/")

        eventStreamForPath(directory, ENTRY_CREATE, pattern = remainingStr) {
            val fileName = (it.context() as Path).name
            val isWildcard = nextPart == "*"
            if (nextPart.equals(fileName, ignoreCase = isWindows()) || (isWildcard && !isExcluded(fileName))) {
                val fullPatternLog = if (nextPart != remainingStr) {
                    " from full pattern \"$remainingStr\""
                } else {
                    ""
                }

                logger.trace { "\"$fileName\" created in \"${normalizedPathString(directory)}\", matching pattern part \"$nextPart\"$fullPatternLog" }
                addAgentsFromPattern(
                    if (isWildcard) {
                        if (remainingParts.size == 1) {
                            fileName
                        } else {
                            "$fileName/${remainingParts.drop(1).joinToString("/")}"
                        }
                    } else {
                        remainingStr
                    },
                    normalizedPathString(directory)
                )

                // if the next part was a specific directory, and it was created, this listener doesn't need to exist anymore
                if (!isWildcard)
                    cancel()
            }
        }.invokeOnCompletion {
            logger.trace { "watcher for \"${remainingParts.joinToString("/")}\" in \"${normalizedPathString(directory)}\" stopped" }
        }
    }

    private fun waitForAgent(directory: Path) {
        if (!watch)
            return

        if (!directory.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(directory)}\"!" }
            return
        }

        logger.trace { "waiting for $AGENT_FILE to be written in \"${normalizedPathString(directory)}\"" }

        eventStreamForPath(directory, ENTRY_CREATE, pattern = AGENT_FILE) {
            if ((it.context() as Path).name == AGENT_FILE) {
                val file = directory.resolve(AGENT_FILE).toFile()

                // Allow time for contents to be written to this file
                delay(500.milliseconds)

                // If the file still exists, check for contents, if no contents, wait for modification instead of
                // immediately throwing an error
                if (file.exists()) {
                    if (file.toPath().fileSize() == 0L) {
                        watchSingleAgent(file, null)
                    } else
                        addAgentFromFile(directory.resolve(AGENT_FILE).toFile())
                }

                cancel()
            }
        }.invokeOnCompletion {
            logger.trace { "watcher for $AGENT_FILE in \"${normalizedPathString(directory)}\" stopped" }
        }
    }

    private fun watchForDeletion(directory: Path, restartPathPattern: String, restartPart: String) {
        if (!watch || directory.parent == null || deletionWatchers.contains(directory.absolutePathString() + ":" + restartPathPattern))
            return

        if (!directory.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(directory)}\"!" }
            return
        }

        val watcherKey = directory.absolutePathString() + ":" + restartPathPattern
        deletionWatchers.add(watcherKey)
        eventStreamForPath(directory.parent, ENTRY_DELETE, pattern = restartPathPattern) {
            if ((it.context() as Path).name == directory.name) {
                logger.trace { "${directory.name} in \"${normalizedPathString(directory.parent)}\" was deleted, restart $restartPathPattern with $restartPart" }

                deletionWatchers.remove(watcherKey)
                addAgentsFromPattern(restartPathPattern, restartPart)

                cancel()
            }
        }
    }

    private fun readAgent(agentFile: File) = UnresolvedRegistryAgent.resolveFromFile(agentFile)


    companion object {
        private val EXCLUDED_DIRECTORIES = setOf(
            "node_modules", "__pycache__", ".venv", "venv", "build", "target", "dist", ".gradle", ".cache"
        )
        private const val WATCH_CAP = 256
    }
}

private fun normalizedPathString(path: String) =
    if (isWindows()) {
        path.replace("\\", "/")
    } else {
        path
    }

private fun normalizedPathString(file: File) =
    normalizedPathString(file.absolutePath)

private fun normalizedPathString(path: Path) =
    normalizedPathString(path.toString())