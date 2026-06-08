<#
.SYNOPSIS
  Free a TCP port (default 8080).

.DESCRIPTION
  Use this when the backend exited without releasing the port, or an orphaned
  IDEA / Maven JVM still holds it: finds the listening/owning process and force
  kills it so the port can be reused. Normal shutdown already releases the port
  via graceful shutdown (application.yml); this script is a fallback.

  ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI, so non-ASCII
  comments/strings (UTF-8 without BOM) would break parsing.

.PARAMETER Port
  Target port. Default 8080.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\free-port.ps1
  powershell -ExecutionPolicy Bypass -File scripts\free-port.ps1 -Port 8080
#>
param([int]$Port = 8080)

$connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' -or $_.State -eq 'Established' }

if (-not $connections) {
    Write-Host "Port $Port is not in use."
    exit 0
}

$ownerPids = $connections.OwningProcess | Sort-Object -Unique
foreach ($ownerPid in $ownerPids) {
    $proc = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Host "Killing PID=$($proc.Id) name=$($proc.ProcessName) holding port $Port"
        Stop-Process -Id $proc.Id -Force
    }
}
Write-Host "Port $Port released."
