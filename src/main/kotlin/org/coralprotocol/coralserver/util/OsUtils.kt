package org.coralprotocol.coralserver.util

private val OS_NAME = System.getProperty("os.name", "").lowercase()

fun isWindows(): Boolean = OS_NAME.contains("windows")