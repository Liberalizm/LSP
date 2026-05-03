package org.example

import kotlin.test.*

class LogoParserTest {
    private val parser = LogoParser()

    @Test
    fun testTokenizeSimple() {
        val text = "to STAR :n\n  repeat 5 [ fd :n rt 144 ]\nend"
        val tokens = parser.tokenize(text)
        
        // "to" - keyword
        // "STAR" - function (definition name)
        // ":n" - variable
        // "repeat" - keyword
        // "5" - number
        // "fd" - keyword
        // ":n" - variable
        // "rt" - keyword
        // "144" - number
        // "end" - keyword
        
        val keywordTokens = tokens.filter { it.type == 2 } // TYPE_KEYWORD
        val functionTokens = tokens.filter { it.type == 0 } // TYPE_FUNCTION
        val variableTokens = tokens.filter { it.type == 1 } // TYPE_VARIABLE
        val numberTokens = tokens.filter { it.type == 5 } // TYPE_NUMBER

        assertTrue(keywordTokens.any { text.substring(getOffset(text, it.line, it.column), getOffset(text, it.line, it.column) + it.length).lowercase() == "to" })
        assertTrue(keywordTokens.any { text.substring(getOffset(text, it.line, it.column), getOffset(text, it.line, it.column) + it.length).lowercase() == "repeat" })
        assertTrue(keywordTokens.any { text.substring(getOffset(text, it.line, it.column), getOffset(text, it.line, it.column) + it.length).lowercase() == "fd" })
        assertTrue(keywordTokens.any { text.substring(getOffset(text, it.line, it.column), getOffset(text, it.line, it.column) + it.length).lowercase() == "rt" })
        assertTrue(keywordTokens.any { text.substring(getOffset(text, it.line, it.column), getOffset(text, it.line, it.column) + it.length).lowercase() == "end" })

        assertTrue(functionTokens.any { text.substring(getOffset(text, it.line, it.column), getOffset(text, it.line, it.column) + it.length) == "STAR" })
        assertEquals(2, variableTokens.size) // :n twice
        assertEquals(2, numberTokens.size) // 5 and 144
    }

    private fun getOffset(text: String, line: Int, col: Int): Int {
        val lines = text.lines()
        var offset = 0
        for (i in 0 until line) {
            offset += lines[i].length + 1 // +1 for newline
        }
        return offset + col
    }

    @Test
    fun testParseSymbols() {
        val uri = "file:///test.logo"
        val text = "to MYPROC :arg\n  make \"var1 10\nend"
        val result = parser.parse(uri, text)
        val symbols = result.symbols
        
        val procedureSymbols = symbols.filter { it.kind == SymbolKind.PROCEDURE }
        val variableSymbols = symbols.filter { it.kind == SymbolKind.VARIABLE }
        
        assertEquals(1, procedureSymbols.size)
        assertEquals("MYPROC", procedureSymbols[0].name)
        
        assertTrue(variableSymbols.any { it.name == "arg" })
        assertTrue(variableSymbols.any { it.name == "var1" })
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun testDiagnosticsUnmatchedEnd() {
        val uri = "file:///test.logo"
        val text = "end"
        val result = parser.parse(uri, text)
        assertEquals(1, result.diagnostics.size)
        assertTrue(result.diagnostics[0].message.contains("Unexpected 'end'"))
    }

    @Test
    fun testDiagnosticsUnclosedProcedure() {
        val uri = "file:///test.logo"
        val text = "to MYPROC"
        val result = parser.parse(uri, text)
        assertEquals(1, result.diagnostics.size)
        assertTrue(result.diagnostics[0].message.contains("not closed with 'end'"))
    }

    @Test
    fun testDiagnosticsUnmatchedBrackets() {
        val uri = "file:///test.logo"
        val text = "repeat 5 [ fd 10"
        val result = parser.parse(uri, text)
        assertEquals(1, result.diagnostics.size)
        assertTrue(result.diagnostics[0].message.contains("Unmatched opening bracket"))
        
        val text2 = "]"
        val result2 = parser.parse(uri, text2)
        assertEquals(1, result2.diagnostics.size)
        assertTrue(result2.diagnostics[0].message.contains("Unexpected closing bracket"))
    }

    @Test
    fun testDiagnosticsNestedProcedures() {
        val uri = "file:///test.logo"
        val text = """
            to OUTER
              to INNER
              end
            end
        """.trimIndent()
        val result = parser.parse(uri, text)
        // Should have 1 diagnostic for nested 'to'
        assertTrue(result.diagnostics.any { it.message.contains("Nested procedure") })
    }
}
