# Summarises how ModConfig's fields would divide into per-feature config blocks.
#
# Read-only static analysis: no file is modified. Used to plan and verify the Phase 3 config split.
# It answers two questions:
#   1. Can every ModConfig instance field be assigned to a feature block?
#   2. Optionally, given a real skypocket.json, which keys are missing from the source (new) and
#      which exist only in the file (left over from an older shape)?
#
# Usage:
#   .\tools\config-blocks.ps1
#       Field grouping only - works on any checkout.
#
#   .\tools\config-blocks.ps1 -ConfigPath 'C:\...\config\skypocket.json'
#       Also compares a real config file against the source. The path is required for this part
#       because a config file lives in the player's game directory, which is machine-specific.

[CmdletBinding()]
param(
    # Optional path to a skypocket.json to compare against. Omit to skip the comparison.
    [string]$ConfigPath
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$modConfig = Join-Path $repo 'versions\26.2-fabric\src\main\java\dev\skypocket\config\ModConfig.java'

if (-not (Test-Path -LiteralPath $modConfig)) {
    throw "找不到 ModConfig：$modConfig"
}

# 1) 从源码提取 public 实例字段（排除大写常量）
$fieldNames = Select-String -Path $modConfig -Pattern '^\s+public\s+\w+[\w<>\[\], ]*\s+(\w+)\s*=' -AllMatches |
    ForEach-Object { $_.Matches[0].Groups[1].Value } |
    Where-Object { $_ -cmatch '^[a-z]' } |
    Select-Object -Unique

Write-Host ("ModConfig 实例字段: {0}" -f $fieldNames.Count)
Write-Host ''

# 2) 功能前缀 -> 块名。顺序重要：更长的前缀先匹配，避免 stats 吃掉 statsManaBar 这类。
$blocks = [ordered]@{
    'statsBar'          = 'stats'
    'statsHealth'       = 'stats'
    'statsMana'         = 'stats'
    'statsDefense'      = 'stats'
    'statsSpeed'        = 'stats'
    'statsVitality'     = 'stats'
    'statsAir'          = 'stats'
    'statsHideVanilla'  = 'stats'
    'stats'             = 'stats'
    'crosshair'         = 'crosshair'
    'handItem'          = 'handItem'
    'blockOutline'      = 'blockOutline'
    'scrollableTooltips'= 'tooltips'
    'playerGlow'        = 'glow'
    'itemGlow'          = 'glow'
    'dragonGlow'        = 'glow'
    'hideNearbyPlayers' = 'esp'
    'hideSpawnPoint'    = 'esp'
    'hidePlayersNearNpcs' = 'esp'
    'hideOtherPlayers'  = 'esp'
    'hideLandedArrows'  = 'esp'
    'hideStuckArrows'   = 'esp'
    'hitbox'            = 'hitbox'
    'hotspot'           = 'fishing'
    'thirdPerson'       = 'camera'
    'noHurtCamera'      = 'camera'
    'mention'           = 'chat'
    'chatCompact'       = 'chat'
    'damageSplash'      = 'stats'
    'miningAbility'     = 'mining'
    'miningBreak'       = 'mining'
    'commissions'       = 'mining'
    'drillFuel'         = 'mining'
    'antialiasing'      = 'postProcess'
    'colorGrade'        = 'postProcess'
    'motionBlur'        = 'postProcess'
    'bloom'             = 'postProcess'
    'postProcess'       = 'postProcess'
    'clock'             = 'hud'
    'dayCount'          = 'hud'
    'fpsTpsPing'        = 'hud'
    'heldItem'          = 'hud'
    'skillDisplay'      = 'hud'
    'thunderBottle'     = 'hud'
    'pickupLog'         = 'hud'
    'legendarySea'      = 'hud'
    'clickGui'          = 'clickGui'
    'autoSprint'        = 'movement'
    'language'          = 'general'
    'panelCache'        = 'debug'
    'debugLog'          = 'debug'
    'configVersion'     = 'general'
    'skyBlockModulesOnly' = 'general'
    'hudSkyPocketFont'  = 'hud'
    # 传奇海怪警告属于钓鱼模块，前缀与模块 id 不同
    'legendarySeaCreature' = 'fishing'
    'legendaryWarning'  = 'fishing'
    # 准星视线射线（hitbox 模块的可选射线）
    'lookRay'           = 'hitbox'
    # 采矿时刻校准的相关字段，前两段共享 mining 前缀但被下面更具体的键命中
    'miningAlign'       = 'mining'
    'miningAuto'        = 'mining'
    'miningCategory'    = 'mining'
    'miningMin'         = 'mining'
    'miningPhase'       = 'mining'
    'miningResolved'    = 'mining'
    'miningTick'        = 'mining'
    'miningTiming'      = 'mining'
    # 挥手速度设置属于手持物品动画
    'swing'             = 'handItem'
}

function Get-Block([string]$field) {
    foreach ($prefix in $blocks.Keys) {
        if ($field.StartsWith($prefix, [StringComparison]::Ordinal)) {
            return $blocks[$prefix]
        }
    }
    return 'UNASSIGNED'
}

$byBlock = @{}
$unassigned = @()
foreach ($f in $fieldNames) {
    $b = Get-Block $f
    if ($b -eq 'UNASSIGNED') {
        $unassigned += $f
    } else {
        if (-not $byBlock.ContainsKey($b)) { $byBlock[$b] = @() }
        $byBlock[$b] += $f
    }
}

Write-Host '=== 按功能块分组 ==='
$byBlock.GetEnumerator() |
    Sort-Object { $_.Value.Count } -Descending |
    ForEach-Object { '{0,-14} {1,3} 字段' -f $_.Key, $_.Value.Count }

$assigned = ($byBlock.Values | ForEach-Object { $_.Count } | Measure-Object -Sum).Sum
Write-Host ''
Write-Host ("已归属: {0} / {1}" -f $assigned, $fieldNames.Count)
Write-Host ("未归属: {0}" -f $unassigned.Count)

if ($unassigned.Count -gt 0) {
    Write-Host ''
    Write-Host '=== 未归属字段（需要人工指定块或保留在顶层）==='
    $unassigned | Sort-Object | ForEach-Object { '  ' + $_ }
}

# 3) Optionally compare a real config file against the declared fields.
if (-not $ConfigPath) {
    Write-Host ''
    Write-Host '(skipping the config comparison: no -ConfigPath given)'
} elseif (-not (Test-Path -LiteralPath $ConfigPath)) {
    # Not fatal: the field grouping above is the part that works on any checkout, and a caller who
    # mistyped the path should still get it.
    Write-Host ''
    Write-Host "（跳过配置对照：找不到 $ConfigPath）"
} else {
    Write-Host ''
    Write-Host '=== 真实配置文件对照 ==='
    # 必须按 UTF-8 读：配置里有中文字符串值（主题名、样式名），
    # Get-Content 默认用系统 ANSI 会解码错，JSON 直接解析失败。
    $bytes = [IO.File]::ReadAllBytes($ConfigPath)
    $jsonText = (New-Object Text.UTF8Encoding($false)).GetString($bytes)
    $json = $jsonText | ConvertFrom-Json
    $present = $json.PSObject.Properties.Name

    $inConfigOnly = $present | Where-Object { $fieldNames -notcontains $_ }
    $inSourceOnly = $fieldNames | Where-Object { $present -notcontains $_ }

    Write-Host ("配置里出现的键: {0}" -f $present.Count)
    Write-Host ("源码里有、配置里没有（新增或从未保存）: {0}" -f $inSourceOnly.Count)
    Write-Host ("配置里有、源码里没有（废弃残留）: {0}" -f $inConfigOnly.Count)

    if ($inConfigOnly.Count -gt 0) {
        Write-Host ''
        Write-Host '--- 废弃残留键 ---'
        $inConfigOnly | Sort-Object | ForEach-Object { '  ' + $_ }
    }
}
