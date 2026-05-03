package org.example

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentItem
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DictionaryUpdateTest {
    @Test
    fun testDictionaryUpdate() {
        val tempDir = Files.createTempDirectory("lsp-test").toFile()
        val ideaDir = File(tempDir, ".idea")
        ideaDir.mkdir()
        
        val server = LogoLanguageServer()
        val rootUri = tempDir.toURI().toString()
        
        // 1. Initialize
        val params = InitializeParams()
        params.rootUri = rootUri
        server.initialize(params).get()
        
        val dictionaryFile = File(tempDir, ".idea/dictionaries/logo.xml")
        
        // Wait for asynchronous update
        Thread.sleep(1500)
        
        assertFalse(dictionaryFile.exists(), "Dictionary file should NOT be created after initialization as it is disabled")
        
        // 2. Open document with new procedure
        val service = server.getTextDocumentService()
        val docUri = File(tempDir, "test.logo").toURI().toString()
        val content = "to MY_NEW_PROC\n  fd 10\n  do.while [ print \"hi ] :x < 5\n  show shown?\nend"
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(docUri, "logo", 1, content)))
        
        // Wait for asynchronous update
        Thread.sleep(1500)
        
        assertFalse(dictionaryFile.exists(), "Dictionary file should still not exist")
        
        // Cleanup
        tempDir.deleteRecursively()
    }
}
