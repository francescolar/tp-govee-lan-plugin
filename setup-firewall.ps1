# Govee LAN Plugin - Windows Firewall Setup
# Run this once to allow the plugin to communicate with Govee devices on the local network.
# Requires administrator privileges (will prompt UAC automatically).

# Self-elevate if not already running as admin
if (-not ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe "-ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

$rules = @(
    @{ Name = "Govee LAN - Discovery responses (UDP 4002 inbound)";  Port = 4002; Direction = "Inbound" },
    @{ Name = "Govee LAN - Device scan (UDP 4001 outbound)";         Port = 4001; Direction = "Outbound" },
    @{ Name = "Govee LAN - Device control (UDP 4003 outbound)";      Port = 4003; Direction = "Outbound" }
)

$added   = 0
$skipped = 0

foreach ($rule in $rules) {
    $existing = Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "Already exists: $($rule.Name)" -ForegroundColor Yellow
        $skipped++
    } else {
        New-NetFirewallRule `
            -DisplayName $rule.Name `
            -Direction   $rule.Direction `
            -Protocol    UDP `
            -LocalPort   $rule.Port `
            -Action      Allow `
            -Profile     Private `
            | Out-Null
        Write-Host "Added: $($rule.Name)" -ForegroundColor Green
        $added++
    }
}

Write-Host ""
Write-Host "Done. Added: $added  Already present: $skipped" -ForegroundColor Cyan
Write-Host "Press any key to close..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
