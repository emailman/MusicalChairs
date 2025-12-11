# Musical Chairs (Kotlin/Compose Wasm)

## Setup
This project is configured as a Kotlin Multiplatform project targeting WasmJS with Compose Multiplatform.

### Prerequisites
- JDK 11 or higher
- IntelliJ IDEA (recommended for Kotlin development)

### Running the project
1. Open this directory in IntelliJ IDEA.
2. Allow the project to sync (it may take a moment to download dependencies).
3. Open the Gradle tool window.
4. Run the task: `compose web` -> `wasmJsBrowserRun`.

Alternatively, if you have Gradle installed globally:
```bash
gradle wasmJsBrowserRun
```

### Project Structure
- `src/wasmJsMain/kotlin`: Kotlin source code
- `src/wasmJsMain/resources`: Static resources (index.html)
