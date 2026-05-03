package org.example

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import kotlin.test.*

class LogoTextDocumentServiceTest {
    private val service = LogoTextDocumentService()

    @Test
    fun testSemanticTokensEncoding() {
        val uri = "file:///test.logo"
        val text = "fd 10\nrt 20"
        
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, text)))
        
        val params = SemanticTokensParams(TextDocumentIdentifier(uri))
        val tokens = service.semanticTokensFull(params).get()
        val data = tokens.data
        
        // "fd" (line 0, col 0, len 2, type KEYWORD=2)
        // deltaLine: 0, deltaStartChar: 0, length: 2, type: 2, mod: 0
        assertEquals(0, data[0])
        assertEquals(0, data[1])
        assertEquals(2, data[2])
        assertEquals(2, data[3])
        
        // "10" (line 0, col 3, len 2, type NUMBER=5)
        // deltaLine: 0, deltaStartChar: 3, length: 2, type: 5, mod: 0
        assertEquals(0, data[5])
        assertEquals(3, data[6])
        assertEquals(2, data[7])
        assertEquals(5, data[8])
        
        // "rt" (line 1, col 0, len 2, type KEYWORD=2)
        // deltaLine: 1, deltaStartChar: 0, length: 2, type: 2, mod: 0
        assertEquals(1, data[10])
        assertEquals(0, data[11])
        assertEquals(2, data[12])
        assertEquals(2, data[13])
    }
}
