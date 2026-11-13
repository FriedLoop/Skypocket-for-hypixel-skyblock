# Compares the hand-written ModuleDefinitions registrations against the migrated FeatureModule
# classes, so a migration that silently changes a slider range or a default can be caught before
# it reaches a build.
#
# Why this exists: migrating a registration by hand is retyping a colour, a range, a step and a
# label. Two such typos slipped through in the first Phase 4 batch (a slider range and its step),
# and neither showed up in compilation or in the panel - a narrower range simply stops the player
# from reaching values the old build allowed.
#
# Usage: .\tools\check-feature-parity.ps1
#
# Reads git HEAD for the "before" side, so the working tree must still contain the change.

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$defsPath = 'versions/26.2-fabric/src/main/java/dev/skypocket/gui/ModuleDefinitions.java'
$modulesDir = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\feature\modules'

# "Before": the committed version of ModuleDefinitions.
#
# git writes the blob to stdout as UTF-8. Capturing that with `& git ...` would let PowerShell decode
# it using the console code page, which on a Chinese Windows is GBK - and that mangles every Chinese
# string in this file. The damage is silent and easy to misread: the quoted name in
# Module("id", "中文名" stops matching, so those registrations are treated as absent and the check
# reports "no HEAD registration to compare" instead of comparing them.
#
# Asking git for the blob with an explicit UTF-8 decode keeps the bytes intact.
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = 'git'
$psi.Arguments = "-C `"$repo`" show `"HEAD:$defsPath`""
$psi.RedirectStandardOutput = $true
$psi.UseShellExecute = $false
$psi.StandardOutputEncoding = [Text.Encoding]::UTF8
$proc = [System.Diagnostics.Process]::Start($psi)
$before = $proc.StandardOutput.ReadToEnd()
$proc.WaitForExit()
if (-not $before) {
    throw "could not read HEAD:$defsPath"
}

# Every Module("id", "name", Category.X, ...) block still in the old file, with its settings text.
#
# The captured text must span the whole enclosing register method. Two earlier attempts were wrong:
# stopping at the first ";\n" cut a block short inside a multi-line lambda (block_outline writes four
# colour channels in a `v -> { ... }` body, so the first channel's semicolon ended the capture and
# its sliders vanished), and stopping at the next Module( swallowed the following feature instead.
# A register method always ends with a line containing exactly "    }", which is unambiguous here.
$oldBlocks = @{}
$rx = [regex]'new Module\("([a-z_]+)",\s*"([^"]+)"'
foreach ($m in $rx.Matches($before)) {
    $id = $m.Groups[1].Value

    # Backwards to the enclosing method's signature, forwards to that method's closing brace.
    $methodStart = $before.LastIndexOf('private static void register', $m.Index)
    if ($methodStart -lt 0) { $methodStart = $m.Index }
    $methodEnd = $before.IndexOf("`n    }`n", $m.Index)
    if ($methodEnd -lt 0) { $methodEnd = $before.Length } else { $methodEnd += "`n    }".Length }

    $oldBlocks[$id] = $before.Substring($methodStart, $methodEnd - $methodStart)
}

# "After": each migrated feature class.
$newFeatures = @{}
foreach ($file in Get-ChildItem -LiteralPath $modulesDir -Filter *.java) {
    $text = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    $idm = [regex]::Match($text, '\n\s+return "([a-z_]+)";')
    if (-not $idm.Success) { continue }
    $newFeatures[$idm.Groups[1].Value] = @{ File = $file.Name; Text = $text }
}

Write-Host ("old registrations: {0}   migrated features: {1}" -f $oldBlocks.Count, $newFeatures.Count)
Write-Host ''

