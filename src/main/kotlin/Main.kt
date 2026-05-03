package org.example

import org.eclipse.lsp4j.launch.LSPLauncher
import java.util.logging.LogManager
import java.util.logging.Logger

fun main() {
    // Disable logging to avoid interference with stdout
    LogManager.getLogManager().reset()
    val logger = Logger.getLogger("LogoLSP")

    val server = LogoLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.`out`)

    server.connect(launcher.remoteProxy)
    launcher.startListening().get()
}