package org.example

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import kotlin.test.*

class LocalScopingTest {
    private val server = LogoLanguageServer()
    private val service = server.textDocumentService as LogoTextDocumentService

    @Test
    fun testLocalVariableDefinition() {
        val uri = "file:///test.logo"
        val content = """
            to PROC1 :param1
              localmake "var1 10
              print :param1
              print :var1
            end
            
            to PROC2
              localmake "var1 20
              print :var1
            end
            
            make "var1 30
        """.trimIndent()
        
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, content)))
        
        // 1. Definition of :var1 in PROC1 (line 3)
        // Position of :var1 on line 3 (index 3) is (3, 8)
        val pos1 = Position(3, 8)
        val def1 = service.definition(DefinitionParams(TextDocumentIdentifier(uri), pos1)).get().left
        assertEquals(1, def1.size)
        assertEquals(1, def1[0].range.start.line, "Should point to localmake in PROC1")

        // 2. Definition of :var1 in PROC2 (line 8)
        val pos2 = Position(8, 8)
        val def2 = service.definition(DefinitionParams(TextDocumentIdentifier(uri), pos2)).get().left
        assertEquals(1, def2.size)
        assertEquals(7, def2[0].range.start.line, "Should point to localmake in PROC2")
        
        // 3. Definition of :var1 globally (line 11) - technically there is no usage after line 11, 
        // but if we put cursor on "var1 at line 11
        val pos3 = Position(11, 6)
        val def3 = service.definition(DefinitionParams(TextDocumentIdentifier(uri), pos3)).get().left
        // It should find the global definition at line 11
        assertTrue(def3.any { it.range.start.line == 11 }, "Should find global definition")
    }

    @Test
    fun testCompletionScoping() {
        val uri = "file:///test.logo"
        val content = """
            to PROC1 :param1
              localmake "var1 10
              
            end
            
            to PROC2 :param2
              
            end
        """.trimIndent()
        
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, content)))
        
        // Completion in PROC1
        val comp1 = service.completion(CompletionParams(TextDocumentIdentifier(uri), Position(2, 2))).get().left
        assertTrue(comp1.any { it.label == "param1" }, "Should suggest param1 in PROC1")
        assertTrue(comp1.any { it.label == "var1" }, "Should suggest var1 in PROC1")
        assertFalse(comp1.any { it.label == "param2" }, "Should NOT suggest param2 in PROC1")
        
        // Completion in PROC2
        val comp2 = service.completion(CompletionParams(TextDocumentIdentifier(uri), Position(6, 2))).get().left
        assertTrue(comp2.any { it.label == "param2" }, "Should suggest param2 in PROC2")
        assertFalse(comp2.any { it.label == "param1" }, "Should NOT suggest param1 in PROC2")
        assertFalse(comp2.any { it.label == "var1" }, "Should NOT suggest var1 in PROC2")
    }

    @Test
    fun testShadowing() {
        val uri = "file:///test.logo"
        val content = """
            make "x 100
            to PROC
              localmake "x 10
              print :x
            end
            print :x
        """.trimIndent()
        
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, content)))
        
        // Definition of :x inside PROC (line 3)
        val posInside = Position(3, 9)
        val defInside = service.definition(DefinitionParams(TextDocumentIdentifier(uri), posInside)).get().left
        assertEquals(1, defInside.size)
        assertEquals(2, defInside[0].range.start.line, "Inside PROC, :x should point to localmake")
        
        // Definition of :x outside PROC (line 5)
        val posOutside = Position(5, 7)
        val defOutside = service.definition(DefinitionParams(TextDocumentIdentifier(uri), posOutside)).get().left
        assertEquals(1, defOutside.size)
        assertEquals(0, defOutside[0].range.start.line, "Outside PROC, :x should point to global make")
    }

    @Test
    fun testLoopVariables() {
        val uri = "file:///test.logo"
        val content = """
            to LOOP_TEST
              for [i 1 5 1] [
                print :i
              ]
              dotimes [j 3] [
                print :j
              ]
            end
        """.trimIndent()
        
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, content)))
        
        // :i inside for loop (line 2)
        val posI = Position(2, 11)
        val defI = service.definition(DefinitionParams(TextDocumentIdentifier(uri), posI)).get().left
        assertEquals(1, defI.size)
        assertEquals(1, defI[0].range.start.line, "Should point to 'for' loop variable 'i'")
        
        // :j inside dotimes loop (line 5)
        val posJ = Position(5, 11)
        val defJ = service.definition(DefinitionParams(TextDocumentIdentifier(uri), posJ)).get().left
        assertEquals(1, defJ.size)
        assertEquals(4, defJ[0].range.start.line, "Should point to 'dotimes' loop variable 'j'")
    }
}
