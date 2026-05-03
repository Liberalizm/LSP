package org.example

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.test.*

class FullLspTest {
    private val server = LogoLanguageServer()
    private val service = server.textDocumentService

    @Test
    fun testUserReportedSnippets() {
        val uri = "file:///test.logo"
        val content = """
            show pendown?
            show pendownp
            
            show word "world
        """.trimIndent()
        
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, content)))
        
        val tokens = service.semanticTokensFull(SemanticTokensParams(TextDocumentIdentifier(uri))).get()
        assertNotNull(tokens)
        
        // Let's analyze the tokens
        val data = tokens.data
        // Token indices: 0: Function, 1: Variable, 2: Keyword, 3: Comment, 4: String, 5: Number
        
        // show (Keyword - 2)
        // pendown? (Keyword - 2, after my change)
        // show (Keyword - 2)
        // pendownp (Keyword - 2, after my change)
        // show (Keyword - 2)
        // word (Keyword - 2)
        // "world (String - 4)
        
        println("[DEBUG_LOG] Tokens for snippet:")
        var currentLine = 0
        var currentChar = 0
        val tokenTypes = mutableListOf<Int>()
        for (i in data.indices step 5) {
            val deltaLine = data[i]
            val deltaChar = data[i+1]
            val length = data[i+2]
            val type = data[i+3]
            
            currentLine += deltaLine
            if (deltaLine > 0) currentChar = deltaChar else currentChar += deltaChar
            
            tokenTypes.add(type)
            println("[DEBUG_LOG] Line $currentLine, Char $currentChar, Length $length, Type $type")
        }
        
        // Expected types: 2 (Keyword) for all commands, 4 (String) for "world
        // show, pendown?, show, pendownp, show, word, "world
        assertEquals(2, tokenTypes[0], "show should be keyword")
        assertEquals(2, tokenTypes[1], "pendown? should be keyword")
        assertEquals(2, tokenTypes[2], "show should be keyword")
        assertEquals(2, tokenTypes[3], "pendownp should be keyword")
        assertEquals(2, tokenTypes[4], "show should be keyword")
        assertEquals(2, tokenTypes[5], "word should be keyword")
        assertEquals(4, tokenTypes[6], "\"world should be string")
    }

    @Test
    fun verifyLogoCodeFile() {
        val file = File("LOGO_code.logo-lang")
        val content = file.readText()
        val uri = file.toURI().toString()
        
        // Mock client to receive diagnostics
        val diagnostics = mutableListOf<Diagnostic>()
        val client = object : LanguageClient {
            override fun publishDiagnostics(params: PublishDiagnosticsParams) {
                diagnostics.addAll(params.diagnostics)
            }
            override fun telemetryEvent(`object`: Any?) {}
            override fun logMessage(message: MessageParams?) {}
            override fun showMessage(message: MessageParams?) {}
            override fun showMessageRequest(params: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> = CompletableFuture.completedFuture(null)
        }
        (service as LogoTextDocumentService).setClient(client)

        // 1. Open document
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, content)))
        
        // Check for diagnostics on valid file
        assertTrue(diagnostics.isEmpty(), "Valid file should have no diagnostics, but found: ${diagnostics.map { it.message }}")

        // 2. Check Semantic Tokens (Syntax Highlighting)
        val tokens = service.semanticTokensFull(SemanticTokensParams(TextDocumentIdentifier(uri))).get()
        assertNotNull(tokens)
        assertTrue(tokens.data.isNotEmpty(), "Should return semantic tokens")
        println("[DEBUG_LOG] Received ${tokens.data.size / 5} semantic tokens")

        // 3. Check Go-to-Declaration (Navigation)
        // Line 180: TEST_VARIABLES
        // Find line index for TEST_VARIABLES call (usually at the end)
        val lines = content.lines()
        val lineIdx = lines.indexOfFirst { it.trim() == "TEST_VARIABLES" }
        assertTrue(lineIdx > 0, "Could not find TEST_VARIABLES call in file")
        
        // Position on TEST_VARIABLES
        val pos = Position(lineIdx, 0)
        val definition = service.definition(DefinitionParams(TextDocumentIdentifier(uri), pos)).get()
        
        assertNotNull(definition)
        val locations = definition.left
        assertTrue(locations.isNotEmpty(), "Should find definition for TEST_VARIABLES")
        
        // The definition should be at line 4 (index 4 is 'to TEST_VARIABLES')
        val defLocation = locations[0]
        assertEquals(4, defLocation.range.start.line, "Definition should be on line 5 (index 4)")
        println("[DEBUG_LOG] Successfully found definition for TEST_VARIABLES at line ${defLocation.range.start.line + 1}")

        // 4. Check Declaration
        val declaration = service.declaration(DeclarationParams(TextDocumentIdentifier(uri), pos)).get()
        assertNotNull(declaration)
        assertTrue(declaration.left.isNotEmpty(), "Should find declaration")
        assertEquals(4, declaration.left[0].range.start.line)

        // 5. Check References
        val references = service.references(ReferenceParams(TextDocumentIdentifier(uri), pos, ReferenceContext(true))).get()
        assertNotNull(references)
        // TEST_VARIABLES is defined on line 5 (index 4) and called on line 180 (index 179)
        assertTrue(references.size >= 2, "Should find at least 2 references (definition + call), found ${references.size}")
        
        // 6. Check Document Symbols
        val symbols = service.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri))).get()
        assertNotNull(symbols)
        assertTrue(symbols.isNotEmpty(), "Should find document symbols")
        val testVariablesSymbol = symbols.find { it.isRight && it.right.name == "TEST_VARIABLES" }
        assertNotNull(testVariablesSymbol, "Should find TEST_VARIABLES in document symbols")

        // 7. Check Workspace Symbols
        val workspaceSymbols = server.workspaceService.symbol(WorkspaceSymbolParams("TEST_VARIABLES")).get()
        assertNotNull(workspaceSymbols)
        val wsSymbol = if (workspaceSymbols.isLeft) workspaceSymbols.left.find { it.name == "TEST_VARIABLES" } else null
        assertNotNull(wsSymbol, "Should find TEST_VARIABLES in workspace symbols")

        // 8. Check Rename
        val newName = "RENAMED_VAR"
        val edit = service.rename(RenameParams(TextDocumentIdentifier(uri), pos, newName)).get()
        assertNotNull(edit)
        assertTrue(edit.changes.containsKey(uri), "Should have changes for the file")
        val fileEdits = edit.changes[uri]!!
        assertTrue(fileEdits.any { it.newText == newName }, "Should contain the new name in edits")
        
        // 9. Check Hover
        val hover = service.hover(HoverParams(TextDocumentIdentifier(uri), pos)).get()
        assertNotNull(hover)
        assertTrue(hover.contents.isRight)
        assertTrue(hover.contents.right.value.contains("TEST_VARIABLES"))
        
        // 10. Check Completion
        val completion = service.completion(CompletionParams(TextDocumentIdentifier(uri), pos)).get()
        assertNotNull(completion)
        val items = completion.left
        assertTrue(items.any { it.label == "fd" }, "Completion should include 'fd' keyword")
        assertTrue(items.any { it.label == "TEST_VARIABLES" }, "Completion should include 'TEST_VARIABLES' procedure")

        println("[DEBUG_LOG] Successfully verified all navigation, hover and completion features")
    }

    @Test
    fun testDiagnosticCycle() {
        val uri = "file:///diagnostic.logo"
        val brokenContent = "to BROKEN" // Missing 'end'
        
        val diagnostics = mutableListOf<Diagnostic>()
        val client = object : LanguageClient {
            override fun publishDiagnostics(params: PublishDiagnosticsParams) {
                diagnostics.clear()
                diagnostics.addAll(params.diagnostics)
            }
            override fun telemetryEvent(`object`: Any?) {}
            override fun logMessage(message: MessageParams?) {}
            override fun showMessage(message: MessageParams?) {}
            override fun showMessageRequest(params: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> = CompletableFuture.completedFuture(null)
        }
        (service as LogoTextDocumentService).setClient(client)

        // 1. Open broken document
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, brokenContent)))
        assertEquals(1, diagnostics.size, "Should have 1 diagnostic for unclosed procedure")
        assertTrue(diagnostics[0].message.contains("not closed with 'end'"))

        // 2. Fix the document
        val fixedContent = "to BROKEN\nend"
        service.didChange(DidChangeTextDocumentParams(VersionedTextDocumentIdentifier(uri, 2), listOf(TextDocumentContentChangeEvent(fixedContent))))
        assertTrue(diagnostics.isEmpty(), "Diagnostics should be cleared after fix")
    }
}
