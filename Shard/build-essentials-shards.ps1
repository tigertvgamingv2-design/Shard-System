param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenExecutable
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$originalJavaHome = $env:JAVA_HOME
if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
}
$javacCommand = Get-Command javac -ErrorAction SilentlyContinue
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue
$javac = if ($JavaHome) { Join-Path $JavaHome "bin\javac.exe" } elseif ($javacCommand) { $javacCommand.Source } else { throw "Set JAVA_HOME to a JDK 25 installation or add javac to PATH." }
$jar = if ($JavaHome) { Join-Path $JavaHome "bin\jar.exe" } elseif ($jarCommand) { $jarCommand.Source } else { throw "Set JAVA_HOME to a JDK 25 installation or add jar to PATH." }
if ($MavenExecutable) {
    $mavenCommand = Get-Command $MavenExecutable -ErrorAction Stop
    $maven = $mavenCommand.Source
} else {
    $mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mavenCommand) {
        throw "Maven is not on PATH. Install Maven or pass -MavenExecutable with its command path."
    }
    $maven = $mavenCommand.Source
}
$mavenWorkingDirectory = Split-Path -Parent $maven
$outputFolder = Join-Path $root "target\essentials-shards"
$classpathFile = Join-Path $env:TEMP ("shard-essentials-classpath-" + [Guid]::NewGuid().ToString("N") + ".txt")
$buildFolder = Join-Path $env:TEMP ("shard-essentials-build-" + [Guid]::NewGuid().ToString("N"))

