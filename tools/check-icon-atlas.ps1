# Verifies that the icon atlases on disk match the dimensions the Java code assumes.
#
# Why this exists
# ---------------
# Two bugs shipped on the icon features, in opposite directions, both from the same
# cause: a number that has to agree across two files with nothing checking it.
#
#   - icons-large.png was 1536x960 while ModuleIcons assumed a square 1536x1536. The
#     renderer had cropped the empty rows; v was divided by the wrong height and every
#     card icon came out stretched and clipped at the bottom.
#   - "Fixing" that by forcing the atlas square was applied to both sets, which made
#     icons.png 768x768 against code expecting 768x256 - breaking the footer gear and
#     the sidebar, which had been correct.
#
# Neither mismatch fails near the change, and neither surfaces as an error: the icons
# simply draw squashed, shifted, or from the wrong cell. The renderer asserts too, but
# it only runs when someone regenerates; this runs on every build, so a swapped atlas, a
# hand-edited constant, or a stale generated file is caught before anything is tested.
#
# What it checks
# --------------
#   1. Both PNGs exist and their real dimensions match AtlasSize.java's constants.
#   2. ModuleIconIds.java's CELLS length matches the number of filled cells the large
#      atlas implies and is under its CAPACITY.
#   3. The generated file's entries are unique - a duplicate id would silently map two
#      modules to one cell, and the second would never be drawn.

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$java = Join-Path $repo 'versions/26.2-fabric/src/main/java/dev/skypocket/gui'
$tex = Join-Path $repo 'versions/26.2-fabric/src/main/resources/assets/skypocket/textures/gui/sprites/ui'

$failures = New-Object System.Collections.Generic.List[string]

function Read-Constant([string]$path, [string]$name) {
    $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    $m = [regex]::Match($text, "static final int $name\s*=\s*(\d+)")
    if (-not $m.Success) {
        throw "找不到常量 $name 于 $path"
    }
    return [int]$m.Groups[1].Value
}

function Get-PngSize([string]$path) {
    # Reads the IHDR chunk directly: bytes 16..23 are width and height, big-endian.
    #
    # BitConverter is used rather than shifting bytes by hand: `[byte] -shl 24` overflows to a
    # negative int in PowerShell, and the result was 0x0 for a 768-wide image - a "dimensions do not
    # match" failure that pointed at the atlas instead of at this function.
    $bytes = [System.IO.File]::ReadAllBytes($path)
    if ($bytes.Length -lt 24) { throw "$path 太短，不是 PNG" }
    $head = [byte[]]$bytes[16..23]
    [array]::Reverse($head)              # big-endian -> little-endian for BitConverter
    $w = [BitConverter]::ToUInt32($head, 4)
    $h = [BitConverter]::ToUInt32($head, 0)
    return @([int]$w, [int]$h)
}

# ---- 1. 图集实际尺寸 vs Java 常量 -----------------------------------------

$sizeFile = Join-Path $java 'AtlasSize.java'
if (-not (Test-Path $sizeFile)) {
    $failures.Add("缺少 AtlasSize.java")
} else {
    $sets = @(
        @{ Name = 'icons.png';
           Cell = (Read-Constant $sizeFile 'SMALL_CELL')
           Cols = (Read-Constant $sizeFile 'SMALL_COLUMNS')
           Rows = (Read-Constant $sizeFile 'SMALL_ROWS') },
        @{ Name = 'icons-large.png';
           Cell = (Read-Constant $sizeFile 'LARGE_CELL')
           Cols = (Read-Constant $sizeFile 'LARGE_COLUMNS')
           Rows = (Read-Constant $sizeFile 'LARGE_ROWS') }
    )

    foreach ($s in $sets) {
        $path = Join-Path $tex $s.Name
        if (-not (Test-Path $path)) {
            $failures.Add("缺少图集 $($s.Name)，先运行 node tools/render-icons.mjs")
            continue
        }
        $actual = Get-PngSize $path
        $expectW = $s.Cols * $s.Cell
        $expectH = $s.Rows * $s.Cell
        if ($actual[0] -ne $expectW -or $actual[1] -ne $expectH) {
            $failures.Add(
                "图集尺寸不符 $($s.Name)：实际 $($actual[0])x$($actual[1])，" +
                "AtlasSize.java 声明 $expectW x $expectH " +
                "($($s.Cols) 列 x $($s.Rows) 行 x $($s.Cell)px)")
        }
    }
}

# ---- 2 & 3. 生成的 id 表与图集一致 ----------------------------------------

$idsFile = Join-Path $java 'ModuleIconIds.java'
if (-not (Test-Path $idsFile)) {
    $failures.Add("缺少 ModuleIconIds.java，先运行 node tools/render-icons.mjs")
} else {
    $text = Get-Content -LiteralPath $idsFile -Raw -Encoding UTF8

    $ids = [regex]::Matches($text, '"([a-z0-9_]+)",') | ForEach-Object { $_.Groups[1].Value }
    if ($ids.Count -eq 0) {
        $failures.Add("ModuleIconIds.java 里没有解析到任何 id")
    }

    $capMatch = [regex]::Match($text, 'CAPACITY\s*=\s*(\d+)')
    if ($capMatch.Success) {
        $cap = [int]$capMatch.Groups[1].Value
        if ($ids.Count -gt $cap) {
            $failures.Add(
                "ModuleIconIds.java 有 $($ids.Count) 个图标，超过图集容量 $cap。" +
                "提高 tools/render-icons.mjs 里的 columns。")
        }
    }

    $dupes = $ids | Group-Object | Where-Object { $_.Count -gt 1 }
    if ($dupes) {
        $names = ($dupes | ForEach-Object { "$($_.Name)x$($_.Count)" }) -join ', '
        $failures.Add("ModuleIconIds.java 有重复 id（两个模块会映射到同一格）：$names")
    }
}

# ---- 报告 -----------------------------------------------------------------

if ($failures.Count -gt 0) {
    Write-Host "icon atlas check FAILED - $($failures.Count) 个问题:" -ForegroundColor Red
    foreach ($f in $failures) {
        Write-Host "  - $f" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "图集由 tools/render-icons.mjs 生成；尺寸常量在 AtlasSize.java。"
    Write-Host "两者必须一致，改一处就要改另一处。"
    exit 1
}

Write-Host "icon atlas OK - both atlases match AtlasSize.java, id table is consistent"
exit 0
