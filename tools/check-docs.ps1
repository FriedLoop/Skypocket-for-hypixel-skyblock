#!/usr/bin/env pwsh
# Verifies that what the docs claim about the package layout still matches the tree.
#
# The failure this guards against is specific: documentation that was right when written and
# quietly wrong afterwards. It has happened three times here already - the LayoutEngine class
# comment claiming no absolute positioning, the notes listing packages without util, and the
# notes saying there is no test screen after one was added.
#
# Run after moving or adding classes:  ./tools/check-docs.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src  = Join-Path $root 'versions/26.2-fabric/src/main/java/dev/skypocket'
$problems = New-Object System.Collections.ArrayList

# --- 1. Packages in the tree must all appear in the docs, and vice versa -------------------
$devDocPath = Join-Path $root 'docs/DEVELOPMENT.md'
$devDoc = [IO.File]::ReadAllText($devDocPath, [Text.Encoding]::UTF8)

# Tree lines are "|-- name/ ..." built from box-drawing characters U+251C / U+2514 / U+2500.
$branch = [string][char]0x251C + [string][char]0x2500
$last   = [string][char]0x2514 + [string][char]0x2500
$docPackages = @()
foreach ($line in ($devDoc -split "`n")) {
    $trimmed = $line.TrimEnd()
    if ($trimmed.StartsWith($branch) -or $trimmed.StartsWith($last)) {
        $rest = $trimmed.Substring(2).TrimStart()
        $slash = $rest.IndexOf('/')
        if ($slash -gt 0) {
            $candidate = $rest.Substring(0, $slash)
            # Skip the repo-root marker line ("dev.skypocket/") and anything not a bare name.
            if ($candidate -match '^[a-z][a-z0-9]*$') { $docPackages += $candidate }
        }
    }
}

$realPackages = @(Get-ChildItem $src -Directory | ForEach-Object { $_.Name })
# Anything the doc mentions that is not a real package is only a problem if it is indented as a
# top-level package. Repo directories such as "versions/" also appear in trees, so ignore names
# that are not under the source root at all unless they look like packages.
$notPackages = @('versions')
foreach ($pkg in $realPackages) {
    if ($docPackages -notcontains $pkg) {
        [void]$problems.Add("package '$pkg/' exists but is not listed in docs/DEVELOPMENT.md")
    }
}
foreach ($pkg in $docPackages) {
    if ($notPackages -contains $pkg) { continue }
    if (-not (Test-Path (Join-Path $src $pkg))) {
        [void]$problems.Add("docs/DEVELOPMENT.md lists '$pkg/' but no such package exists")
    }
}

# --- 2. Every docs/ file referenced from another doc or the README must exist --------------
# Renaming or deleting a doc leaves dangling references behind, which read as authoritative
# until someone follows them.
$docsDir = Join-Path $root 'docs'
$scanFiles = @(Get-ChildItem $docsDir -Filter '*.md')
$readme = Join-Path $root 'README.md'
if (Test-Path $readme) { $scanFiles += Get-Item $readme }

foreach ($doc in $scanFiles) {
    $text = [IO.File]::ReadAllText($doc.FullName, [Text.Encoding]::UTF8)
    # Match both "docs/NAME.md" and a bare "NAME.md" appearing in a doc tree listing.
    foreach ($m in [regex]::Matches($text, '(?:docs/)?([A-Za-z0-9_\-]+\.md)')) {
        $name = $m.Groups[1].Value
        $target = Join-Path $docsDir $name
        $atRoot = Join-Path $root $name
        if (-not (Test-Path $target) -and -not (Test-Path $atRoot)) {
            [void]$problems.Add("$($doc.Name) references $name which does not exist")
        }
    }
}

# --- 3. ADR numbering must be contiguous and unique ---------------------------------------
# Gaps and duplicates make ADR references ("see ADR-0007") ambiguous.
$adrDir = Join-Path $docsDir 'adr'
if (Test-Path $adrDir) {
    $numbers = @()
    foreach ($adr in (Get-ChildItem $adrDir -Filter '*.md')) {
        if ($adr.Name -match '^(\d{4})-') {
            $numbers += [int]$Matches[1]
        } else {
            [void]$problems.Add("docs/adr/$($adr.Name) does not start with a 4-digit number")
        }
    }
    $sorted = $numbers | Sort-Object
    for ($i = 0; $i -lt $sorted.Count; $i++) {
        if ($sorted[$i] -ne ($i + 1)) {
            [void]$problems.Add("ADR numbering has a gap or duplicate at $($i + 1) (found $($sorted[$i]))")
            break
        }
    }
}

# --- 4. The kernel must not depend upward -------------------------------------------------
# Mirrors the compile-time guard in uiKernelSources, so a violation is caught even for a class
# that is not in that list yet.
$upward = @('gui', 'feature', 'config', 'hud', 'skyblock', 'crosshair')
Get-ChildItem (Join-Path $src 'ui') -Recurse -Filter *.java | ForEach-Object {
    $file = $_
    $inMc = $file.FullName -match '[\\/]ui[\\/]mc[\\/]'
    $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    foreach ($layer in $upward) {
        if ($text -match "(?m)^import dev\.skypocket\.$layer\.") {
            [void]$problems.Add("ui/$($file.Name) imports dev.skypocket.$layer - kernel must not depend upward")
        }
    }
    if (-not $inMc -and $text -match '(?m)^import dev\.skypocket\.render\.') {
        [void]$problems.Add("ui/$($file.Name) imports dev.skypocket.render outside ui/mc")
    }
}

# --- Report -------------------------------------------------------------------------------
if ($problems.Count -eq 0) {
    Write-Host 'docs OK - no drift found'
    exit 0
}
Write-Host "docs drift: $($problems.Count) problem(s)"
foreach ($p in $problems) { Write-Host "  - $p" }
exit 1
