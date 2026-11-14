<#
.SYNOPSIS
    Moves ModConfig.sanitize()'s per-feature checks into each feature's config block.

.DESCRIPTION
    sanitize() is 220 lines of clamps and guards written against flat fields. Once those fields move
    into config blocks, every one of those references stops compiling - and they are bare names
    ("crosshairRed = ..."), not "x.crosshairRed", so the rewrite that handles read sites does not see
    them.

    Moving them into the block is not just a mechanical necessity, it is the right home: a clamp
    belongs with the field it clamps, and the block is the only thing that knows its own range.

    The work splits in two:

    - Pure clamps ("name = ColorUtil.clamp(name, lo, hi);") are unambiguous: the field name decides
      the owning block.
    - Everything else - guards, loops, calls into renderers, per-bar arrays - is analysed by which
      fields it mentions, and reported if it spans more than one feature. In this codebase nothing
      does, which is what makes the split safe; the check is here so that stays true.

    Each block gets a sanitize() method; ModConfig.sanitize() delegates to them in field order.

.PARAMETER DryRun
    Report the plan without writing.

.EXAMPLE
    .\split-sanitize.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$configFile = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\config\ModConfig.java'
$blockDir = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\feature\config'

$mapFile = Join-Path $repo 'build\config-block-map.json'
if (-not (Test-Path -LiteralPath $mapFile)) { throw "run map-config-fields.ps1 first" }
$map = [IO.File]::ReadAllText($mapFile, [Text.Encoding]::UTF8) | ConvertFrom-Json
$fieldToFeature = @{}
foreach ($e in $map) { if ($e.Feature) { $fieldToFeature[$e.Field] = $e.Feature } }

# Fields that stay on ModConfig keep their sanitize lines where they are.
$staysFlat = @{}
foreach ($e in $map) { if ($e.How -eq 'global') { $staysFlat[$e.Field] = $true } }

$text = [IO.File]::ReadAllText($configFile, [Text.Encoding]::UTF8)
$start = $text.IndexOf('public void sanitize()')
if ($start -lt 0) { throw 'sanitize() not found' }

# Find the method's closing brace by brace counting from its opening one.
$open = $text.IndexOf('{', $start)
$depth = 0
$end = $open
for ($i = $open; $i -lt $text.Length; $i++) {
    if ($text[$i] -eq '{') { $depth++ }
    elseif ($text[$i] -eq '}') { $depth--; if ($depth -eq 0) { $end = $i; break } }
}
$bodyStart = $open + 1
$body = $text.Substring($bodyStart, $end - $bodyStart)
$bodyLines = $body -split "\r?\n"

# ---- classify each statement ----------------------------------------------------------------

# A "statement" here is one top-level line, or a whole block when the line opens one. Splitting on
# top-level lines rather than on ";" keeps guards and loops intact, which matters because their
# closing brace is as much part of the check as the condition.
$statements = New-Object System.Collections.Generic.List[object]
$i = 0
while ($i -lt $bodyLines.Count) {
    $line = $bodyLines[$i]
    if ($line.Trim() -eq '' -or $line.Trim().StartsWith('//')) {
        $statements.Add([PSCustomObject]@{ Lines = @($line); Text = $line; Kind = 'blank' })
        $i++
        continue
    }
    $depth = ([regex]::Matches($line, '\{')).Count - ([regex]::Matches($line, '\}')).Count
    $collected = @($line)
    $j = $i
    while ($depth -gt 0 -and $j + 1 -lt $bodyLines.Count) {
        $j++
        $collected += $bodyLines[$j]
        $depth += ([regex]::Matches($bodyLines[$j], '\{')).Count - ([regex]::Matches($bodyLines[$j], '\}')).Count
    }
    $statements.Add([PSCustomObject]@{ Lines = $collected; Text = ($collected -join "`n"); Kind = 'code' })
    $i = $j + 1
}

# ---- assign each statement to a block -------------------------------------------------------

$byFeature = @{}
$unassigned = New-Object System.Collections.Generic.List[object]
$multiFeature = New-Object System.Collections.Generic.List[object]

foreach ($stmt in $statements) {
    if ($stmt.Kind -ne 'code') { continue }
    # @() around the pipeline: a single match otherwise unwraps to a string, and $feats[0] then
    # indexes the first character - which is how every feature name came out as "c", "s", "m".
    $feats = @([regex]::Matches($stmt.Text, '(?<![A-Za-z0-9_])([a-z][A-Za-z0-9]*)(?![A-Za-z0-9_])') |
            ForEach-Object { $_.Groups[1].Value } |
            Where-Object { $fieldToFeature.ContainsKey($_) -and -not $staysFlat.ContainsKey($_) } |
            ForEach-Object { $fieldToFeature[$_] } |
            Select-Object -Unique)
    if ($feats.Count -eq 0) {
        $unassigned.Add($stmt)
    } elseif ($feats.Count -gt 1) {
        $multiFeature.Add([PSCustomObject]@{ Features = $feats; Text = $stmt.Text.Trim() })
    } else {
        $f = $feats[0]
        if (-not $byFeature.ContainsKey($f)) { $byFeature[$f] = New-Object System.Collections.Generic.List[string] }
        foreach ($l in $stmt.Lines) { $byFeature[$f].Add($l) }
    }
}

