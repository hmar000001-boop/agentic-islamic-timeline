[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ProjectRoot,
    [Parameter(Mandatory = $true)][string]$ResultFile
)

$ErrorActionPreference = 'Stop'
$propertiesPath = Join-Path $ProjectRoot '.mvn/wrapper/maven-wrapper.properties'
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Missing Maven wrapper properties: $propertiesPath"
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    if ($line -match '^\s*([^#\s][^=]*)=(.*)$') {
        $key = $Matches[1].Trim()
        $value = $Matches[2].Trim()
        if ($properties.ContainsKey($key)) {
            throw "Duplicate Maven wrapper property '$key'"
        }
        $properties[$key] = $value
    }
}

$distributionUrl = [string]$properties['distributionUrl']
$expectedSha512 = [string]$properties['distributionSha512Sum']
if ($distributionUrl -notmatch '^https://') {
    throw 'distributionUrl must use HTTPS'
}
if ($distributionUrl -notmatch '/apache-maven-(\d+(?:\.\d+)+)-bin\.tar\.gz$') {
    throw "Unsupported Maven distribution URL: $distributionUrl"
}
$mavenVersion = $Matches[1]
if ($expectedSha512 -notmatch '^[0-9a-f]{128}$') {
    throw 'distributionSha512Sum must be 128 lowercase hexadecimal characters'
}

$archiveName = [IO.Path]::GetFileName([Uri]$distributionUrl)
$userHome = $env:MAVEN_USER_HOME
if ([string]::IsNullOrWhiteSpace($userHome)) {
    $userHome = Join-Path $env:USERPROFILE '.m2'
}
$cacheRoot = Join-Path $userHome 'wrapper/dists'
$installDir = Join-Path (Join-Path $cacheRoot "apache-maven-$mavenVersion") $expectedSha512
$mavenCmd = Join-Path $installDir 'bin/mvn.cmd'
$stamp = Join-Path $installDir '.retail-agent-distribution-sha512'

function Test-InstalledMaven {
    if (-not (Test-Path -LiteralPath $mavenCmd -PathType Leaf) -or
        -not (Test-Path -LiteralPath $stamp -PathType Leaf)) {
        return $false
    }
    if ((Get-Content -LiteralPath $stamp -Raw).Trim() -ne $expectedSha512) {
        return $false
    }
    $versionText = (& $mavenCmd --version 2>&1 | Out-String)
    return ($LASTEXITCODE -eq 0 -and $versionText.StartsWith("Apache Maven $mavenVersion "))
}

if (-not (Test-InstalledMaven)) {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    $lockDir = Join-Path $cacheRoot ".apache-maven-$mavenVersion.lock"
    $lockAcquired = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        try {
            New-Item -ItemType Directory -Path $lockDir -ErrorAction Stop | Out-Null
            $lockAcquired = $true
            break
        }
        catch [System.IO.IOException] {
            if (Test-InstalledMaven) { break }
            Start-Sleep -Seconds 1
        }
    }
    if (-not $lockAcquired -and -not (Test-InstalledMaven)) {
        throw "Timed out waiting for Maven installation lock: $lockDir"
    }

    if ($lockAcquired) {
        $temporaryDir = Join-Path $cacheRoot ('.maven-wrapper-' + [Guid]::NewGuid().ToString('N'))
        try {
            if (-not (Test-InstalledMaven)) {
                New-Item -ItemType Directory -Path $temporaryDir | Out-Null
                $archivePath = Join-Path $temporaryDir $archiveName
                Invoke-WebRequest -Uri $distributionUrl -OutFile $archivePath -MaximumRedirection 10 -TimeoutSec 600
                $actualSha512 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA512).Hash.ToLowerInvariant()
                if ($actualSha512 -ne $expectedSha512) {
                    throw "Maven distribution SHA-512 mismatch (expected $expectedSha512, got $actualSha512)"
                }
                & tar.exe -xzf $archivePath -C $temporaryDir
                if ($LASTEXITCODE -ne 0) { throw 'Failed to extract Maven distribution with tar.exe' }
                $extractedDir = Join-Path $temporaryDir "apache-maven-$mavenVersion"
                $extractedMaven = Join-Path $extractedDir 'bin/mvn.cmd'
                if (-not (Test-Path -LiteralPath $extractedMaven -PathType Leaf)) {
                    throw 'Maven archive has an unexpected layout'
                }
                $versionText = (& $extractedMaven --version 2>&1 | Out-String)
                if ($LASTEXITCODE -ne 0 -or -not $versionText.StartsWith("Apache Maven $mavenVersion ")) {
                    throw 'Downloaded Maven executable did not report the pinned version'
                }
                Set-Content -LiteralPath (Join-Path $extractedDir '.retail-agent-distribution-sha512') -Value $expectedSha512 -NoNewline -Encoding Ascii
                if (Test-Path -LiteralPath $installDir) {
                    Remove-Item -LiteralPath $installDir -Recurse -Force
                }
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $installDir) | Out-Null
                Move-Item -LiteralPath $extractedDir -Destination $installDir
            }
        }
        finally {
            if (Test-Path -LiteralPath $temporaryDir) {
                Remove-Item -LiteralPath $temporaryDir -Recurse -Force
            }
            if (Test-Path -LiteralPath $lockDir) {
                Remove-Item -LiteralPath $lockDir -Force
            }
        }
    }
}

if (-not (Test-InstalledMaven)) {
    throw 'Pinned Maven distribution is not installed or failed its cache validation'
}
# Return only ASCII version/hash tokens. cmd.exe reconstructs the executable path
# from USERPROFILE/MAVEN_USER_HOME, preserving Unicode in user directory names.
[IO.File]::WriteAllLines($ResultFile, @($mavenVersion, $expectedSha512), [Text.Encoding]::ASCII)
