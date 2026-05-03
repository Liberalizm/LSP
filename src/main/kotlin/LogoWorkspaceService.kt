package org.example

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class LogoWorkspaceService(private val textDocumentService: LogoTextDocumentService) : WorkspaceService {
    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<out SymbolInformation>, List<out WorkspaceSymbol>>> {
        val query = params.query
        val allSymbols = textDocumentService.getAllSymbols()
        val filtered = allSymbols.filter { it.name.contains(query, ignoreCase = true) }
        val results = filtered.map { symbol ->
            val kind = if (symbol.kind == org.example.SymbolKind.PROCEDURE) org.eclipse.lsp4j.SymbolKind.Function else org.eclipse.lsp4j.SymbolKind.Variable
            SymbolInformation(symbol.name, kind, symbol.location)
        }
        return CompletableFuture.completedFuture(Either.forLeft(results))
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
    }
}
