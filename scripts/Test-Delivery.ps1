param(
    [Parameter(Mandatory = $true)]
    [string]$PackageDirectory,
    [int]$Port = 22322,
    [switch]$SkipAi
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
# 验证使用明确的现有管理员，只读业务接口，不创建用户或修改资金
$testUsername = 'admin'
$apiTimeoutSeconds = 75
# 验证图片保留完整封面比例，宽度限制使请求小于正式图片体积上限
$testImageWidth = 480
$projectRoot = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $projectRoot 'packaging\DeliveryTools.ps1'
. $tools
$packageRoot = (Resolve-Path -LiteralPath $PackageDirectory).Path
$server = $null
$archive = $null
$report = New-Object 'System.Collections.Generic.List[string]'

# 编码 JWT 的一个片段，移除普通 Base64 中不适用于地址的字符
function ConvertTo-TokenPart {
    param([byte[]]$Bytes)

    $encoded = [Convert]::ToBase64String($Bytes)
    $encoded = $encoded.TrimEnd('=')
    $encoded = $encoded.Replace('+', '-')
    return $encoded.Replace('/', '_')
}

# 用本运行包独立签名验证现有账户，令牌只留在当前检查进程中，不写入文件
function New-DeliveryTestToken {
    param([string]$Secret, [string]$Username)

    $encoding = [System.Text.Encoding]::UTF8
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = $encoding.GetBytes('{"alg":"HS256","typ":"JWT"}')
    $payload = @{ sub = $Username; iat = $now; exp = $now + 300 }
    $payloadText = ConvertTo-Json -InputObject $payload -Compress
    $payloadBytes = $encoding.GetBytes($payloadText)
    $headerPart = ConvertTo-TokenPart -Bytes $header
    $payloadPart = ConvertTo-TokenPart -Bytes $payloadBytes
    $unsigned = $headerPart + '.' + $payloadPart
    $signer = New-Object System.Security.Cryptography.HMACSHA256
    try {
        $signer.Key = [Convert]::FromBase64String($Secret)
        $signature = $signer.ComputeHash($encoding.GetBytes($unsigned))
        return $unsigned + '.' + (ConvertTo-TokenPart -Bytes $signature)
    } finally {
        $signer.Dispose()
    }
}

# 发送现有后端的标准请求，失败时仅显示业务原因，不输出凭据
function Invoke-DeliveryApi {
    param([string]$Path, [hashtable]$Values)

    $body = ConvertTo-Json -InputObject @{ data = $Values } -Depth 8 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $response = Invoke-RestMethod -Uri ($address + $Path) -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec $apiTimeoutSeconds
    if ($response.code -ne 0) {
        throw ($Path + '：' + $response.msg)
    }
    return $response.data
}

# 从真实八球封面生成小图，验证图片问答的解码、大小限制和云端模型权限
function Get-DeliveryGameImage {
    Add-Type -AssemblyName System.Drawing
    $entry = $archive.GetEntry('BOOT-INF/classes/public/games/billiards/media/table.png')
    if (-not $entry) {
        throw '运行包缺少八球封面'
    }
    $stream = $entry.Open()
    $image = $null
    $thumbnail = $null
    $output = New-Object System.IO.MemoryStream
    try {
        $image = [System.Drawing.Image]::FromStream($stream)
        $width = $testImageWidth
        $height = [int][Math]::Round($image.Height * $width / $image.Width)
        $thumbnail = New-Object System.Drawing.Bitmap($width, $height)
        $graphics = [System.Drawing.Graphics]::FromImage($thumbnail)
        try {
            $graphics.DrawImage($image, 0, 0, $width, $height)
        } finally {
            $graphics.Dispose()
        }
        $thumbnail.Save($output, [System.Drawing.Imaging.ImageFormat]::Jpeg)
        return 'data:image/jpeg;base64,' + [Convert]::ToBase64String($output.ToArray())
    } finally {
        if ($thumbnail) { $thumbnail.Dispose() }
        if ($image) { $image.Dispose() }
        $output.Dispose()
        $stream.Dispose()
    }
}

try {
    Test-DeliveryFiles -Root $packageRoot
    $selectedPort = Get-DeliveryPort -PreferredPort $Port
    $server = Start-DeliveryServer -Root $packageRoot -Port $selectedPort
    $address = Wait-DeliveryReady -Process $server -Port $selectedPort
    $configurationPath = Join-Path $packageRoot 'config\application-local.properties'
    $configuration = Read-DeliveryProperties -Path $configurationPath
    $token = New-DeliveryTestToken -Secret $configuration['security.jwt.secret-key'] -Username $testUsername
    $headers = @{ Authorization = 'Bearer ' + $token }
    $profile = Invoke-DeliveryApi -Path '/api/gameUser/profile' -Values @{}
    if ($profile.username -ne $testUsername) {
        throw '运行包无法读取指定的现有账户'
    }
    $report.Add('PASS: bundled Java, backend startup and account identity')
    $switching = Invoke-DeliveryApi -Path '/api/localAccount/list' -Values @{}
    if (-not $switching.enabled -or $switching.accounts.Count -eq 0) {
        throw '右上角账户切换未正常提供账户列表'
    }
    $report.Add('PASS: local account switching')
    $homepage = Invoke-WebRequest -Uri ($address + '/MainPage') -UseBasicParsing -TimeoutSec 15
    if ($homepage.Content -notmatch 'id="app"') {
        throw '后端没有提供已构建的网页入口'
    }
    $registry = Invoke-RestMethod -Uri ($address + '/games/registry.json') -TimeoutSec 15
    foreach ($gamePath in @('/games/pvz/game.json', '/games/billiards/game.json', '/games/joker-table/game.json')) {
        if ($registry -notcontains $gamePath) {
            throw ('游戏清单缺少：' + $gamePath)
        }
        $game = Invoke-RestMethod -Uri ($address + $gamePath) -TimeoutSec 15
        if (-not $game.id) { throw ('游戏配置无效：' + $gamePath) }
    }
    $report.Add('PASS: website and three game manifests')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jarPath = Join-Path $packageRoot 'app\4398.jar'
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    if ($archive.GetEntry('BOOT-INF/classes/application-local.properties')) {
        throw '交付 JAR 中仍存在开发配置'
    }
    foreach ($name in @('java-player.js', 'native-player.js', 'pvz/game.jar', 'pvz/assets.zip', 'joker-table/runtime/index.html', 'joker-table/media/cover.jpg')) {
        $entry = $archive.GetEntry('BOOT-INF/classes/public/games/' + $name)
        if (-not $entry -or $entry.Length -eq 0) { throw ('运行包缺少游戏资源：' + $name) }
    }
    $report.Add('PASS: Java game resources, web runtime and no embedded development secrets')
    Invoke-DeliveryApi -Path '/api/base/getMenuList' -Values @{} | Out-Null
    Invoke-DeliveryApi -Path '/api/game/records' -Values @{} | Out-Null
    Invoke-DeliveryApi -Path '/api/store/catalog' -Values @{} | Out-Null
    Invoke-DeliveryApi -Path '/api/messages/list' -Values @{ page = 1; pageSize = 20; kind = 'all'; unreadOnly = $false } | Out-Null
    $report.Add('PASS: navigation, records, store and messages')
    $aiStatus = Invoke-DeliveryApi -Path '/api/ai/status' -Values @{}
    if (-not $aiStatus.configured) { throw '运行包中的 AI 配置不可用' }
    if (-not $SkipAi) {
        $textConversation = [Guid]::NewGuid()
        $textReply = Invoke-DeliveryApi -Path '/api/ai/chat' -Values @{
            conversationId = $textConversation.ToString()
            message = '请用一句中文说明美式八球的获胜条件'
        }
        if (-not $textReply.reply) { throw '文字 AI 未返回回答' }
        $report.Add('PASS: live text AI response')
        $imageSource = Get-DeliveryGameImage
        $imageConversation = [Guid]::NewGuid()
        $imageReply = Invoke-DeliveryApi -Path '/api/ai/image-chat' -Values @{
            conversationId = $imageConversation.ToString()
            message = '这张图是哪种游戏？请简短回答'
            images = @($imageSource)
        }
        if (-not $imageReply.reply) { throw '图片 AI 未返回回答' }
        $report.Add('PASS: live image AI response')
    } else {
        $report.Add('SKIPPED: live AI calls')
    }
    foreach ($line in $report) { Write-Host $line }
    $reportPath = Join-Path $packageRoot 'logs\delivery-check.txt'
    [System.IO.File]::WriteAllLines($reportPath, $report)
} finally {
    if ($archive) { $archive.Dispose() }
    Stop-DeliveryServer -Process $server
}
