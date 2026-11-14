<#
.SYNOPSIS
    Converts a skypocket.json written by an older build into the current config shape.

.DESCRIPTION
    The mod has no released users yet, so its own config migrations are deliberately not shipped:
    each one would have to be carried forever for the sake of a handful of developer instances. This
    script does that work instead, once, against the profiles that actually exist.

    It rewrites the file in place and keeps a timestamped backup beside it. Running it twice is safe:
    a step whose source field is already gone is skipped, so the second run changes nothing.

    Written as steps rather than one transformation so that adding the next feature's field is a
    matter of appending a step, and so a failure names the step that failed.

.PARAMETER ConfigPath
    One or more skypocket.json files. Directories are searched for config/skypocket.json below them,
    which covers the profile-folder case.

.PARAMETER DryRun
    Report what would change without writing.

.EXAMPLE
    .\migrate-config.ps1 -ConfigPath 'D:\AxolotlApplication\profiles\26.2-Fabric 0.20.1 (1)\config\skypocket.json'

.EXAMPLE
    .\migrate-config.ps1 -ConfigPath 'D:\AxolotlApplication\profiles' -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string[]] $ConfigPath,

    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'

# Each step: a name, and a transformation over the deserialised JSON object.
#
# The steps operate on the parsed object rather than on text, so a value keeps its type: writing
# "hideStuckArrows": true out and reading it back as a string would make the mod's Gson reject the
# whole file, and losing every setting to a type mistake is far worse than the migration being
# verbose.
$steps = @(
    @{
        Name = 'hideStuckArrows -> hideStuckArrowsBlock'
        Apply = {
            param($json)
            if (-not ($json.PSObject.Properties.Name -contains 'hideStuckArrows')) {
                return $false
            }
            $value = [bool] $json.hideStuckArrows
            # The block is added even when the old value was false: the field is part of the current
            # shape, and a missing object would make the mod fall back to its default anyway.
            $block = [PSCustomObject]@{ enabled = $value }
            if ($json.PSObject.Properties.Name -contains 'hideStuckArrowsBlock') {
                $json.hideStuckArrowsBlock = $block
            } else {
                $json | Add-Member -NotePropertyName 'hideStuckArrowsBlock' -NotePropertyValue $block
            }
            $json.PSObject.Properties.Remove('hideStuckArrows')
            return $true
        }
    },
    @{
        Name = 'hideLandedArrows -> hideLandedArrowsBlock'
        Apply = {
            param($json)
            if (-not ($json.PSObject.Properties.Name -contains 'hideLandedArrows')) {
                return $false
            }
            $value = [bool] $json.hideLandedArrows
            $block = [PSCustomObject]@{ enabled = $value }
            if ($json.PSObject.Properties.Name -contains 'hideLandedArrowsBlock') {
                $json.hideLandedArrowsBlock = $block
            } else {
                $json | Add-Member -NotePropertyName 'hideLandedArrowsBlock' -NotePropertyValue $block
            }
            $json.PSObject.Properties.Remove('hideLandedArrows')
            return $true
        }
    }
)

function Resolve-ConfigFiles {
    param([string[]] $Paths)

    $files = New-Object System.Collections.Generic.List[string]
    foreach ($path in $Paths) {
        if (-not (Test-Path -LiteralPath $path)) {
            Write-Warning "not found, skipped: $path"
            continue
        }
        $item = Get-Item -LiteralPath $path
        if ($item.PSIsContainer) {
            # Profile root, or the config directory itself - accept either.
            $direct = Join-Path $item.FullName 'skypocket.json'
            $nested = Join-Path $item.FullName 'config\skypocket.json'
            if (Test-Path -LiteralPath $direct) {
                $files.Add($direct)
            } elseif (Test-Path -LiteralPath $nested) {
                $files.Add($nested)
            } else {
                # One level down covers "a folder of profiles" without walking an entire drive.
                foreach ($child in Get-ChildItem -LiteralPath $item.FullName -Directory) {
                    $candidate = Join-Path $child.FullName 'config\skypocket.json'
                    if (Test-Path -LiteralPath $candidate) {
                        $files.Add($candidate)
                    }
                }
            }
        } else {
            $files.Add($item.FullName)
        }
    }
    return $files
}

$targets = Resolve-ConfigFiles -Paths $ConfigPath
if ($targets.Count -eq 0) {
    throw 'no skypocket.json found under the given paths'
}

$totalChanged = 0
foreach ($file in $targets) {
    Write-Host ''
    Write-Host "== $file"

    # Read as UTF-8 explicitly. Get-Content would use the console code page, which on a Chinese
    # Windows is GBK - the file is UTF-8 and every Chinese setting value would come back mangled,
    # then be written back mangled.
    $raw = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)
    $json = $raw | ConvertFrom-Json

    $changed = $false
    foreach ($step in $steps) {
        $applied = & $step.Apply $json
        if ($applied) {
            Write-Host ("   applied: {0}" -f $step.Name)
            $changed = $true
        }
    }

    if (-not $changed) {
        Write-Host '   nothing to do'
        continue
    }

    $totalChanged++

    if ($DryRun) {
        Write-Host '   (dry run, not written)'
        continue
    }

    $backup = '{0}.{1}' -f $file, (Get-Date -Format 'yyyyMMdd-HHmmss')
    Copy-Item -LiteralPath $file -Destination $backup -Force
    Write-Host "   backup: $backup"

    # Write without a BOM and with the same 2-space indentation the mod's own Gson writer uses, so
    # the file stays diff-able instead of turning into one line.
    $text = $json | ConvertTo-Json -Depth 32
    [IO.File]::WriteAllText($file, $text, (New-Object Text.UTF8Encoding($false)))
    Write-Host '   written'
}

Write-Host ''
if ($DryRun) {
    Write-Host ("{0} file(s) would change (dry run)" -f $totalChanged)
} else {
    Write-Host ("{0} file(s) changed" -f $totalChanged)
}
