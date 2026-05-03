package org.example

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LogoTextDocumentService : TextDocumentService {
    private val documents = mutableMapOf<String, String>()
    private val symbols = mutableMapOf<String, List<LogoSymbol>>()
    private val parser = LogoParser()
    private val dictionaryUpdater = DictionaryUpdater()
    private var rootUri: String? = null
    private var client: LanguageClient? = null
    
    private val dictionaryExecutor = Executors.newSingleThreadScheduledExecutor()
    private var pendingUpdate: ScheduledFuture<*>? = null

    fun setRootUri(uri: String?) {
        this.rootUri = uri
        updateDictionary()
    }

    fun setClient(client: LanguageClient) {
        this.client = client
    }

    private fun updateDictionary() {
        val allWords = mutableSetOf<String>()
        allWords.addAll(parser.getKeywords())
        symbols.values.forEach { list ->
            list.forEach { allWords.add(it.name) }
        }
        val currentRootUri = rootUri

        pendingUpdate?.cancel(false)
        pendingUpdate = dictionaryExecutor.schedule({
            // dictionaryUpdater.updateDictionary(currentRootUri, allWords)
        }, 1, TimeUnit.SECONDS)
    }

    fun dispose() {
        dictionaryExecutor.shutdown()
        try {
            if (!dictionaryExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                dictionaryExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            dictionaryExecutor.shutdownNow()
        }
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val text = params.textDocument.text
        documents[uri] = text
        val parseResult = parser.parse(uri, text)
        symbols[uri] = parseResult.symbols
        publishDiagnostics(uri, parseResult.diagnostics)
        updateDictionary()
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        // For simplicity, we assume full sync (TextDocumentSyncKind.Full)
        val text = params.contentChanges[0].text
        documents[uri] = text
        val parseResult = parser.parse(uri, text)
        symbols[uri] = parseResult.symbols
        publishDiagnostics(uri, parseResult.diagnostics)
        updateDictionary()
    }

    private fun publishDiagnostics(uri: String, diagnostics: List<Diagnostic>) {
        client?.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = params.textDocument.uri
        documents.remove(uri)
        symbols.remove(uri)
        updateDictionary()
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        return findDefinition(params.textDocument.uri, params.position)
    }

    override fun declaration(params: DeclarationParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        return findDefinition(params.textDocument.uri, params.position)
    }

    override fun implementation(params: ImplementationParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        return findDefinition(params.textDocument.uri, params.position)
    }

    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        return findDefinition(params.textDocument.uri, params.position)
    }

    private fun findDefinition(uri: String, position: Position): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        val text = documents[uri] ?: return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))
        val lines = text.lines()
        val line = lines.getOrNull(position.line) ?: return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))
        
        // Find the word at the position
        val word = getWordAt(line, position.character) ?: return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))
        val searchName = if (word.startsWith(":") || word.startsWith("\"")) word.substring(1) else word
        
        // Determine current scope
        val currentScope = findScopeAt(uri, position)
        
        val results = mutableListOf<Location>()
        
        // 1. Search in current scope (if any)
        if (currentScope != null) {
            val fileSymbols = symbols[uri] ?: emptyList()
            for (symbol in fileSymbols) {
                if (symbol.name.equals(searchName, ignoreCase = true) && symbol.scope == currentScope) {
                    results.add(symbol.location)
                }
            }
        }
        
        // 2. If no local matches, search globally
        if (results.isEmpty()) {
            for (fileSymbols in symbols.values) {
                for (symbol in fileSymbols) {
                    if (symbol.name.equals(searchName, ignoreCase = true) && symbol.scope == null) {
                        results.add(symbol.location)
                    }
                }
            }
        }
        
        // 3. Last resort: search everywhere (including other scopes in THIS file if it's a unique match)
        if (results.isEmpty()) {
            val fileSymbols = symbols[uri] ?: emptyList()
            for (symbol in fileSymbols) {
                if (symbol.name.equals(searchName, ignoreCase = true)) {
                    results.add(symbol.location)
                }
            }
        }
        
        return CompletableFuture.completedFuture(Either.forLeft(results))
    }

    private fun findScopeAt(uri: String, position: Position): String? {
        val fileSymbols = symbols[uri] ?: return null
        return fileSymbols
            .filter { it.kind == SymbolKind.PROCEDURE }
            .find { it.location.range.start.line <= position.line && isInsideProcedure(uri, it, position) }
            ?.name
    }

    private fun isInsideProcedure(uri: String, procSymbol: LogoSymbol, position: Position): Boolean {
        val text = documents[uri] ?: return false
        val lines = text.lines()
        for (i in procSymbol.location.range.start.line until lines.size) {
            if (lines[i].trim().lowercase() == "end") {
                return position.line <= i
            }
        }
        return true // Fallback if no 'end' found
    }

    override fun references(params: ReferenceParams): CompletableFuture<MutableList<out Location>> {
        val uri = params.textDocument.uri
        val position = params.position
        val text = documents[uri] ?: return CompletableFuture.completedFuture(mutableListOf())
        
        val lineText = text.lines().getOrNull(position.line) ?: return CompletableFuture.completedFuture(mutableListOf())
        val word = getWordAt(lineText, position.character) ?: return CompletableFuture.completedFuture(mutableListOf())
        val searchName = if (word.startsWith(":") || word.startsWith("\"")) word.substring(1) else word
        
        val results = mutableListOf<Location>()
        val regex = Regex("(?:^|(?<=[\\s\\[\\]\\(\\);:\"]))" + Regex.escape(searchName) + "(?=[\\s\\[\\]\\(\\);:\"]|$)", RegexOption.IGNORE_CASE)
        for ((fileUri, fileText) in documents) {
            val lines = fileText.lines()
            for ((lineIdx, line) in lines.withIndex()) {
                val matches = regex.findAll(line)
                for (match in matches) {
                    results.add(Location(fileUri, Range(Position(lineIdx, match.range.start), Position(lineIdx, match.range.endInclusive + 1))))
                }
            }
        }
        
        return CompletableFuture.completedFuture(results)
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        val uri = params.textDocument.uri
        val fileSymbols = symbols[uri] ?: return CompletableFuture.completedFuture(emptyList())
        
        val results = fileSymbols.map { symbol ->
            val kind = if (symbol.kind == org.example.SymbolKind.PROCEDURE) org.eclipse.lsp4j.SymbolKind.Function else org.eclipse.lsp4j.SymbolKind.Variable
            val documentSymbol = DocumentSymbol(symbol.name, kind, symbol.location.range, symbol.location.range)
            Either.forRight<SymbolInformation, DocumentSymbol>(documentSymbol)
        }
        
        return CompletableFuture.completedFuture(results)
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        val uri = params.textDocument.uri
        val position = params.position
        val newName = params.newName
        
        val text = documents[uri] ?: return CompletableFuture.completedFuture(WorkspaceEdit())
        val lineText = text.lines().getOrNull(position.line) ?: return CompletableFuture.completedFuture(WorkspaceEdit())
        val word = getWordAt(lineText, position.character) ?: return CompletableFuture.completedFuture(WorkspaceEdit())
        val searchName = if (word.startsWith(":") || word.startsWith("\"")) word.substring(1) else word
        
        val changes = mutableMapOf<String, List<TextEdit>>()
        val regex = Regex("(?:^|(?<=[\\s\\[\\]\\(\\);:\"]))" + Regex.escape(searchName) + "(?=[\\s\\[\\]\\(\\);:\"]|$)", RegexOption.IGNORE_CASE)
        
        for ((fileUri, fileText) in documents) {
            val edits = mutableListOf<TextEdit>()
            val lines = fileText.lines()
            for ((lineIdx, line) in lines.withIndex()) {
                val matches = regex.findAll(line)
                for (match in matches) {
                    edits.add(TextEdit(Range(Position(lineIdx, match.range.start), Position(lineIdx, match.range.endInclusive + 1)), newName))
                }
            }
            if (edits.isNotEmpty()) {
                changes[fileUri] = edits
            }
        }
        
        return CompletableFuture.completedFuture(WorkspaceEdit(changes))
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val uri = params.textDocument.uri
        val position = params.position
        val currentScope = findScopeAt(uri, position)
        
        val results = mutableListOf<CompletionItem>()
        
        // Add keywords
        for (keyword in parser.getKeywords()) {
            val item = CompletionItem(keyword)
            item.kind = CompletionItemKind.Keyword
            item.detail = "Built-in Logo command"
            results.add(item)
        }
        
        // Add defined symbols
        for ((fileUri, fileSymbols) in symbols) {
            for (symbol in fileSymbols) {
                // Only suggest if global or in current scope
                if (symbol.scope == null || (fileUri == uri && symbol.scope == currentScope)) {
                    val item = CompletionItem(symbol.name)
                    item.kind = if (symbol.kind == org.example.SymbolKind.PROCEDURE) CompletionItemKind.Function else CompletionItemKind.Variable
                    val scopeDetail = if (symbol.scope != null) " (Local)" else " (Global)"
                    item.detail = (if (symbol.kind == org.example.SymbolKind.PROCEDURE) "Logo procedure" else "Logo variable") + scopeDetail
                    results.add(item)
                }
            }
        }
        
        return CompletableFuture.completedFuture(Either.forLeft(results.distinctBy { it.label }))
    }

    fun getAllSymbols(): List<LogoSymbol> {
        return symbols.values.flatten()
    }

    private fun getWordAt(line: String, character: Int): String? {
        if (character < 0 || character > line.length) return null
        
        val stopChars = setOf(' ', '\t', '[', ']', '(', ')', ';')
        
        var start = if (character == line.length) character - 1 else character
        if (start < 0) return null
        if (stopChars.contains(line[start]) && character > 0 && !stopChars.contains(line[character - 1])) {
            start = character - 1
        }
        
        while (start > 0 && !stopChars.contains(line[start - 1])) {
            start--
        }
        
        var end = if (character == line.length) character else character
        while (end < line.length && !stopChars.contains(line[end])) {
            end++
        }
        
        if (start >= end) return null
        return line.substring(start, end)
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        val uri = params.textDocument.uri
        val position = params.position
        val text = documents[uri] ?: return CompletableFuture.completedFuture(null)
        val lineText = text.lines().getOrNull(position.line) ?: return CompletableFuture.completedFuture(null)
        val word = getWordAt(lineText, position.character) ?: return CompletableFuture.completedFuture(null)
        val searchName = if (word.startsWith(":") || word.startsWith("\"")) word.substring(1) else word

        val contents = MarkupContent()
        contents.kind = MarkupKind.MARKDOWN
        
        if (parser.getKeywords().contains(searchName.lowercase())) {
            contents.value = "**$searchName**\n\nBuilt-in Logo command."
        } else {
            val currentScope = findScopeAt(uri, position)
            val defined = symbols[uri]?.find { it.name.equals(searchName, ignoreCase = true) && it.scope == currentScope }
                ?: symbols.values.flatten().find { it.name.equals(searchName, ignoreCase = true) && it.scope == null }
                ?: symbols.values.flatten().find { it.name.equals(searchName, ignoreCase = true) }

            if (defined != null) {
                val kindStr = if (defined.kind == SymbolKind.PROCEDURE) "Procedure" else "Variable"
                val scopeStr = if (defined.scope != null) " (Local to ${defined.scope})" else " (Global)"
                contents.value = "**$searchName** ($kindStr)$scopeStr\n\nDefined in ${defined.location.uri}"
            } else {
                return CompletableFuture.completedFuture(null)
            }
        }
        
        return CompletableFuture.completedFuture(Hover(contents))
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val uri = params.textDocument.uri
        val text = documents[uri] ?: return CompletableFuture.completedFuture(SemanticTokens(listOf()))
        
        val logoTokens = parser.tokenize(text)
        val data = mutableListOf<Int>()
        
        var lastLine = 0
        var lastStartChar = 0
        
        for (token in logoTokens) {
            val deltaLine = token.line - lastLine
            val deltaStartChar = if (deltaLine == 0) token.column - lastStartChar else token.column
            
            data.add(deltaLine)
            data.add(deltaStartChar)
            data.add(token.length)
            data.add(token.type)
            data.add(0) // tokenModifiers
            
            lastLine = token.line
            lastStartChar = token.column
        }
        
        return CompletableFuture.completedFuture(SemanticTokens(data))
    }
}
