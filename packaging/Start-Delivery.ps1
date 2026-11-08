param([int]$Port = 22222)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$tools = Join-Path $root 'DeliveryTools.ps1'
. $tools
$server = $null

try {
    $selectedPort = Get-DeliveryPort -PreferredPort $Port
    Write-Host '正在启动 4398，首次连接数据库可能需要一些时间'
    $server = Start-DeliveryServer -Root $root -Port $selectedPort
    $address = Wait-DeliveryReady -Process $server -Port $selectedPort
    Write-Host ('启动成功：' + $address)
    Write-Host '请登录已有账户，右上角可以切换账户'
    Write-Host '请保留这个窗口，按回车停止本次服务'
    Start-Process ($address + '/Login')
    Read-Host | Out-Null
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Read-Host '按回车关闭窗口' | Out-Null
    exit 1
} finally {
    Stop-DeliveryServer -Process $server
}
