$ErrorActionPreference = 'Stop'

# 交付服务只监听本机，端口冲突时从默认端口向后寻找空位
$DeliveryAddress = '127.0.0.1'
$DeliveryDefaultPort = 22222
$DeliveryPortAttempts = 20
$DeliveryStartupSeconds = 180
$DeliveryProbeSeconds = 3

# 读取交付配置的键值，不将数据库密码或云端密钥显示到终端
function Read-DeliveryProperties {
    param([string]$Path)

    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $text = $line.Trim()
        if ($text.Length -eq 0 -or $text.StartsWith('#') -or $text.StartsWith('!')) {
            continue
        }
        $separator = $text.IndexOf('=')
        if ($separator -gt 0) {
            $name = $text.Substring(0, $separator)
            $value = $text.Substring($separator + 1)
            $properties[$name.Trim()] = $value.Trim()
        }
    }
    return $properties
}

# 核对运行包和必需配置，缺失时明确报错，不读取本机开发目录作为替代
function Test-DeliveryFiles {
    param([string]$Root)

    foreach ($relativePath in @('runtime\bin\java.exe', 'app\4398.jar', 'config\application-local.properties')) {
        $filename = Join-Path $Root $relativePath
        if (-not (Test-Path -LiteralPath $filename -PathType Leaf)) {
            throw "运行包缺少文件：$relativePath，请完整解压 ZIP"
        }
    }
    $configurationPath = Join-Path $Root 'config\application-local.properties'
    $configuration = Read-DeliveryProperties -Path $configurationPath
    foreach ($name in @('spring.datasource.password', 'security.jwt.secret-key', 'app.ai.api-key')) {
        $value = $configuration[$name]
        if (-not $value -or $value.StartsWith('YOUR_') -or $value.Contains('${')) {
            throw "交付配置缺少有效参数：$name，请联系提交者重新打包"
        }
    }
}

# 不停止已经占用端口的程序，只在相邻端口中选择本次服务的位置
function Get-DeliveryPort {
    param([int]$PreferredPort = $DeliveryDefaultPort)

    $port = $PreferredPort
    for ($attempt = 0; $attempt -lt $DeliveryPortAttempts; $attempt = $attempt + 1) {
        $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if (-not $listener) {
            return $port
        }
        $port = $port + 1
    }
    throw "端口 $PreferredPort 附近没有空闲位置，请关闭之前启动的本项目后重试"
}

# 使用包内 Java 和独立工作目录启动服务，日志和数据不会写入开发工作区
function Start-DeliveryServer {
    param([string]$Root, [int]$Port)

    Test-DeliveryFiles -Root $Root
    foreach ($directory in @('logs', 'data', 'data\attachments')) {
        $directoryPath = Join-Path $Root $directory
        New-Item -ItemType Directory -Path $directoryPath -Force | Out-Null
    }
    $java = Join-Path $Root 'runtime\bin\java.exe'
    $jar = Join-Path $Root 'app\4398.jar'
    $arguments = '-Dfile.encoding=UTF-8 -jar "' + $jar + '"'
    $arguments = $arguments + ' --spring.config.additional-location=file:./config/application-local.properties'
    $arguments = $arguments + ' --server.address=' + $DeliveryAddress
    $arguments = $arguments + ' --server.port=' + $Port
    $outputLog = Join-Path $Root 'logs\startup-output.log'
    $errorLog = Join-Path $Root 'logs\startup-error.log'
    return Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $Root -WindowStyle Hidden -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog -PassThru
}

# 等待实际后端响应，启动失败时保留日志，避免只打开一个无法访问的浏览器页面
function Wait-DeliveryReady {
    param([System.Diagnostics.Process]$Process, [int]$Port)

    $deadline = (Get-Date).AddSeconds($DeliveryStartupSeconds)
    $address = 'http://' + $DeliveryAddress + ':' + $Port
    while ((Get-Date) -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) {
            throw '服务启动失败，请查看 logs 中的 startup-output.log 和 startup-error.log，确认数据库网络与配置有效'
        }
        try {
            $response = Invoke-RestMethod -Uri ($address + '/auth/getValidateCode') -Method Post -ContentType 'application/json' -Body '{"data":{}}' -TimeoutSec $DeliveryProbeSeconds
            if ($response.code -eq 0 -and $response.data.validateCodeId) {
                return $address
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw '服务启动等待超时，请检查网络、数据库和 logs 中的启动日志'
}

# 只结束本脚本创建的 Java 进程，不影响 IDEA 或其他已运行的网站
function Stop-DeliveryServer {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return
    }
    $Process.Refresh()
    if (-not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit()
    }
    $Process.Dispose()
}
