# Logo Language Server

## Overview
This project is an implementation of a Language Server Protocol (LSP) for the Logo programming language, submitted as a test task for the internship program. The goal was to create a functional language server that enhances the Logo development experience with modern IDE features.

## Key Features
- **Semantic Highlighting**: Accurate coloring of keywords, variables, comments, and strings.
- **Go to Definition**: Navigate effortlessly between procedure calls/variable usages and their declarations.
- **Real-time Diagnostics**: Immediate feedback on syntax errors and basic logical issues.
- **Auto-completion**: Suggestions for Logo keywords and user-defined symbols.
- **Hover Support**: Quick info on symbols.

## Technical Approach

### 1. Parser Implementation (`LogoParser.kt`)
Instead of using a parser generator, I implemented a custom hand-written parser to have finer control over error recovery and tokenization. 
- **Scoping**: The parser handles both global and local scopes (using `localmake`). 
- **Resilience**: It's designed to continue parsing even after encountering syntax errors to provide as many diagnostics as possible.

### 2. LSP Integration
I used `lsp4j` to implement the protocol. The architecture is split into:
- `LogoLanguageServer`: Handles lifecycle and capability negotiation.
- `LogoTextDocumentService`: Manages document synchronization and specific features like definitions and highlighting.
- `LogoWorkspaceService`: Handles workspace-wide symbol searches.

### 3. Decisions & Trade-offs
- **Full Text Sync**: Currently uses `TextDocumentSyncKind.Full` for simplicity and reliability in state management.
## Setup & Running

### Prerequisites
- **JDK 11+**
- **Gradle** (Wrapper included)

### Build Jar
To create a standalone executable JAR (fat JAR) that contains all dependencies:
```bash
./gradlew jar
```
The resulting JAR will be located at `build/libs/LSP-1.0-SNAPSHOT.jar`.

### Adding to IntelliJ IDEA
To use this Language Server in IntelliJ IDEA (Ultimate Edition), follow these steps:

1.  **Build the JAR** as described above.
2.  Open **Settings** (`Ctrl+Alt+S` or `Cmd+,`).
3.  Navigate to **Languages & Frameworks > Language Server Protocol**.
4.  Click **+** (Add) to add a new server.
5.  Set **Binary** to `java`.
6.  Set **Arguments** to `-jar path/to/LSP-1.0-SNAPSHOT.jar`. Replace `path/to/` with the absolute path to your project's `build/libs/` folder.
7.  Add a **Feedback Log** if you want to see the communication.
8.  In the **File Types** section, add `logo-lang` (or the extension you are using, e.g., `.logo-lang`).
9.  Click **OK** and restart/reopen your Logo files.

*Note: For IntelliJ IDEA Community Edition, you might need a plugin like "LSP Support" to achieve similar functionality.*

### Run
The server communicates via `stdin/stdout`:
```bash
./gradlew run
```