Write-Host ("statements: {0}   assigned: {1} feature(s)   unassigned: {2}   multi-feature: {3}" -f `
        $statements.Count, $byFeature.Count, $unassigned.Count, $multiFeature.Count)
Write-Host ''
Write-Host 'lines per feature:'
foreach ($k in ($byFeature.Keys | Sort-Object)) {
    Write-Host ("  {0,-32} {1,3}" -f $k, $byFeature[$k].Count)
}
if ($multiFeature.Count -gt 0) {
    Write-Host ''
    Write-Host 'MULTI-FEATURE (must be resolved by hand before this can proceed):'
    foreach ($m in $multiFeature) {
        Write-Host ("  [{0}] {1}" -f ($m.Features -join ', '), $m.Text)
    }
}
if ($unassigned.Count -gt 0) {
    Write-Host ''
    Write-Host 'unassigned (stays on ModConfig):'
    foreach ($u in ($unassigned | Select-Object -First 20)) {
        Write-Host ('  ' + $u.Text.Trim())
    }
    if ($unassigned.Count -gt 20) { Write-Host ("  ... and {0} more" -f ($unassigned.Count - 20)) }
}

if ($DryRun) {
    Write-Host ''
    Write-Host '(dry run, nothing written)'
    return
}

if ($multiFeature.Count -gt 0) {
    throw ("{0} statement(s) span more than one feature; resolve them before writing" -f $multiFeature.Count)
}

# ---- 1) give each block a sanitize() ---------------------------------------------------------

# The feature id to block field name, matching what apply-config-blocks.ps1 produced.
function Get-BlockName {
    param([string] $FeatureId)
    $parts = $FeatureId -split '_'
    $camel = $parts[0]
    for ($k = 1; $k -lt $parts.Length; $k++) {
        if ($parts[$k].Length -gt 0) { $camel += $parts[$k].Substring(0, 1).ToUpper() + $parts[$k].Substring(1) }
    }
    return "${camel}Block"
}

$writtenBlocks = 0
foreach ($feature in ($byFeature.Keys | Sort-Object)) {
    $blockName = Get-BlockName $feature
    # The config class is the block field name with a capital and "Config".
    $classBase = ($blockName -replace 'Block$', 'Config')
    $className = $classBase.Substring(0, 1).ToUpper() + $classBase.Substring(1)

    $candidates = @(
        (Join-Path $blockDir ($className + '.java'))
    )
    $classFile = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if ($null -eq $classFile) {
        Write-Warning ("no config class for {0} (expected {1})" -f $feature, $className)
        continue
    }

    $classText = [IO.File]::ReadAllText($classFile, [Text.Encoding]::UTF8)
    if ($classText -match 'void sanitize\(\)') { continue }

    # The statements are written against bare field names, which is exactly what a method on the
    # block sees - no rewriting of the bodies is needed.
    $sanitizeBody = @"
    /**
     * Clamps and repairs this block's fields after load.
     *
     * <p>Moved out of ModConfig.sanitize() when the fields moved here: a range belongs with the
     * field it constrains, and this class is the only one that knows it. Called from
     * ModConfig.sanitize(), which stays the single entry point.
     */
    public void sanitize() {
$((($byFeature[$feature]) -join "`r`n"))
    }
"@ -replace "`n", "`r`n"

    # Insert before the class's closing brace.
    $lastBrace = $classText.LastIndexOf('}')
    if ($lastBrace -lt 0) { throw "no closing brace in $className" }
    $classText = $classText.Substring(0, $lastBrace) + $sanitizeBody + "`r`n}" + $classText.Substring($lastBrace + 1)
    [IO.File]::WriteAllText($classFile, $classText, (New-Object Text.UTF8Encoding($false)))
    $writtenBlocks++
}
Write-Host ("wrote sanitize() into {0} block(s)" -f $writtenBlocks)

# ---- 2) ModConfig.sanitize delegates ---------------------------------------------------------

# Keep the statements that stay flat, and replace the rest with one call per block. Order follows the
# block fields so the delegation reads in the same order as the declarations.
$kept = New-Object System.Collections.Generic.List[string]
foreach ($stmt in $statements) {
    $feats = @()
    if ($stmt.Kind -eq 'code') {
        $feats = @([regex]::Matches($stmt.Text, '(?<![A-Za-z0-9_])([a-z][A-Za-z0-9]*)(?![A-Za-z0-9_])') |
                ForEach-Object { $_.Groups[1].Value } |
                Where-Object { $fieldToFeature.ContainsKey($_) -and -not $staysFlat.ContainsKey($_) } |
                Select-Object -Unique)
    }
    if ($stmt.Kind -ne 'code' -or $feats.Count -eq 0) {
        foreach ($l in $stmt.Lines) { $kept.Add($l) }
    }
}

$delegation = New-Object System.Collections.Generic.List[string]
$delegation.Add('        // Each block clamps its own fields; see the block classes for the ranges.')
foreach ($feature in ($byFeature.Keys | Sort-Object)) {
    $blockName = Get-BlockName $feature
    $delegation.Add(('        {0}.sanitize();' -f $blockName))
}

# Drop trailing blank lines from the kept body, then append the delegation.
while ($kept.Count -gt 0 -and $kept[$kept.Count - 1].Trim() -eq '') { $kept.RemoveAt($kept.Count - 1) }
$newBody = ($kept -join "`r`n") + "`r`n`r`n" + ($delegation -join "`r`n") + "`r`n    "

$text = $text.Substring(0, $bodyStart) + $newBody + $text.Substring($end)
[IO.File]::WriteAllText($configFile, $text, (New-Object Text.UTF8Encoding($false)))
Write-Host 'updated ModConfig.sanitize()'