$problems = 0
foreach ($id in ($newFeatures.Keys | Sort-Object)) {
    $new = $newFeatures[$id]
    if (-not $oldBlocks.ContainsKey($id)) {
        # Not a problem: the feature may have been migrated in an earlier commit whose "before"
        # side no longer exists in HEAD. Say so rather than pretending it matches.
        Write-Host ("  {0,-32} (no HEAD registration to compare)" -f $id)
        continue
    }
    $old = $oldBlocks[$id]

    # 1) Slider: name, min, max, step. Compared as raw text because the old side is source code.
    $oldSliders = [regex]::Matches($old, 'SliderSetting\("([^"]+)",\s*([-\d.]+),\s*([-\d.]+)')
    $newSliders = [regex]::Matches($new.Text, 'SliderSetting\("([^"]+)",\s*([-\d.]+),\s*([-\d.]+)')
    if ($oldSliders.Count -ne $newSliders.Count) {
        Write-Host ("  {0,-32} slider count {1} -> {2}" -f $id, $oldSliders.Count, $newSliders.Count)
        $problems++
    }
    for ($i = 0; $i -lt [Math]::Min($oldSliders.Count, $newSliders.Count); $i++) {
        $o = $oldSliders[$i]; $n = $newSliders[$i]
        if ($o.Groups[1].Value -ne $n.Groups[1].Value -or
            $o.Groups[2].Value -ne $n.Groups[2].Value -or
            $o.Groups[3].Value -ne $n.Groups[3].Value) {
            Write-Host ("  {0,-32} slider '{1}' ({2}..{3}) became '{4}' ({5}..{6})" -f `
                $id, $o.Groups[1].Value, $o.Groups[2].Value, $o.Groups[3].Value,
                $n.Groups[1].Value, $n.Groups[2].Value, $n.Groups[3].Value)
            $problems++
        }
    }

    # 2) Slider step: the number after the format string, e.g. SliderSetting(..., "%.1f", 0.1).
    #
    # The format string is not always a bare "%.1f": sliders use suffixes too ("%.0fs" for seconds,
    # "%.2fx" for a multiplier, "%.0f%%" for a percentage). Matching only '%\.\d+f' silently skipped
    # every suffixed slider, and a mutated step in one of them produced "no differences" - the check
    # looked like it had passed.
    $oldSteps = [regex]::Matches($old, '"%[^"]*",\s*([-\d.]+)\)')
    $newSteps = [regex]::Matches($new.Text, '"%[^"]*",\s*([-\d.]+)\)')
    for ($i = 0; $i -lt [Math]::Min($oldSteps.Count, $newSteps.Count); $i++) {
        if ($oldSteps[$i].Groups[1].Value -ne $newSteps[$i].Groups[1].Value) {
            Write-Host ("  {0,-32} slider step {1} -> {2}" -f $id, $oldSteps[$i].Groups[1].Value, $newSteps[$i].Groups[1].Value)
            $problems++
        }
    }

    # 3) Setting labels and Header subtitles, in order.
    #
    # Both halves matter. A Header's first string is its title and the second is the explanation
    # shown under it; comparing only the titles let a retyped subtitle through, and the subtitle is
    # also the i18n key - so the panel showed Chinese in English mode with nothing to flag it.
    $oldLabels = ([regex]::Matches($old, 'new Setting\.\w+\("([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    $newLabels = ([regex]::Matches($new.Text, 'new Setting\.\w+\("([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    if (($oldLabels -join '|') -ne ($newLabels -join '|')) {
        Write-Host ("  {0,-32} labels [{1}] -> [{2}]" -f $id, ($oldLabels -join ','), ($newLabels -join ','))
        $problems++
    }

    $oldHeaders = ([regex]::Matches($old, 'new Setting\.Header\("([^"]+)",\s*"([^"]+)"') | ForEach-Object { "$($_.Groups[1].Value) / $($_.Groups[2].Value)" })
    $newHeaders = ([regex]::Matches($new.Text, 'new Setting\.Header\("([^"]+)",\s*"([^"]+)"') | ForEach-Object { "$($_.Groups[1].Value) / $($_.Groups[2].Value)" })
    if (($oldHeaders -join '|') -ne ($newHeaders -join '|')) {
        Write-Host ("  {0,-32} headers [{1}] -> [{2}]" -f $id, ($oldHeaders -join ' ; '), ($newHeaders -join ' ; '))
        $problems++
    }

    # 5) Every Chinese literal the feature uses must exist in the translation table.
    #
    # This is the check that would have caught the subtitle typo directly: the text was retyped
    # without its "SkyBlock", so the panel fell back to the key and rendered Chinese in English mode.
    $localization = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\i18n\Localization.java'
    if (Test-Path -LiteralPath $localization) {
        $en = [IO.File]::ReadAllText($localization, [Text.Encoding]::UTF8)
        foreach ($lit in [regex]::Matches($new.Text, '"([^"\\]*[\u4e00-\u9fff][^"\\]*)"')) {
            $value = $lit.Groups[1].Value
            if (-not $en.Contains("EN.put(`"$value`"")) {
                Write-Host ("  {0,-32} no EN entry for '{1}'" -f $id, $value)
                $problems++
            }
        }
    }

    # 4) SkyBlock badge.
    $oldSb = $old -match 'markSkyBlockOnly'
    $newSb = $new.Text -match 'skyBlockOnly\(\)[\s\S]{0,80}?return true'
    if ($oldSb -ne $newSb) {
        Write-Host ("  {0,-32} skyBlockOnly {1} -> {2}" -f $id, $oldSb, $newSb)
        $problems++
    }
}

Write-Host ''
if ($problems -eq 0) {
    Write-Host 'no differences'
} else {
    Write-Host ("{0} difference(s) found" -f $problems)
    exit 1
}
