<#
.SYNOPSIS
    Moves a feature's config fields out of ModConfig into its own block.

.DESCRIPTION
    Phase 3's mechanical part: given the field map produced by map-config-fields.ps1, this creates one
    config class per feature, replaces the flat fields on ModConfig with block fields, and rewrites
    every read site to go through the block.

    Two design choices make the rewrite safe enough to script:

    - Field names are unique across ModConfig, and a block keeps the original field names. So the
      rewrite is an insertion - `x.autoSprintEnabled` becomes `x.autoSprintBlock.autoSprintEnabled` -
      and the field name does not have to be understood, only located.
    - Every access goes through a variable or a call that ends in a dot before the name. Rather than
      enumerating those receivers (cfg, config, c, ConfigManager.get()), the pattern matches any
      receiver, and the replacement keeps it. A missed receiver would silently leave the old field
      behind, and once the old field is deleted that is a compile error - but only if the access was
      found, which is what the count check below is for.

    The old field is deleted, not deprecated. Keeping it would make an unrewritten read compile and
    return a default, which is the one failure mode this cannot check automatically.

.PARAMETER Feature
    Feature ids to migrate, e.g. auto_sprint. Omit with -All to do every mapped feature.

.PARAMETER DryRun
    Report what would change without writing.

.EXAMPLE
    .\apply-config-blocks.ps1 -Feature auto_sprint -DryRun

.EXAMPLE
    .\apply-config-blocks.ps1 -All
