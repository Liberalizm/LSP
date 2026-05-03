package org.example

import org.eclipse.lsp4j.*
import kotlin.test.*

class WorkspaceIntegratedTest {
    private val server = LogoLanguageServer()
    private val service = server.textDocumentService as LogoTextDocumentService

    @Test
    fun testCrossFileGoToDefinition() {
        val uri1 = "file:///file1.logo"
        val content1 = """
            to LIB_PROC :x
              print :x
            end
        """.trimIndent()

        val uri2 = "file:///file2.logo"
        val content2 = """
            to MAIN
              LIB_PROC 10
            end
        """.trimIndent()

        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri1, "logo", 1, content1)))
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri2, "logo", 1, content2)))

        // Position of LIB_PROC call in file2.logo (line 1, col 2)
        val pos = Position(1, 2)
        val definition = service.definition(DefinitionParams(TextDocumentIdentifier(uri2), pos)).get().left
        
        assertEquals(1, definition.size)
        assertEquals(uri1, definition[0].uri, "Should point to file1.logo")
        assertEquals(0, definition[0].range.start.line, "Should point to line 1 of file1.logo")
    }

    @Test
    fun testCrossFileScoping() {
        // Local variables in one file should NOT be visible in another file's global scope
        val uri1 = "file:///file1.logo"
        val content1 = """
            to PROC1
              localmake "localVar 10
            end
        """.trimIndent()

        val uri2 = "file:///file2.logo"
        val content2 = """
            print :localVar
        """.trimIndent()

        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri1, "logo", 1, content1)))
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri2, "logo", 1, content2)))

        // Go-to-definition for :localVar in file2
        val pos = Position(0, 8)
        val definition = service.definition(DefinitionParams(TextDocumentIdentifier(uri2), pos)).get().left
        
        assertTrue(definition.isEmpty(), "Local variable from file1 should not be visible in file2")
        
        // Completion in file2 should not include localVar
        val completion = service.completion(CompletionParams(TextDocumentIdentifier(uri2), Position(0, 0))).get().left
        assertFalse(completion.any { it.label == "localVar" }, "Local variable from file1 should not be in completion of file2")
    }
}
