param(
    [switch]$CheckOnly,
    [switch]$SmokeTest
)

$ErrorActionPreference = 'Stop'
$backendPort = 22222
$frontendPort = 3000
$frontendAddress = [Net.IPAddress]::Loopback
$startupTimeoutSeconds = 180
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'java-server\java-server'
$frontendRoot = Join-Path $projectRoot 'web-vue'
$logRoot = Join-Path $projectRoot 'logs'
$serviceJob = $null
$backendProcess = $null
$frontendProcess = $null

# 优先使用已配置的 JDK 21，也可从 PATH 或 Windows 的 Java 安装目录找到。
function Find-StartupJavaHome {
    $candidates = @($env:JAVA_HOME)
    foreach ($compiler in Get-Command javac.exe -All -CommandType Application -ErrorAction SilentlyContinue) {
        $candidates += Split-Path -Parent (Split-Path -Parent $compiler.Source)
    }
    $javaDirectory = Join-Path $env:ProgramFiles 'Java'
    foreach ($directory in Get-ChildItem -LiteralPath $javaDirectory -Directory -ErrorAction SilentlyContinue) {
        $candidates += $directory.FullName
    }
    foreach ($candidate in $candidates) {
        if (-not $candidate) {
            continue
        }
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath $release -PathType Leaf) -and
            ((Get-Content -LiteralPath $release -Raw) -match '(?m)^JAVA_VERSION="21(?:[.+-][^"]*)?"\s*$')) {
            return $candidate
        }
    }
    throw '找不到 JDK 21。请安装 JDK 21，或将 JAVA_HOME 指向已有的 JDK 21 安装目录。'
}

# 直接尝试绑定，关窗后给系统短暂时间释放端口。
function Assert-StartupPortAvailable {
    param([int]$Port, [Net.IPAddress]$Address)
    $deadline = [DateTime]::UtcNow.AddSeconds(3)
    do {
        $socket = [Net.Sockets.Socket]::new($Address.AddressFamily, 'Stream', 'Tcp')
        try {
            if ($Address -eq [Net.IPAddress]::IPv6Any) {
                $socket.DualMode = $true
            }
            $socket.ExclusiveAddressUse = $true
            $socket.Bind([Net.IPEndPoint]::new($Address, $Port))
            return
        } catch {
            $failure = $_.Exception.GetBaseException()
            if ($failure -isnot [Net.Sockets.SocketException] -or
                $failure.SocketErrorCode -notin @('AddressAlreadyInUse', 'AccessDenied')) {
                throw
            }
        } finally {
            $socket.Dispose()
        }
        Start-Sleep -Milliseconds 150
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "端口 $Port 无法绑定（$($failure.SocketErrorCode)）。请检查端口是否被其他服务占用。"
}

# 等待网页服务就绪；进程退出或等待超时时报告日志位置。
function Wait-ServiceReady {
    param([System.Threading.WaitHandle]$Process, [string]$Url, [string]$LogName, [string]$Method = 'Get')
    $deadline = (Get-Date).AddSeconds($startupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.WaitOne(0)) {
            throw "服务启动失败，请查看 $logRoot 中的 $LogName 日志。"
        }
        try {
            if ($Method -eq 'Post') {
                $response = Invoke-WebRequest -Uri $Url -Method Post -ContentType 'application/json' -Body '{"data":{}}' -UseBasicParsing -TimeoutSec 2
            } else {
                $response = Invoke-WebRequest -Uri $Url -Headers @{Accept = 'text/html'} -UseBasicParsing -TimeoutSec 2
            }
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "等待服务超时，请查看 $logRoot 中的 $LogName 日志。"
}

try {
    # 使用 JDK 21 运行源码，不使用可能过期的 JAR。
    $env:JAVA_HOME = Find-StartupJavaHome
    $nodePath = (Get-Command node.exe -ErrorAction Stop).Source
    $vitePath = Join-Path $frontendRoot 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path -LiteralPath $vitePath)) {
        throw '前端依赖尚未安装，请在 web-vue 目录执行 npm install。'
    }
    $localConfig = Join-Path $backendRoot 'src\main\resources\application-local.properties'
    if (-not (Test-Path -LiteralPath $localConfig)) {
        if (-not $env:DB_PASSWORD -or -not $env:JWT_SECRET) {
            throw '请先填写 application-local.properties，或设置 DB_PASSWORD 和 JWT_SECRET。'
        }
    }
    $backendAddress = [Net.IPAddress]::Any
    if ([Net.Sockets.Socket]::OSSupportsIPv6) {
        $backendAddress = [Net.IPAddress]::IPv6Any
    }
    Assert-StartupPortAvailable -Port $backendPort -Address $backendAddress
    Assert-StartupPortAvailable -Port $frontendPort -Address $frontendAddress
    if ($CheckOnly) {
        Write-Host '检查通过：JDK 21、Node.js、前端依赖、配置和端口均可用。'
        exit 0
    }

    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    if (-not ('Games4398.StartupProcessJob' -as [type])) {
        Add-Type -Path (Join-Path $PSScriptRoot 'StartupProcessJob.cs')
    }
    $serviceJob = [Games4398.StartupProcessJob]::new()
    $wrapperPath = Join-Path $backendRoot 'mvnw.cmd'
    $backendArguments = '/d /s /c ""' + $wrapperPath + '" -B -ntp -nsu spring-boot:run"'
    Write-Host "正在启动后端，端口 $backendPort。首次编译和下载依赖可能需要一些时间。"
    $backendProcess = $serviceJob.Start($env:ComSpec, $backendArguments, $backendRoot,
        (Join-Path $logRoot 'backend-output.log'), (Join-Path $logRoot 'backend-error.log'))
    Wait-ServiceReady -Process $backendProcess -Url "http://localhost:$backendPort/auth/getValidateCode" -LogName 'backend' -Method 'Post'

    Write-Host "正在启动前端，端口 $frontendPort。"
    $frontendArguments = '"' + $vitePath + '" --host ' + $frontendAddress + ' --port ' + $frontendPort + ' --strictPort'
    $frontendProcess = $serviceJob.Start($nodePath, $frontendArguments, $frontendRoot,
        (Join-Path $logRoot 'frontend-output.log'), (Join-Path $logRoot 'frontend-error.log'))
    $websiteUrl = "http://localhost:$frontendPort"
    Wait-ServiceReady -Process $frontendProcess -Url $websiteUrl -LogName 'frontend'
    Write-Host "启动完成：$websiteUrl"
    Write-Host "日志目录：$logRoot"
    if ($SmokeTest) {
        Write-Host '启动验证完成，正在关闭本次服务。'
        exit 0
    }
    Start-Process $websiteUrl
    Write-Host '请保留这个窗口。按回车、Ctrl+C 或关闭窗口可停止服务。'
    Read-Host | Out-Null
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($null -ne $serviceJob) { $serviceJob.Dispose() }
    if ($null -ne $frontendProcess) { $frontendProcess.Dispose() }
    if ($null -ne $backendProcess) { $backendProcess.Dispose() }
}