try {
    if ($mavenWorkingDirectory) {
        Push-Location $mavenWorkingDirectory
    }
    try {
        & $maven -f (Join-Path $root "pom.xml") -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "The Shard plugin build failed."
        }

        & $maven -q -f (Join-Path $root "pom.xml") dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
        if ($LASTEXITCODE -ne 0) {
            throw "Could not resolve the Bukkit API classpath for the shard examples."
        }
    } finally {
        if ($mavenWorkingDirectory) {
            Pop-Location
        }
    }

    $compileClasspath = (Join-Path $root "target\classes") + ";" + (Get-Content $classpathFile -Raw).Trim()
    $modules = @(Get-ChildItem (Join-Path $root "examples\essentials-shards") -Filter "shard.yml" -Recurse)
    if ($modules.Count -ne 50) {
        throw "Expected 50 Essentials-style shard descriptors, found $($modules.Count)."
    }
    if (-not (Test-Path $outputFolder)) {
        New-Item -ItemType Directory -Path $outputFolder | Out-Null
    }

    foreach ($descriptor in $modules) {
        $contents = Get-Content $descriptor.FullName -Raw
        $nameMatch = [regex]::Match($contents, "(?m)^name:\s*(\S+)\s*$")
        $mainMatch = [regex]::Match($contents, "(?m)^main:\s*(\S+)\s*$")
        if (-not $nameMatch.Success -or -not $mainMatch.Success) {
            throw "Invalid shard descriptor: $($descriptor.FullName)"
        }

        $sources = @(Get-ChildItem $descriptor.Directory.FullName -Filter "*.java" -Recurse)
        if ($sources.Count -eq 0) {
            throw "No Java sources found for $($nameMatch.Groups[1].Value)."
        }

        $moduleBuild = Join-Path $buildFolder $nameMatch.Groups[1].Value
        New-Item -ItemType Directory -Path $moduleBuild -Force | Out-Null
        & $javac -encoding UTF-8 -classpath $compileClasspath -d $moduleBuild @($sources.FullName)
        if ($LASTEXITCODE -ne 0) {
            throw "Compilation failed for $($nameMatch.Groups[1].Value)."
        }
        $mainClassFile = Join-Path $moduleBuild (($mainMatch.Groups[1].Value -replace '\.', '\') + ".class")
        if (-not (Test-Path $mainClassFile)) {
            throw "Main class $($mainMatch.Groups[1].Value) was not compiled for $($nameMatch.Groups[1].Value)."
        }

        $outputJar = Join-Path $outputFolder ($descriptor.Directory.Name + ".jar")
        & $jar --create --file $outputJar -C $descriptor.Directory.FullName "shard.yml" -C $moduleBuild .
        if ($LASTEXITCODE -ne 0) {
            throw "Could not package $($nameMatch.Groups[1].Value)."
        }
        Write-Host "Built $outputJar"
    }

    $legacyJarFolder = Join-Path $buildFolder "legacy-shards"
    New-Item -ItemType Directory -Path $legacyJarFolder -Force | Out-Null
    foreach ($group in @("shards", "advanced-shards", "creative-shards")) {
        $groupRoot = Join-Path $root "examples\$group"
        foreach ($descriptor in Get-ChildItem $groupRoot -Filter "shard.yml" -Recurse) {
            $contents = Get-Content $descriptor.FullName -Raw
            $nameMatch = [regex]::Match($contents, "(?m)^name:\s*(\S+)\s*$")
            $mainMatch = [regex]::Match($contents, "(?m)^main:\s*(\S+)\s*$")
            if (-not $nameMatch.Success -or -not $mainMatch.Success) {
                throw "Invalid shard descriptor: $($descriptor.FullName)"
            }

            $sources = @(Get-ChildItem $descriptor.Directory.FullName -Filter "*.java" -Recurse)
            if ($group -eq "advanced-shards" -or $group -eq "creative-shards") {
                $sharedFolder = Join-Path $groupRoot "shared"
                $sources += @(Get-ChildItem $sharedFolder -Filter "*.java" -Recurse)
            }
            if ($sources.Count -eq 0) {
                throw "No Java sources found for $($nameMatch.Groups[1].Value)."
            }

            $moduleBuild = Join-Path $buildFolder ("legacy-" + $nameMatch.Groups[1].Value)
            New-Item -ItemType Directory -Path $moduleBuild -Force | Out-Null
            & $javac -encoding UTF-8 -classpath $compileClasspath -d $moduleBuild @($sources.FullName)
            if ($LASTEXITCODE -ne 0) {
                throw "Compilation failed for $($nameMatch.Groups[1].Value)."
            }
            $mainClassFile = Join-Path $moduleBuild (($mainMatch.Groups[1].Value -replace '\.', '\') + ".class")
            if (-not (Test-Path $mainClassFile)) {
                throw "Main class $($mainMatch.Groups[1].Value) was not compiled for $($nameMatch.Groups[1].Value)."
            }

            $outputJar = Join-Path $legacyJarFolder ($nameMatch.Groups[1].Value + ".jar")
            & $jar --create --file $outputJar -C $descriptor.Directory.FullName "shard.yml" -C $moduleBuild .
            if ($LASTEXITCODE -ne 0) {
                throw "Could not package $($nameMatch.Groups[1].Value)."
            }
        }
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $releaseArchive = Join-Path $root "target\Shard-release.zip"
    $updatingArchive = Test-Path $releaseArchive
    if ($updatingArchive) {
        $archive = [System.IO.Compression.ZipFile]::Open(
            $releaseArchive,
            [System.IO.Compression.ZipArchiveMode]::Update
        )
    } else {
        $archive = [System.IO.Compression.ZipFile]::Open(
            $releaseArchive,
            [System.IO.Compression.ZipArchiveMode]::Create
        )
    }

    try {
        $releaseFiles = @(
            [pscustomobject]@{
                Path = Join-Path $root "target\Shard-1.0.0-SNAPSHOT.jar"
                Entry = "plugins/Shard-1.0.0-SNAPSHOT.jar"
            },
            [pscustomobject]@{
                Path = Join-Path $root "SHARDS.md"
                Entry = "docs/SHARDS.md"
            }
        )
        $customerTemplate = Join-Path $root "examples\customer-shard-template"
        foreach ($file in Get-ChildItem $customerTemplate -File -Recurse) {
            $relativePath = $file.FullName.Substring($customerTemplate.Length).TrimStart('\') -replace '\\', '/'
            $releaseFiles += [pscustomobject]@{
                Path = $file.FullName
                Entry = "custom-shard-template/$relativePath"
            }
        }
        foreach ($file in Get-ChildItem $legacyJarFolder -Filter "*.jar") {
            $releaseFiles += [pscustomobject]@{
                Path = $file.FullName
                Entry = "plugins/Shard/shards/$($file.Name)"
            }
        }
        foreach ($file in Get-ChildItem $outputFolder -Filter "*.jar") {
            $releaseFiles += [pscustomobject]@{
                Path = $file.FullName
                Entry = "plugins/Shard/shards/$($file.Name)"
            }
        }

        foreach ($file in $releaseFiles) {
            if ($updatingArchive) {
                $existingEntry = $archive.GetEntry($file.Entry)
                if ($existingEntry) {
                    $existingEntry.Delete()
                }
            }
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive,
                $file.Path,
                $file.Entry,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    } finally {
        $archive.Dispose()
    }
}
finally {
    if (Test-Path $classpathFile) {
        Remove-Item $classpathFile
    }
    if (Test-Path $buildFolder) {
        Remove-Item $buildFolder -Recurse
    }
    $env:JAVA_HOME = $originalJavaHome
}

Write-Host "Built $($modules.Count) Essentials-style shard JARs in $outputFolder"
Write-Host "Updated release package at $(Join-Path $root 'target\Shard-release.zip')"
