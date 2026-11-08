param(
    [string]$ConfigurationPath = '',
    [string]$OutputDirectory = '',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$RuntimeArchive = '',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
# 运行环境从官方清单获取，压缩包必须通过清单给出的 SHA-256 校验
$runtimeCatalog = 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot 'web-vue'
$backendRoot = Join-Path $projectRoot 'java-server\java-server'
$templateRoot = Join-Path $projectRoot 'packaging'
$cacheRoot = Join-Path $projectRoot '.local\delivery-cache'
$tools = Join-Path $templateRoot 'DeliveryTools.ps1'
. $tools

# 构建机查找现有 JDK 21，老师运行时不使用这些本机安装路径
function Find-BuildJava {
    param([string]$RequestedHome)

    $candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($RequestedHome) {
        $candidates.Add($RequestedHome)
    }
    $javaDirectory = Join-Path $env:ProgramFiles 'Java'
    if (Test-Path -LiteralPath $javaDirectory) {
        foreach ($directory in Get-ChildItem -LiteralPath $javaDirectory -Directory) {
            $candidates.Add($directory.FullName)
        }
    }
    foreach ($candidate in $candidates) {
        $compiler = Join-Path $candidate 'bin\javac.exe'
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $compiler) -and (Test-Path -LiteralPath $release)) {
            $releaseText = Get-Content -LiteralPath $release -Raw
            if ($releaseText -match 'JAVA_VERSION="21\.') {
                return $candidate
            }
        }
    }
    throw '打包机需要 JDK 21，请设置 JAVA_HOME 或使用 -JavaHome 指定目录'
}

# 构建前验证实际凭据，缺失时不生成一个只能打开空页面的交付包
function Read-PackageConfiguration {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw '找不到本机配置，请先填写 application-local.properties，再执行打包'
    }
    $configuration = Read-DeliveryProperties -Path $Path
    foreach ($name in @('spring.datasource.password', 'app.ai.api-key')) {
        $value = $configuration[$name]
        if (-not $value -or $value.StartsWith('YOUR_') -or $value.Contains('${')) {
            throw "打包需要有效配置：$name"
        }
    }
    return $configuration
}

# 只把业务连接配置写进交付目录，为本包生成独立 JWT 密钥，不携带开发工具规则
function Write-PackageConfiguration {
    param([hashtable]$Configuration, [string]$Destination)

    $lines = New-Object 'System.Collections.Generic.List[string]'
    foreach ($name in $Configuration.Keys) {
        if ($name.StartsWith('spring.datasource.') -or $name.StartsWith('app.ai.')) {
            if ($name -ne 'app.ai.quota-file') {
                $lines.Add($name + '=' + $Configuration[$name])
            }
        }
    }
    $secretBytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($secretBytes)
    } finally {
        $random.Dispose()
    }
    $secret = [Convert]::ToBase64String($secretBytes)
    $lines.Add('security.jwt.secret-key=' + $secret)
    $lines.Add('app.local-account-switch=true')
    $lines.Add('server.address=127.0.0.1')
    $lines.Add('attach.folder=./data/attachments/')
    $lines.Add('app.ai.quota-file=./data/ai-usage.json')
    $content = [string]::Join("`r`n", $lines) + "`r`n"
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Destination, $content, $encoding)
}

# 移除构建时自动复制进 JAR 的开发配置，交付版只读取外部独立配置
function Remove-EmbeddedSecrets {
    param([string]$JarPath)

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::Open($JarPath, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        foreach ($name in @('BOOT-INF/classes/application-local.properties', 'BOOT-INF/classes/application-dev.properties')) {
            $entry = $archive.GetEntry($name)
            if ($entry) {
                $entry.Delete()
            }
        }
        # Maven 复制资源不会自动删除旧指纹文件，交付副本只保留本次前端实际产物
        $publicPrefix = 'BOOT-INF/classes/public/'
        $publicRoot = Join-Path $backendRoot 'src\main\resources\public'
        $obsolete = New-Object 'System.Collections.Generic.List[System.IO.Compression.ZipArchiveEntry]'
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.StartsWith($publicPrefix) -and -not $entry.FullName.EndsWith('/')) {
                $relative = $entry.FullName.Substring($publicPrefix.Length)
                $source = Join-Path $publicRoot $relative
                if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
                    $obsolete.Add($entry)
                }
            }
        }
        foreach ($entry in $obsolete) {
            $entry.Delete()
        }
    } finally {
        $archive.Dispose()
    }
}

# 下载并验证官方运行环境，已经缓存的相同文件通过校验后直接复用
function Get-PackageRuntime {
    param([string]$ExistingArchive)

    $catalog = Invoke-RestMethod -Uri $runtimeCatalog -TimeoutSec 60
    if ($catalog.Count -eq 0) {
        throw '官方运行环境清单为空，请稍后重新打包'
    }
    $runtime = $catalog[0]
    $package = $runtime.binary.package
    $archivePath = Join-Path $cacheRoot $package.name
    if ($ExistingArchive) {
        $archivePath = (Resolve-Path -LiteralPath $ExistingArchive).Path
    } elseif (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
        Write-Host '正在下载教师运行所需的 Java 21 环境'
        Invoke-WebRequest -Uri $package.link -OutFile $archivePath -UseBasicParsing -TimeoutSec 300
    }
    $hash = Get-FileHash -LiteralPath $archivePath -Algorithm SHA256
    if ($hash.Hash -ne $package.checksum) {
        throw 'Java 运行环境校验失败，请删除下载的压缩包后重试'
    }
    return @{ archive = $archivePath; name = $package.name; checksum = $hash.Hash; source = $package.link }
}

