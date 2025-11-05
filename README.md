# Jcmdshell (beta-release)

A cross-platform command-line shell built in **pure Java**, designed around the **WORE** principle  
**(Write Once, Run Everywhere).**

> **Note:** Jcmdshell is **not** affiliated with Oracle or the Java® trademark.

---

## Features
- Works on **Windows, Linux, macOS, and Windows on ARM**
- Uses **JLine** for modern terminal input and editing
- Can be built **with or without Maven**
- Supports both **standard JAR** and **fat JAR** builds
- No native dependencies

---

## Requirements
- **JDK 25** or newer
- **Maven** (optional, but recommended)

---

## Building with Maven

Maven automatically downloads dependencies and produces **two JAR files**:

| Output                     | Description                                               |
|----------------------------|-----------------------------------------------------------|
| `target/Jcmdshell.jar`     | Standard JAR — requires a `lib/` folder with dependencies |
| `target/Jcmdshell-fat.jar` | Fat JAR — includes JLine and runs anywhere                |

Build command:
```sh
mvn clean package
```
`clean` is optional, but recommended when a `target/` directory already exists.

---

## Building Manually (Without Maven)

1. Download **JLine 3.30.6**  
   https://mvnrepository.com/artifact/org.jline/jline/3.30.6

2. Create a `lib/` directory and place the downloaded JAR inside:
```sh
mkdir lib
```

> Using a `lib/` folder is recommended, but optional if you provide a manifest with the classpath.  
> The following manual build uses the `lib/` setup.

---

## Manual Compilation

### Windows - PowerShell
```powershell
javac -cp "lib/*" -d target (Get-ChildItem -Recurse -Filter *.java src/main/java/xyz/stackpancakes).FullName
```

### Windows - Batch
```batch
dir /B /S "src\main\java\xyz\stackpancakes\*.java" > java_files.txt
javac -cp "lib\*" -d target @java_files.txt
del java_files.txt
```

### *NIX - Shell
```sh
javac -cp "lib/*" -d target $(find src/main/java/xyz/stackpancakes -name "*.java")
```
`target/` act as compilation output directories.
> On Unix-like systems, a POSIX-compatible `sh` is assumed to be available.

---

# Packaging a Standard JAR

Your project already includes a `MANIFEST.MF` with:

```
Manifest-Version: 1.0
Created-By: StackPancakes
Main-Class: xyz.stackpancakes.Main
Class-Path: lib/jline-3.30.6.jar
Sealed: true
Specification-Title: Jcmdshell
Specification-Version: 0.1.0
Specification-Vendor: StackPancakes
Implementation-Title: Jcmdshell
Implementation-Version: 0.1.0
Implementation-Vendor: StackPancakes

```

Because of this, creating a new manifest file is **optional**.  
You may reuse the existing one when packaging.

## Build the JAR (example places the JAR in `target/`)

```sh
jar cfm target/Jcmdshell.jar MANIFEST.MF -C target .
```

If your `lib/` directory is not next to the JAR, update **Class-Path** accordingly in your existing `MANIFEST.MF`.

---

# Fat JAR (Manual, No Maven)

A **fat JAR** is created by **unpacking dependency JARs** and **combining their class files** with your compiled classes into **one** runnable JAR.
> Do **not** just place dependency JARs inside your JAR - Java won’t load nested JARs automatically.

### 1) Compile (same as above)
```powershell
# PowerShell
javac -cp "lib/*" -d target (Get-ChildItem -Recurse -Filter *.java src/main/java/xyz/stackpancakes).FullName
```
```sh
# *NIX shell
javac -cp "lib/*" -d target $(find src/main/java/xyz/stackpancakes -name "*.java")
```

### 2) Create a staging directory
```powershell
mkdir fatbuild
```
```sh
mkdir -p fatbuild
```

### 3) Copy your compiled classes/resources into the staging dir
```powershell
Copy-Item -Recurse target\* fatbuild\
```
```sh
cp -R target/* fatbuild/
```

### 4) Unpack dependencies into the staging dir
> Use `jar xf` **inside** the staging directory to extract the dependency.
```powershell
Push-Location fatbuild
jar xf ..\lib\jline-3.30.6.jar
Pop-Location
```
```sh
cd fatbuild && jar xf ../lib/jline-3.30.6.jar
```

Now `fatbuild/` contains:
```
xyz/stackpancakes/...     # your classes
org/jline/...             # dependency classes
META-INF/...              # manifests, services, signatures from deps
```

### 5) Clean META-INF signatures (avoid signature errors)
PowerShell:
```powershell
Remove-Item fatbuild\META-INF\*.SF -ErrorAction Ignore
Remove-Item fatbuild\META-INF\*.RSA -ErrorAction Ignore
Remove-Item fatbuild\META-INF\*.DSA -ErrorAction Ignore
```
Batch:
```batch
del /Q fatbuild\META-INF\*.SF 2>nul
del /Q fatbuild\META-INF\*.RSA 2>nul
del /Q fatbuild\META-INF\*.DSA 2>nul
```
*NIX:
```sh
rm -f fatbuild/META-INF/*.SF fatbuild/META-INF/*.RSA fatbuild/META-INF/*.DSA
```

> If `META-INF/services` collisions occur across multiple deps, merge or remove those files as needed.

### 6) Create a minimal runnable manifest (no Class-Path needed)
Create **`MANIFEST.MF`** with:
```
Manifest-Version: 1.0
Main-Class: xyz.stackpancakes.Main

```
*(Keep the final blank line.)*

### 7) Build the fat JAR from the staging dir
```sh
jar cfm target/Jcmdshell-fat.jar MANIFEST.MF -C fatbuild .
```

### 8) Run it
Batch / Powershell:
```powershell
java --enable-native-access=ALL-UNNAMED -jar target\Jcmdshell-fat.jar
```
*NIX:
```sh
java --enable-native-access=ALL-UNNAMED -jar target/Jcmdshell-fat.jar
```

**Notes**
- Keep your own `META-INF/MANIFEST.MF`; do not delete `META-INF` entirely.
- Remove signature files (`*.SF`, `*.RSA`, `*.DSA`) from unpacked dependencies.
- For many dependencies or complex `META-INF/services` merges, consider using **Maven Shade**.

---

## Running Jcmdshell

### Standard JAR (requires `lib/` on the classpath)
*NIX:
```sh
java --enable-native-access=ALL-UNNAMED -cp "target/Jcmdshell.jar:lib/*" xyz.stackpancakes.Main
```
Windows:
```sh
java --enable-native-access=ALL-UNNAMED -cp "target/Jcmdshell.jar;lib/*" xyz.stackpancakes.Main
```

### Fat JAR (no `lib/` required)
```sh
java --enable-native-access=ALL-UNNAMED -jar target/Jcmdshell-fat.jar
```

---

## Project Structure
```
Jcmdshell/
 ├─ src/
 │   └─ main/java/xyz/stackpancakes/...
 ├─ lib/                 # only needed for manual builds
 ├─ target/              # Maven outputs .jar here
 ├─ MANIFEST.MF          # used when packaging
 └─ README.md
```

---

## Contributing
Pull requests and suggestions are welcome.