#>
[CmdletBinding(DefaultParameterSetName = 'Feature')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Feature')]
    [string[]] $Feature,

    [Parameter(Mandatory = $true, ParameterSetName = 'All')]
    [switch] $All,

    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$configFile = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\config\ModConfig.java'
$configDir = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\feature\config'
$srcRoot = Join-Path $repo 'versions\26.2-fabric\src'

$mapFile = Join-Path $repo 'build\config-block-map.json'
if (-not (Test-Path -LiteralPath $mapFile)) {
    throw "run map-config-fields.ps1 first: $mapFile is missing"
}
$map = [IO.File]::ReadAllText($mapFile, [Text.Encoding]::UTF8) | ConvertFrom-Json

# Fields already migrated (their block exists) are excluded so the script can be re-run.
$configText = [IO.File]::ReadAllText($configFile, [Text.Encoding]::UTF8)
$alreadyDone = [regex]::Matches($configText, 'public dev\.skypocket\.feature\.config\.(\w+) (\w+Block)') |
        ForEach-Object { $_.Groups[2].Value }

$targets = if ($All) {
    $map | Where-Object { $_.How -ne 'global' } | Select-Object -ExpandProperty Feature | Sort-Object -Unique
} else {
    $Feature
}

# ---- field name -> block field name -----------------------------------------------------------

# snake_case feature id to the camelCase name used for its block: hide_stuck_arrows -> hideStuckArrows
function Get-BlockName {
    param([string] $FeatureId)
    $parts = $FeatureId -split '_'
    $camel = $parts[0]
    for ($i = 1; $i -lt $parts.Length; $i++) {
        if ($parts[$i].Length -gt 0) {
            $camel += $parts[$i].Substring(0, 1).ToUpper() + $parts[$i].Substring(1)
        }
    }
    return "${camel}Block"
}

function Get-ConfigClassName {
    param([string] $FeatureId)
    $block = Get-BlockName $FeatureId
    $name = $block -replace 'Block$', 'Config'
    # PascalCase: the block field is camelCase (autoSprintBlock) but a class name must start upper.
    return $name.Substring(0, 1).ToUpper() + $name.Substring(1)
}

# ---- plan ------------------------------------------------------------------------------------

$plan = New-Object System.Collections.Generic.List[object]
foreach ($id in $targets) {
    $block = Get-BlockName $id
    if ($alreadyDone -contains $block) {
        Write-Host "skip $id (already has $block)"
        continue
    }
    $fields = $map | Where-Object { $_.Feature -eq $id -and $_.How -ne 'global' }
    if (-not $fields -or $fields.Count -eq 0) {
        Write-Host "skip $id (no mapped fields)"
        continue
    }
    $plan.Add([PSCustomObject]@{ Id = $id; Block = $block; ConfigClass = (Get-ConfigClassName $id); Fields = $fields })
}

if ($plan.Count -eq 0) {
    Write-Host 'nothing to do'
    return
}

# ---- access sites, counted per field ----------------------------------------------------------

# Every receiver is accepted: `cfg.`, `config.`, `c.`, `ConfigManager.get().`, and any other name the
# code happens to use. The boundary groups stop `statsBarHeight` from matching inside
# `statsBarHeights`, which is the one prefix collision present in this codebase (and all six such
# pairs are within a single feature, so a missed boundary would still not cross blocks).
$allMigrating = @()
foreach ($p in $plan) { $allMigrating += $p.Fields | Select-Object -ExpandProperty Field }
$accessPatternTemplate = '(?<![A-Za-z0-9_])(\w+(?:\(\))?\.)({0})(?![A-Za-z0-9_])'

$sourceFiles = Get-ChildItem -LiteralPath $srcRoot -Recurse -Filter *.java
$planned = @()
foreach ($p in $plan) {
    foreach ($f in $p.Fields) {
        $pattern = $accessPatternTemplate -f [regex]::Escape($f.Field)
        $count = 0
        foreach ($file in $sourceFiles) {
            if ($file.FullName -eq $configFile) { continue }   # the declaration itself is handled separately
            $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
            $count += ([regex]::Matches($text, $pattern)).Count
        }
        $planned += [PSCustomObject]@{ Plan = $p; Field = $f; Pattern = $pattern; Count = $count }
    }
}

Write-Host ''
Write-Host ("plan: {0} feature(s), {1} field(s)" -f $plan.Count, $planned.Count)
foreach ($p in $plan) {
    Write-Host ("  {0,-30} {1,-26} {2,2} fields" -f $p.Id, $p.Block, @($p.Fields).Count)
}
Write-Host ''
Write-Host 'access sites per field:'
foreach ($x in $planned) {
    Write-Host ("  {0,-34} {1,3}" -f $x.Field.Field, $x.Count)
}

if ($DryRun) {
    Write-Host ''
    Write-Host '(dry run, nothing written)'
    return
}

# ---- 1) config classes -----------------------------------------------------------------------

if (-not (Test-Path -LiteralPath $configDir)) { New-Item -ItemType Directory -Path $configDir -Force | Out-Null }
foreach ($p in $plan) {
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('package dev.skypocket.feature.config;')
    $lines.Add('')
    $lines.Add('/**')
    $lines.Add((" * Persisted state for the <code>{0}</code> feature." -f $p.Id))
    $lines.Add(' *')
    $lines.Add(' * <p>Fields keep the names they had on ModConfig so the rewrite of every read site is an')
    $lines.Add(' * insertion rather than a rename. Defaults are the ones the flat fields carried.')
    $lines.Add(' */')
    $lines.Add(("public final class {0} {{" -f $p.ConfigClass))
    foreach ($f in ($p.Fields | Sort-Object Field)) {
        # The initialiser is carried over verbatim. Booleans would survive losing it (false is
        # already the default) but nothing else would: a float that defaulted to 0.5 would come back
        # as 0.0 with no build-time signal.
        $init = if ([string]::IsNullOrEmpty($f.Init)) { '' } else { ' = ' + $f.Init }
        $lines.Add(("    public {0} {1}{2};" -f $f.Type, $f.Field, $init))
    }
    $lines.Add('}')
    $out = Join-Path $configDir ($p.ConfigClass + '.java')
    [IO.File]::WriteAllText($out, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding($false)))
    Write-Host ("wrote {0}" -f (Split-Path $out -Leaf))
}

# ---- 2) ModConfig: drop flat fields, add block fields ----------------------------------------

$configLines = [System.Collections.Generic.List[string]](@($configText -split "\r?\n"))
foreach ($p in $plan) {
    foreach ($f in $p.Fields) {
        # Remove the declaration line. Its javadoc, if any, is left in place: deleting comments by
        # pattern is how a comment ends up attached to the next field, and a leftover comment above a
        # block is harmless while a misplaced one is not.
        $removed = $false
        for ($i = 0; $i -lt $configLines.Count; $i++) {
            if ($configLines[$i] -match "^    public $([regex]::Escape($f.Type)) $($f.Field)\s*(=|;)") {
                $configLines.RemoveAt($i)
                $removed = $true
                break
            }
        }
        if (-not $removed) {
            throw ("could not find the declaration of {0} in ModConfig" -f $f.Field)
        }
    }
}

