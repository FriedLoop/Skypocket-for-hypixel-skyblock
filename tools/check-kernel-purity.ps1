#!/usr/bin/env pwsh
# Enforces that the UI kernel does not reference Minecraft.
#
# Reading the bytecode rather than relying on a compile failure: Minecraft is on the compile
# classpath, so a `ui` class that imports net.minecraft compiles perfectly well. The previous guard
# was a source-set include list, which looked like a guard but proved nothing - a deliberate probe
# that added `net.minecraft.client.Minecraft` to ui/Size.java compiled and passed the whole test
# suite.
#
# Not every reference is a violation. Some types have to cross the boundary:
#
#   GuiGraphicsExtractor  the draw entry point. UiPainter, RenderCache and UiPage all take it
#                         because painting has to start somewhere real.
#   input events          MC fires the events; the kernel's adapter for them (InputKeys) is where
#                         the platform types are translated. That is its whole job.
#   Minecraft             reaching into the game for a service - window scale, clipboard. This is
#                         the one that belongs behind an interface, because it is avoidable.
#
# So the check allows the first two categories and fails on anything that reaches for a service.
#
#   ./tools/check-kernel-purity.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$classesRoot = Join-Path $root 'versions/26.2-fabric/build/classes/java/main/dev/skypocket'
$problems = New-Object System.Collections.ArrayList

if (-not (Test-Path $classesRoot)) {
    Write-Host 'kernel purity: no compiled classes, run a build first'
    exit 0
}

# Types that legitimately cross the boundary, with the reason.
$allowedTypes = @(
    'net/minecraft/client/gui/GuiGraphicsExtractor',   # draw entry point
    'net/minecraft/client/input/KeyEvent',             # platform input, adapted by InputKeys
    'net/minecraft/client/input/CharacterEvent',
    'net/minecraft/client/input/PreeditEvent',
    'net/minecraft/client/input/MouseButtonEvent'
)

# Reaching for a live game object is always avoidable - it means a service hid inside a widget.
$forbiddenPatterns = @(
    'net/minecraft/client/Minecraft'
)

function Get-PlatformReferences {
    param([System.IO.FileInfo] $File)
    $text = [Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($File.FullName))
    $refs = @()
    foreach ($m in [regex]::Matches($text, 'net/minecraft/[\w/$]+')) {
        $name = $m.Value
        if ($allowedTypes -contains $name) { continue }
        # Nested and inner classes resolve to the outer name plus $.
        $outer = ($name -split '\$')[0]
        if ($allowedTypes -contains $outer) { continue }
        if ($refs -notcontains $name) { $refs += $name }
    }
    return $refs
}

# --- kernel classes: everything in ui/ except ui/mc ---------------------------------------
$uiDir = Join-Path $classesRoot 'ui'
if (Test-Path $uiDir) {
    Get-ChildItem $uiDir -Recurse -Filter *.class | ForEach-Object {
        $rel = $_.FullName.Substring($classesRoot.Length + 1)
        if ($rel -like 'ui\mc\*') { return }
        $refs = Get-PlatformReferences $_
        if ($refs.Count -gt 0) {
            [void]$problems.Add("$rel references $($refs -join ', ') - use a UiPlatform service instead")
        }
    }
}

if ($problems.Count -eq 0) {
    Write-Host 'kernel purity OK - ui/ reaches the platform only through its declared boundary types'
    exit 0
}
Write-Host "kernel purity: $($problems.Count) violation(s)"
foreach ($p in $problems) { Write-Host "  - $p" }
exit 1

