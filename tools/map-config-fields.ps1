<#
.SYNOPSIS
    Works out which config block every ModConfig field belongs to.

.DESCRIPTION
    Phase 3 moves each feature's fields out of the flat ModConfig into a per-feature block. That is
    ~300 fields and ~880 read sites, which is only worth doing by script if the mapping is derived
    rather than hand-written - a hand-written map of 300 entries is the same work as editing by hand
    and cannot be re-derived when it drifts.

    The mapping has two sources, in order:

    1. The feature class that reads the field. A field read by exactly one feature's own class
       belongs to that feature, and this covers most of them.
    2. The field's name prefix, for the rest: writers and readers outside the feature (HUD elements,
       renderers, mixins) still name their fields after the feature they serve.

    Whatever neither source explains is reported as unknown rather than guessed. A wrong guess here
    produces a block that compiles and reads the wrong object, which is the failure mode this whole
    exercise has to avoid.

    Writes a JSON report; changes nothing.
#>
[CmdletBinding()]
param(
    [string] $ReportPath = 'build\config-block-map.json'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$configFile = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\config\ModConfig.java'
$modulesDir = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\feature\modules'
$srcRoot = Join-Path $repo 'versions\26.2-fabric\src'

# ---- fields ----------------------------------------------------------------------------------

$configText = [IO.File]::ReadAllText($configFile, [Text.Encoding]::UTF8)
# The initialiser is captured verbatim so a block field keeps the default the flat field had. Losing
# it is silent for booleans (false is already the default) and wrong for everything else: a float
# that defaulted to 0.5 would come back as 0.0, and nothing in the build would say so.
$fields = [regex]::Matches($configText, '(?m)^    public ([\w<>\[\], \.]+) (\w+) (=|;)') |
        ForEach-Object {
            $name = $_.Groups[2].Value
            $type = $_.Groups[1].Value
            $init = ''
            if ($_.Groups[3].Value -eq '=') {
                # From '=' to the terminating semicolon on the same or a later line. Config
                # initialisers are single-expression, so the first ';' at depth 0 ends it.
                $start = $_.Index + $_.Length
                $depth = 0
                $i = $start
                while ($i -lt $configText.Length) {
                    $ch = $configText[$i]
                    if ($ch -eq '(' -or $ch -eq '{' -or $ch -eq '[') { $depth++ }
                    elseif ($ch -eq ')' -or $ch -eq '}' -or $ch -eq ']') { $depth-- }
                    elseif ($ch -eq ';' -and $depth -le 0) { break }
                    $i++
                }
                $init = $configText.Substring($start, $i - $start).Trim()
            }
            [PSCustomObject]@{ Type = $type; Name = $name; Init = $init }
        } |
        Where-Object { $_.Name -notlike '*Block' -and $_.Type -notlike 'static*' }

# ---- every read site -------------------------------------------------------------------------

# name -> set of files that reference it, so a field's owner can be inferred from its readers.
$readers = @{}
foreach ($file in Get-ChildItem -LiteralPath $srcRoot -Recurse -Filter *.java) {
    $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    foreach ($m in [regex]::Matches($text, '(?:ConfigManager\.get\(\)|cfg)\.(\w+)')) {
        $name = $m.Groups[1].Value
        if (-not $readers.ContainsKey($name)) { $readers[$name] = New-Object System.Collections.Generic.List[string] }
        if (-not $readers[$name].Contains($file.Name)) { $readers[$name].Add($file.Name) }
    }
}

# ---- source 1: the feature class that owns the field -----------------------------------------

$fieldToFeature = @{}
if (Test-Path -LiteralPath $modulesDir) {
    foreach ($file in Get-ChildItem -LiteralPath $modulesDir -Filter *.java) {
        # Normalise to the feature id rather than the class name: both sources below have to agree on
        # one key, and the class name and id differ (SkillDisplay vs skill_display), which split
        # every such feature into two groups.
        $text0 = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
        $idMatch = [regex]::Match($text0, '\n\s+return "([a-z_]+)";')
        if (-not $idMatch.Success) { continue }
        $feature = $idMatch.Groups[1].Value
        $text = $text0
        foreach ($m in [regex]::Matches($text, '(?:ConfigManager\.get\(\)|cfg)\.(\w+)')) {
            $name = $m.Groups[1].Value
            # Only claim it when no other feature's class also reads it; a field shared between two
            # feature classes is not obviously either one's, and is left to the prefix rule.
            if (-not $fieldToFeature.ContainsKey($name)) {
                $fieldToFeature[$name] = $feature
            } elseif ($fieldToFeature[$name] -ne $feature) {
                $fieldToFeature[$name] = $null
            }
        }
    }
}

# ---- source 2: name prefix -------------------------------------------------------------------

# Longest prefix wins, so "statsHealthText" beats "stats". Only prefixes that correspond to a real
# feature id are used - a prefix that matches nothing would silently create a block no feature owns.
$featureIds = @()
if (Test-Path -LiteralPath $modulesDir) {
    foreach ($file in Get-ChildItem -LiteralPath $modulesDir -Filter *.java) {
        $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
        $m = [regex]::Match($text, '\n\s+return "([a-z_]+)";')
        if ($m.Success) { $featureIds += $m.Groups[1].Value }
    }
}

# Feature id (snake_case) -> camelCase prefix used by field names: hide_stuck_arrows -> hideStuckArrows
$prefixOf = @{}
foreach ($id in $featureIds) {
    $parts = $id -split '_'
    $camel = $parts[0]
    for ($i = 1; $i -lt $parts.Length; $i++) {
        if ($parts[$i].Length -gt 0) {
            $camel += $parts[$i].Substring(0, 1).ToUpper() + $parts[$i].Substring(1)
        }
    }
    $prefixOf[$id] = $camel
}

<#
    Extra prefixes that map onto a feature without being a prefix of its full name.

    A field is named after the thing it configures, not after the feature id, and the two differ
    whenever the feature's name is longer than the subject: mining_ability_reminder owns
    "miningAbilityX", not "miningAbilityReminderX". The derived prefixes are exact, so those fields
    fell through to unknown; these entries cover them.

    Kept as an explicit table rather than a looser prefix rule (matching any known feature's leading
    words) because a loose rule would also match a field belonging to a *different* feature that
    happens to share a first word - which is the silent mis-assignment this script exists to avoid.
#>
$aliases = @(
    @{ Prefix = 'miningAbility';  Id = 'mining_ability_reminder' }
    @{ Prefix = 'pickupLog';      Id = 'item_pickup_log' }
    @{ Prefix = 'hotspotColor';   Id = 'fishing_hotspot_overlay' }
    @{ Prefix = 'mining';         Id = 'mining_break_predict' }
    @{ Prefix = 'stats';          Id = 'stats_display' }
)
foreach ($alias in $aliases) {
    if ($prefixOf.ContainsKey($alias.Id) -and $alias.Prefix.Length -gt $prefixOf[$alias.Id].Length) {
        # A shorter alias for a feature that already has a longer derived prefix: only add it when it
        # does not shadow anything, i.e. it is strictly shorter and therefore less specific.
        continue
    }
    $prefixOf[$alias.Id] = $alias.Prefix
}

$result = New-Object System.Collections.Generic.List[object]
$unknown = New-Object System.Collections.Generic.List[object]

<#
    Fields that are deliberately not a feature's state.

    These configure the mod as a whole - the panel's look and behaviour, the language, logging, the
    HUD font - or are bookkeeping the config manager owns (configVersion, the cached mining timing
    key). They stay on ModConfig: a "clickGui" block would have exactly one consumer and no feature
    to belong to, which is the flat class again with an extra hop.

    Listed explicitly so the report can tell "does not belong to a feature" apart from "could not be
    classified". Without that split the two look identical, and a real mis-classification would sit
    among these looking deliberate.
#>
$globalFields = @(
    'clickGuiAnimDuration', 'clickGuiBackgroundAlpha', 'clickGuiBlur', 'clickGuiBlurIntensity',
    'clickGuiEasing', 'clickGuiPanelCache', 'clickGuiScale', 'clickGuiTextShadow', 'clickGuiTheme',
    'configVersion', 'debugLog', 'hudSkyPocketFont', 'language', 'panelCacheStats',
    'skyBlockModulesOnly', 'miningTimingVersion'
)

foreach ($field in ($fields | Sort-Object Name)) {
    $name = $field.Name
    $owner = $null
    $how = $null

    if ($globalFields -contains $name) {
        $how = 'global'
    } elseif ($fieldToFeature.ContainsKey($name) -and $null -ne $fieldToFeature[$name]) {
        $owner = $fieldToFeature[$name]
        $how = 'feature-class'
    } else {
        # Longest matching camelCase prefix among the feature names.
        $best = $null
        foreach ($id in $prefixOf.Keys) {
            $p = $prefixOf[$id]
            if ($name.Length -gt $p.Length -and $name.StartsWith($p, [StringComparison]::Ordinal)) {
                $nextIsUpper = [char]::IsUpper($name[$p.Length])
                if ($nextIsUpper -and ($null -eq $best -or $p.Length -gt $best.Prefix.Length)) {
                    $best = [PSCustomObject]@{ Prefix = $p; Id = $id }
                }
            }
        }
        if ($null -ne $best) {
            $owner = $best.Id
            $how = 'prefix'
        }
    }

    $entry = [PSCustomObject]@{
        Field   = $name
        Type    = $field.Type
        Init    = $field.Init
        Feature = $owner
        How     = $how
        Readers = ($readers[$name] -join ',')
    }
    $result.Add($entry)
    # Only a field with no classification at all is unknown. A global field also has no owner, but
    # that is the deliberate answer, and reporting it as unknown would bury the real misses.
    if ($null -eq $owner -and $how -ne 'global') { $unknown.Add($entry) }
}

# ---- report ----------------------------------------------------------------------------------

$reportDir = Split-Path -Parent (Join-Path $repo $ReportPath)
if (-not (Test-Path -LiteralPath $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $repo $ReportPath) -Encoding utf8

$byFeature = $result | Where-Object { $null -ne $_.Feature } | Group-Object Feature | Sort-Object Count -Descending
Write-Host ("fields: {0}   mapped: {1}   unknown: {2}" -f $result.Count,
        ($result.Count - $unknown.Count), $unknown.Count)
Write-Host ("report: {0}" -f $ReportPath)
Write-Host ''
Write-Host 'by feature:'
foreach ($g in $byFeature) {
    Write-Host ("  {0,-28} {1,3}" -f $g.Name, $g.Count)
}

if ($unknown.Count -gt 0) {
    Write-Host ''
    Write-Host 'unknown (needs a hand-written rule):'
    foreach ($u in $unknown) {
        Write-Host ("  {0,-34} read by: {1}" -f $u.Field, $u.Readers)
    }
}