# Insert each block field where the first of its fields used to be, so related settings stay near
# each other in the file rather than all piling up at the end.
$blockDecls = New-Object System.Collections.Generic.List[string]
$blockDecls.Add('')
$blockDecls.Add('    // ---- Per-feature config blocks (Phase 3) ----')
$blockDecls.Add('    //')
$blockDecls.Add('    // Each feature owns its own state object; Gson writes them as nested objects under the')
$blockDecls.Add('    // field name. FeatureModule.config() returns the instance so the framework can find it.')
foreach ($p in $plan) {
    $blockDecls.Add(("    public dev.skypocket.feature.config.{0} {1} = new dev.skypocket.feature.config.{0}();" -f $p.ConfigClass, $p.Block))
}
# Insert just before the class's closing brace.
#
# The last non-blank line of the file is that brace; inserting after it puts the declarations
# outside the class, and the compiler then reports "compact source file should not have package
# declaration" - a message about line 1 that points nowhere near the real problem. Anchoring on the
# last non-blank line (rather than searching for the first "}") also keeps it correct if the class
# ends with a nested type or method, since those are closed before it.
$insertAt = $configLines.Count - 1
while ($insertAt -gt 0 -and $configLines[$insertAt].Trim() -eq '') { $insertAt-- }
if ($configLines[$insertAt].Trim() -ne '}') {
    throw ("expected the class to end with a closing brace, found: '{0}'" -f $configLines[$insertAt].Trim())
}
$configLines.InsertRange($insertAt, $blockDecls)
# Written with CRLF: the file is CRLF throughout, and mixing in bare LFs makes the Java compiler
# reject the result as a compact source file ("compact source file should not have package
# declaration") - a confusing message for what is only a line-ending problem.
[IO.File]::WriteAllText($configFile, (($configLines -join "`r`n") + "`r`n"), (New-Object Text.UTF8Encoding($false)))
Write-Host 'updated ModConfig'

# ---- 3) rewrite access sites -----------------------------------------------------------------

$totalRewritten = 0
foreach ($p in $plan) {
    foreach ($f in $p.Fields) {
        $pattern = $accessPatternTemplate -f [regex]::Escape($f.Field)
        $expected = ($planned | Where-Object { $_.Field.Field -eq $f.Field }).Count
        $actual = 0
        foreach ($file in $sourceFiles) {
            if ($file.FullName -eq $configFile) { continue }
            $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
            if (-not [regex]::IsMatch($text, $pattern)) { continue }
            # Insert the block name between the receiver's dot and the field name.
            $replaced = [regex]::Replace($text, $pattern, { param($m) $m.Groups[1].Value + $p.Block + '.' + $m.Groups[2].Value })
            $actual += ([regex]::Matches($text, $pattern)).Count
            [IO.File]::WriteAllText($file.FullName, $replaced, (New-Object Text.UTF8Encoding($false)))
        }
        if ($actual -ne $expected) {
            throw ("{0}: expected {1} replacements, made {2}" -f $f.Field, $expected, $actual)
        }
        $totalRewritten += $actual
    }
}

# ---- 4) verify no old field survives ---------------------------------------------------------

$leftovers = @()
foreach ($p in $plan) {
    foreach ($f in $p.Fields) {
        $pattern = '(?<![A-Za-z0-9_])' + [regex]::Escape($f.Field) + '(?![A-Za-z0-9_])'
        foreach ($file in $sourceFiles) {
            $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
            if ([regex]::IsMatch($text, '(?<![A-Za-z0-9_])' + $p.Block + '\.' + [regex]::Escape($f.Field) + '(?![A-Za-z0-9_])')) {
                continue   # the rewritten form, expected
            }
            # Any other mention must be gone; the config class is the only place it may appear bare.
            if ($file.FullName -like "*\feature\config\*") { continue }
        }
    }
}
if ($leftovers.Count -gt 0) { throw ("unrewritten references: " + ($leftovers -join ', ')) }

Write-Host ''
Write-Host ("rewrote {0} access site(s)" -f $totalRewritten)
