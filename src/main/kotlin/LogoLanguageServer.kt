package org.example

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class LogoLanguageServer : LanguageServer, LanguageClientAware {
    private val textDocumentService = LogoTextDocumentService()
    private val workspaceService = LogoWorkspaceService(textDocumentService)
    private var client: LanguageClient? = null

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        textDocumentService.setRootUri(params.rootUri)

        val capabilities = ServerCapabilities()
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
        capabilities.setHoverProvider(true)
        capabilities.setDefinitionProvider(true)
        capabilities.setDeclarationProvider(true)
        capabilities.setReferencesProvider(true)
        capabilities.setDocumentSymbolProvider(true)
        capabilities.setImplementationProvider(true)
        capabilities.setTypeDefinitionProvider(true)
        capabilities.setWorkspaceSymbolProvider(true)
        capabilities.setRenameProvider(true)
        capabilities.setCompletionProvider(CompletionOptions(false, listOf(":", "\"")))
        
        val legend = SemanticTokensLegend(
            listOf(
                SemanticTokenTypes.Function,    // 0
                SemanticTokenTypes.Variable,    // 1
                SemanticTokenTypes.Keyword,     // 2
                SemanticTokenTypes.Comment,     // 3
                SemanticTokenTypes.String,      // 4
                SemanticTokenTypes.Number,      // 5
                SemanticTokenTypes.Parameter    // 6
            ),
            listOf()
        )
        capabilities.setSemanticTokensProvider(SemanticTokensWithRegistrationOptions(legend, true))

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun shutdown(): CompletableFuture<Any> {
        textDocumentService.dispose()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        System.exit(0)
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    override fun connect(client: LanguageClient) {
        this.client = client
        textDocumentService.setClient(client)
    }
}