if (-not $ConfigurationPath) {
    $ConfigurationPath = Join-Path $backendRoot 'src\main\resources\application-local.properties'
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $projectRoot 'dist'
}
$configuration = Read-PackageConfiguration -Path $ConfigurationPath
$project = Get-Content -LiteralPath (Join-Path $frontendRoot 'package.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$packageName = '4398-teacher-' + $project.version + '-windows-x64'
$packageRoot = Join-Path $OutputDirectory $packageName
$zipPath = Join-Path $OutputDirectory ($packageName + '.zip')
if ((Test-Path -LiteralPath $packageRoot) -or (Test-Path -LiteralPath $zipPath)) {
    throw "同版本交付目录已存在：$packageRoot，请选择其他输出目录，避免覆盖已交付文件"
}

if (-not $SkipBuild) {
    $env:JAVA_HOME = Find-BuildJava -RequestedHome $JavaHome
    Push-Location $frontendRoot
    try {
        if (-not (Test-Path -LiteralPath 'node_modules\vite\bin\vite.js')) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) {
                throw '前端依赖安装失败'
            }
        }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) {
            throw '网页或游戏构建失败'
        }
    } finally {
        Pop-Location
    }
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd -B -ntp '-Dmaven.test.skip=true' package
        if ($LASTEXITCODE -ne 0) {
            throw '后端打包失败'
        }
    } finally {
        Pop-Location
    }
}

$jarSource = Join-Path $backendRoot 'target\java-server-1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarSource -PathType Leaf)) {
    throw '没有找到已构建的后端 JAR，不能生成交付包'
}
$runtime = Get-PackageRuntime -ExistingArchive $RuntimeArchive
New-Item -ItemType Directory -Path $packageRoot | Out-Null
foreach ($directory in @('app', 'config', 'logs', 'data')) {
    New-Item -ItemType Directory -Path (Join-Path $packageRoot $directory) | Out-Null
}
$jarDestination = Join-Path $packageRoot 'app\4398.jar'
Copy-Item -LiteralPath $jarSource -Destination $jarDestination
Remove-EmbeddedSecrets -JarPath $jarDestination
Write-PackageConfiguration -Configuration $configuration -Destination (Join-Path $packageRoot 'config\application-local.properties')

# 使用独立展开目录，不会覆盖开发机的 Java 或之前生成的运行包
$runtimeDirectory = Join-Path $packageRoot 'runtime-source'
Expand-Archive -LiteralPath $runtime.archive -DestinationPath $runtimeDirectory
$runtimeCandidates = Get-ChildItem -LiteralPath $runtimeDirectory -Directory
if ($runtimeCandidates.Count -ne 1) {
    throw '运行环境目录结构与官方 ZIP 不符'
}
$runtimeDestination = Join-Path $packageRoot 'runtime'
$expandedRuntime = $runtimeCandidates[0].FullName
$expandedParent = [System.IO.Path]::GetDirectoryName($expandedRuntime)
$expectedParent = (Resolve-Path -LiteralPath $runtimeDirectory).Path
$destinationParent = [System.IO.Path]::GetDirectoryName($runtimeDestination)
if ($expandedParent -ne $expectedParent -or $destinationParent -ne $packageRoot) {
    throw '运行环境移动路径超出本次交付目录'
}
Move-Item -LiteralPath $runtimeCandidates[0].FullName -Destination $runtimeDestination
Remove-Item -LiteralPath $runtimeDirectory
foreach ($name in @('DeliveryTools.ps1', 'Start-Delivery.ps1', 'start.cmd', 'README.md')) {
    Copy-Item -LiteralPath (Join-Path $templateRoot $name) -Destination $packageRoot
}
Test-DeliveryFiles -Root $packageRoot
$jarHash = Get-FileHash -LiteralPath $jarDestination -Algorithm SHA256
$manifest = @{
    version = $project.version
    platform = 'windows-x64'
    runtime = @{
        name = $runtime.name
        checksum = $runtime.checksum
        source = $runtime.source
    }
    applicationSha256 = $jarHash.Hash
    onlineDatabaseRequired = $true
    onlineAiRequired = $true
}
$manifestText = ConvertTo-Json -InputObject $manifest -Depth 5
$manifestPath = Join-Path $packageRoot 'delivery.json'
$encoding = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($manifestPath, $manifestText, $encoding)
Write-Host '正在用包内 Java 验证网站、游戏资源和真实 AI，文字与图片请求各消耗一次额度'
$verificationScript = Join-Path $PSScriptRoot 'Test-Delivery.ps1'
& $verificationScript -PackageDirectory $packageRoot
# 检查结束后只保留不含凭据的结果清单，不把构建机的详细日志送入运行包
foreach ($name in @('startup-output.log', 'startup-error.log', 'java-server.log')) {
    $logPath = Join-Path $packageRoot ('logs\' + $name)
    if (Test-Path -LiteralPath $logPath -PathType Leaf) {
        Remove-Item -LiteralPath $logPath
    }
}
Write-Host '正在生成完整 ZIP，内含实际连接凭据，请勿公开分发'
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($packageRoot, $zipPath)
$zipHash = Get-FileHash -LiteralPath $zipPath -Algorithm SHA256
[System.IO.File]::WriteAllText($zipPath + '.sha256', $zipHash.Hash + '  ' + (Split-Path -Leaf $zipPath) + "`r`n", $encoding)
Write-Host ('教师运行包：' + $zipPath)
Write-Host ('可测试目录：' + $packageRoot)
