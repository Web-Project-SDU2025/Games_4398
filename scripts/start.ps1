param(
    [switch]$CheckOnly,
    [switch]$SmokeTest
)

$ErrorActionPreference = 'Stop'
$backendPort = 22222
$frontendPort = 3000
$startupTimeoutSeconds = 180
$preferredJavaHome = 'C:\Program Files\Java\jdk-21.0.12'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'java-server\java-server'
$frontendRoot = Join-Path $projectRoot 'web-vue'
$logRoot = Join-Path $projectRoot 'logs'
$backendProcess = $null
$frontendProcess = $null

# 检查端口是否已有程序监听，返回占用端口的进程编号。
function Get-PortOwner {
    param([int]$Port)
    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        return $listener.OwningProcess
    }
    return $null
}

# 等待网页服务就绪；进程退出或等待超时时报告日志位置。
function Wait-ServiceReady {
    param([System.Diagnostics.Process]$Process, [string]$Url, [string]$LogName, [string]$Method = 'Get')
    $deadline = (Get-Date).AddSeconds($startupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) {
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

# 只关闭本次创建的服务进程及其子进程，不关闭其他占用端口的程序。
function Stop-OwnedProcess {
    param([System.Diagnostics.Process]$Process)
    if ($null -eq $Process) {
        return
    }
    $Process.Refresh()
    if (-not $Process.HasExited) {
        & taskkill.exe /PID $Process.Id /T /F 2>&1 | Out-Null
    }
}

try {
    # 使用 JDK 21 运行源码，不使用可能过期的 JAR。
    if (Test-Path -LiteralPath $preferredJavaHome) {
        $env:JAVA_HOME = $preferredJavaHome
    }
    if (-not $env:JAVA_HOME) {
        throw '请先设置 JAVA_HOME，指向 JDK 21 的安装目录。'
    }
    $javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $javaVersion = & $javaPath --version | Out-String
    if ($javaVersion -notmatch '(java|openjdk) 21\.') {
        throw '项目需要 JDK 21，请检查 JAVA_HOME。'
    }
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
    foreach ($port in @($backendPort, $frontendPort)) {
        $ownerId = Get-PortOwner -Port $port
        if ($null -ne $ownerId) {
            throw "端口 $port 已由进程 $ownerId 占用。请关闭原来的 IDEA 后端、JAR 或 Vue 服务后再启动。"
        }
    }
    if ($CheckOnly) {
        Write-Host '检查通过：JDK 21、Node.js、前端依赖、配置和端口均可用。'
        exit 0
    }

    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $wrapperPath = Join-Path $backendRoot 'mvnw.cmd'
    $backendArguments = '/d /s /c ""' + $wrapperPath + '" -B -ntp -nsu spring-boot:run"'
    Write-Host "正在启动后端，端口 $backendPort。首次编译和下载依赖可能需要一些时间。"
    $backendProcess = Start-Process -FilePath $env:ComSpec -ArgumentList $backendArguments -WorkingDirectory $backendRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'backend-output.log') -RedirectStandardError (Join-Path $logRoot 'backend-error.log') -PassThru
    Wait-ServiceReady -Process $backendProcess -Url "http://localhost:$backendPort/auth/getValidateCode" -LogName 'backend' -Method 'Post'

    Write-Host "正在启动前端，端口 $frontendPort。"
    $frontendArguments = '"' + $vitePath + '" --host 127.0.0.1 --port ' + $frontendPort + ' --strictPort'
    $frontendProcess = Start-Process -FilePath $nodePath -ArgumentList $frontendArguments -WorkingDirectory $frontendRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'frontend-output.log') -RedirectStandardError (Join-Path $logRoot 'frontend-error.log') -PassThru
    $websiteUrl = "http://localhost:$frontendPort"
    Wait-ServiceReady -Process $frontendProcess -Url $websiteUrl -LogName 'frontend'
    Write-Host "启动完成：$websiteUrl"
    Write-Host "日志目录：$logRoot"
    if ($SmokeTest) {
        Write-Host '启动验证完成，正在关闭本次服务。'
        exit 0
    }
    Start-Process $websiteUrl
    Write-Host '请保留这个窗口。按回车停止服务，或按 Ctrl+C 退出。'
    Read-Host | Out-Null
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    Stop-OwnedProcess -Process $frontendProcess
    Stop-OwnedProcess -Process $backendProcess
}
