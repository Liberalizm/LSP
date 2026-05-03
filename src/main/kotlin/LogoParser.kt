package org.example

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

data class LogoSymbol(
    val name: String,
    val kind: SymbolKind,
    val location: Location,
    val scope: String? = null // null means global, otherwise the name of the procedure
)

enum class SymbolKind {
    PROCEDURE, VARIABLE
}

data class ParseResult(
    val symbols: List<LogoSymbol>,
    val diagnostics: List<Diagnostic>
)

data class LogoToken(
    val line: Int,
    val column: Int,
    val length: Int,
    val type: Int // Index in the legend
)

class LogoParser {
    private val keywords = setOf(
        "to", "end", "make", "localmake", "name", "define",
        "forward", "fd", "back", "bk", "left", "lt", "right", "rt", "home",
        "setx", "sety", "setxy", "setpos", "setheading", "seth", "arc", "ellipse",
        "hideturtle", "ht", "showturtle", "st", "clean", "cs", "clearscreen",
        "pu", "pd", "penup", "pendown", "fill", "filled", "label", "setlabelheight",
        "wrap", "window", "fence",
        "repeat", "for", "if", "ifelse", "test", "iftrue", "iffalse", "wait",
        "dotimes", "do.while", "while", "do.until", "until",
        "sum", "minus", "random", "modulo", "power", "product", "quotient", "remainder",
        "abs", "round", "sqrt", "exp", "log", "ln", "sin", "cos", "tan", "arctan",
        "word?", "wordp", "list?", "listp", "array?", "arrayp", "number?", "numberp",
        "empty?", "emptyp", "equal?", "equalp", "notequal?", "notequalp",
        "before?", "beforep", "substring?", "substringp", "member?", "memberp",
        "window?", "windowp", "pendown?", "pendownp", "penup?", "penupp", "shown?", "shownp",
        "show", "print", "thing", "repcount", "first", "butfirst", "last", "butlast",
        "item", "pick", "list", "readword", "readlist", "pos", "xcor", "ycor",
        "heading", "towards", "labelsize", "pencolor", "pc",
        "pensize", "setcolor", "setpencolor", "setpc", "setwidth", "setpensize",
        "changeshape", "csh", "bye", "stop", "output", "word", "def", "bg", "setbg", "sc", "setscreencolor",
        "lowercase", "uppercase", "count", "ascii", "char",
        "run", "apply", "map", "filter", "foreach"
    )

    fun getKeywords(): Set<String> = keywords

    // Token types indices (must match LogoLanguageServer legend)
    // 0: Function, 1: Variable, 2: Keyword, 3: Comment, 4: String, 5: Number, 6: Parameter
    private val TYPE_FUNCTION = 0
    private val TYPE_VARIABLE = 1
    private val TYPE_KEYWORD = 2
    private val TYPE_COMMENT = 3
    private val TYPE_STRING = 4
    private val TYPE_NUMBER = 5
    private val TYPE_PARAMETER = 6

