package org.coralprotocol.coralserver.config

import java.nio.file.Path

data class CacheConfig(
    val root: Path = Path.of(System.getProperty("user.home"), ".coral"),
    val index: Path = root.resolve("index"),
    val agent: Path = root.resolve("agent")
)