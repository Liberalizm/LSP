package org.example

import java.io.File
import java.net.URI

class DictionaryUpdater {
    fun updateDictionary(rootUri: String?, words: Set<String>) {
        /*
        if (rootUri == null) return

        try {
            val rootPath = File(URI(rootUri)).absolutePath
            val dictionaryDir = File(rootPath, ".idea/dictionaries")
            
            if (!dictionaryDir.exists()) {
                if (!dictionaryDir.mkdirs()) {
                    return
                }
            }

            val dictionaryFile = File(dictionaryDir, "logo.xml")
            
            // Filter words to only include simple alphanumeric strings (no dots, question marks for dictionary)
            // Actually, IntelliJ dictionaries can contain words with dots if they are treated as one word,
            // but usually it's better to split them.
            // For Logo, we want things like 'shown?' to be known. 
            // However, IntelliJ's spellchecker splits by non-alphanumeric.
            // So 'shown?' is seen as 'shown'. 
            // We should add the split parts to the dictionary.
            
            val dictionaryWords = mutableSetOf<String>()
            for (word in words) {
                // Split by non-alphanumeric characters (except underscore) and add parts
                val parts = word.lowercase().split(Regex("[^a-z0-9_]"))
                for (part in parts) {
                    if (part.length > 1) {
                        dictionaryWords.add(part)
                    }
                }
            }

            val sortedWords = dictionaryWords.sorted()
            
            val content = buildString {
                appendLine("<component name=\"ProjectDictionaryState\">")
                appendLine("  <dictionary name=\"logo\">")
                appendLine("    <words>")
                for (word in sortedWords) {
                    appendLine("      <w>$word</w>")
                }
                appendLine("    </words>")
                appendLine("  </dictionary>")
                appendLine("</component>")
            }

            // Only write if content changed to avoid unnecessary disk I/O and IDE reloads
            if (dictionaryFile.exists() && dictionaryFile.readText() == content) {
                return
            }

            dictionaryFile.writeText(content)
        } catch (e: Exception) {
            // Ignore errors in dictionary update to not break LSP functionality
        }
        */
    }
}