    fun parse(uri: String, text: String): ParseResult {
        val symbols = mutableListOf<LogoSymbol>()
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = text.lines()
        
        var insideProcedure = false
        var currentProcedure: String? = null
        var procedureStartLine = -1
        
        val bracketStack = mutableListOf<Pair<Int, Int>>() // line, col
        
        for ((lineIdx, line) in lines.withIndex()) {
            // Check for brackets
            for ((colIdx, char) in line.withIndex()) {
                if (char == '[') {
                    bracketStack.add(lineIdx to colIdx)
                } else if (char == ']') {
                    if (bracketStack.isEmpty()) {
                        val diagnostic = Diagnostic(
                            Range(Position(lineIdx, colIdx), Position(lineIdx, colIdx + 1)),
                            "Unexpected closing bracket ']'",
                            DiagnosticSeverity.Error,
                            "LogoLSP"
                        )
                        diagnostics.add(diagnostic)
                    } else {
                        bracketStack.removeAt(bracketStack.size - 1)
                    }
                }
            }

            // 1. Procedure definitions: to PROC_NAME
            val toMatch = Regex("""^\s*to\s+([a-zA-Z0-9_?.]+)""", RegexOption.IGNORE_CASE).find(line)
            if (toMatch != null) {
                if (insideProcedure) {
                    val diagnostic = Diagnostic(
                        Range(Position(lineIdx, toMatch.range.start), Position(lineIdx, toMatch.range.endInclusive + 1)),
                        "Nested procedure definitions are not allowed",
                        DiagnosticSeverity.Error,
                        "LogoLSP"
                    )
                    diagnostics.add(diagnostic)
                }
                insideProcedure = true
                val name = toMatch.groupValues[1]
                currentProcedure = name
                procedureStartLine = lineIdx
                
                val start = line.indexOf(name)
                symbols.add(LogoSymbol(name, SymbolKind.PROCEDURE, Location(uri, Range(Position(lineIdx, start), Position(lineIdx, start + name.length)))))
                
                // Also add parameters
                val paramsLine = line.substring(toMatch.range.endInclusive + 1)
                val paramMatch = Regex(""":([a-zA-Z0-9_?.]+)""").findAll(paramsLine)
                for (p in paramMatch) {
                    val pName = p.groupValues[1]
                    val pStart = toMatch.range.endInclusive + 1 + p.range.start + 1
                    symbols.add(LogoSymbol(pName, SymbolKind.VARIABLE, Location(uri, Range(Position(lineIdx, pStart), Position(lineIdx, pStart + pName.length))), currentProcedure))
                }
            }
            
            // End of procedure
            if (Regex("""^\s*end\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                if (!insideProcedure) {
                    val diagnostic = Diagnostic(
                        Range(Position(lineIdx, line.indexOf("end")), Position(lineIdx, line.indexOf("end") + 3)),
                        "Unexpected 'end' outside of procedure definition",
                        DiagnosticSeverity.Error,
                        "LogoLSP"
                    )
                    diagnostics.add(diagnostic)
                }
                insideProcedure = false
                currentProcedure = null
            }
            
            // 2. Variable definitions: make "VAR_NAME
            val makeMatch = Regex("""\b(?:make|localmake)\s+"([a-zA-Z0-9_?.]+)""", RegexOption.IGNORE_CASE).findAll(line)
            for (match in makeMatch) {
                val name = match.groupValues[1]
                val isLocal = match.value.lowercase().startsWith("localmake")
                val start = match.range.start + match.value.indexOf(name)
                symbols.add(LogoSymbol(name, SymbolKind.VARIABLE, Location(uri, Range(Position(lineIdx, start), Position(lineIdx, start + name.length))), if (isLocal) currentProcedure else null))
            }

            // 3. Variable definitions: name val "VAR_NAME
            val nameMatch = Regex("""\bname\s+.*?"([a-zA-Z0-9_?.]+)""", RegexOption.IGNORE_CASE).findAll(line)
            for (match in nameMatch) {
                val name = match.groupValues[1]
                val start = match.range.start + match.value.lastIndexOf(name)
                symbols.add(LogoSymbol(name, SymbolKind.VARIABLE, Location(uri, Range(Position(lineIdx, start), Position(lineIdx, start + name.length))), currentProcedure))
            }
            
            // 4. define "PROC_NAME [[args] [body]]
            val defineMatch = Regex("""\bdefine\s+"([a-zA-Z0-9_?.]+)\s+\[\s*\[(.*?)\]""", RegexOption.IGNORE_CASE).find(line)
            if (defineMatch != null) {
                val name = defineMatch.groupValues[1]
                val start = line.indexOf(name)
                symbols.add(LogoSymbol(name, SymbolKind.PROCEDURE, Location(uri, Range(Position(lineIdx, start), Position(lineIdx, start + name.length)))))
                
                val args = defineMatch.groupValues[2]
                val argMatch = Regex("""([a-zA-Z0-9_?.]+)""").findAll(args)
                for (a in argMatch) {
                    val aName = a.groupValues[1]
                    val aStart = line.indexOf(args) + a.range.start
                    symbols.add(LogoSymbol(aName, SymbolKind.VARIABLE, Location(uri, Range(Position(lineIdx, aStart), Position(lineIdx, aStart + aName.length))), name))
                }
            } else {
                // Fallback for simple define
                val simpleDefine = Regex("""\bdefine\s+"([a-zA-Z0-9_?.]+)""", RegexOption.IGNORE_CASE).find(line)
                if (simpleDefine != null) {
                    val name = simpleDefine.groupValues[1]
                    val start = line.indexOf(name)
                    symbols.add(LogoSymbol(name, SymbolKind.PROCEDURE, Location(uri, Range(Position(lineIdx, start), Position(lineIdx, start + name.length)))))
                }
            }
            
            // 5. for [i 1 5 1]
            val forMatch = Regex("""\bfor\s+\[\s*([a-zA-Z0-9_?.]+)""", RegexOption.IGNORE_CASE).findAll(line)
            for (match in forMatch) {
                val name = match.groupValues[1]
                val start = match.range.start + match.value.indexOf(name)
                symbols.add(LogoSymbol(name, SymbolKind.VARIABLE, Location(uri, Range(Position(lineIdx, start), Position(lineIdx, start + name.length))), currentProcedure))
            }

            // 6. dotimes [j 3]
            val dotimesMatch = Regex("""\bdotimes\s+\[\s*([a-zA-Z0-9_?.]+)""", RegexOption.IGNORE_CASE).findAll(line)
            for (match in dotimesMatch) {
                val name = match.groupValues[1]
                val start = match.range.start + match.value.indexOf(name)
                symbols.add(LogoSymbol(name, SymbolKind.VARIABLE, Location(uri, Range(Position(lineIdx, start), Position(lineIdx, start + name.length))), currentProcedure))
            }
        }
        
        // Final checks
        if (insideProcedure) {
            val diagnostic = Diagnostic(
                Range(Position(procedureStartLine, 0), Position(procedureStartLine, 10)),
                "Procedure definition not closed with 'end'",
                DiagnosticSeverity.Error,
                "LogoLSP"
            )
            diagnostics.add(diagnostic)
        }
        
        for (unmatched in bracketStack) {
            val diagnostic = Diagnostic(
                Range(Position(unmatched.first, unmatched.second), Position(unmatched.first, unmatched.second + 1)),
                "Unmatched opening bracket '['",
                DiagnosticSeverity.Error,
                "LogoLSP"
            )
            diagnostics.add(diagnostic)
        }
        
        return ParseResult(symbols, diagnostics)
    }

    fun tokenize(text: String): List<LogoToken> {
        val tokens = mutableListOf<LogoToken>()
        val lines = text.lines()
        
        for ((lineIdx, line) in lines.withIndex()) {
            var i = 0
            while (i < line.length) {
                if (line[i].isWhitespace()) {
                    i++
                    continue
                }
                
                // Comment
                if (line[i] == ';') {
                    tokens.add(LogoToken(lineIdx, i, line.length - i, TYPE_COMMENT))
                    break
                }
                
                // String literal: "word
                if (line[i] == '"') {
                    val start = i
                    i++
                    while (i < line.length && !line[i].isWhitespace() && line[i] != '[' && line[i] != ']' && line[i] != '(' && line[i] != ')') {
                        i++
                    }
                    tokens.add(LogoToken(lineIdx, start, i - start, TYPE_STRING))
                    continue
                }
                
                // Variable reference: :word
                if (line[i] == ':') {
                    val start = i
                    i++
                    while (i < line.length && !line[i].isWhitespace() && line[i] != '[' && line[i] != ']' && line[i] != '(' && line[i] != ')') {
                        i++
                    }
                    tokens.add(LogoToken(lineIdx, start, i - start, TYPE_VARIABLE))
                    continue
                }
                
                // Numbers
                if (line[i].isDigit() || (line[i] == '-' && i + 1 < line.length && line[i+1].isDigit())) {
                    val start = i
                    i++
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) {
                        i++
                    }
                    tokens.add(LogoToken(lineIdx, start, i - start, TYPE_NUMBER))
                    continue
                }

                // Identifiers (Keywords or Procedures)
                if (line[i].isLetter() || line[i] == '.' || line[i] == '?') {
                    val start = i
                    while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '?' || line[i] == '_')) {
                        i++
                    }
                    val word = line.substring(start, i).lowercase()
                    val type = if (keywords.contains(word)) {
                        TYPE_KEYWORD
                    } else {
                        TYPE_FUNCTION
                    }
                    tokens.add(LogoToken(lineIdx, start, i - start, type))
                    continue
                }
                
                i++
            }
        }
        return tokens
    }
}
