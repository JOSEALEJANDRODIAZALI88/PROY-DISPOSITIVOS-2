param(
    [ValidateSet('Check', 'Install', 'Status', 'Remove')]
    [string] $Action = 'Status',
    [switch] $RunNow
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$project = 'ymfypllkcrqwfwihqzpf'
$taskName = 'PaceFitAI-Backup-' + $project
$description = 'PaceFit application-data backup v1; project ' + $project + '; current user only.'
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$sid = $identity.User.Value
$account = $identity.Name
$python = (& python -c 'import sys; print(sys.executable)').Trim()
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $python -PathType Leaf)) {
    throw 'Python is required; no scheduled task was changed.'
}
$runner = Join-Path $PSScriptRoot 'backup-maintenance.py'
$argumentText = '"' + $runner + '" maintain --scheduled'
$taskAction = New-ScheduledTaskAction -Execute $python -Argument $argumentText -WorkingDirectory $root
$triggers = @(
    (New-ScheduledTaskTrigger -Daily -At '03:00'),
    (New-ScheduledTaskTrigger -AtLogOn -User $account)
)
$principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10) -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries
$definition = New-ScheduledTask -Action $taskAction -Trigger $triggers -Principal $principal `
    -Settings $settings -Description $description

function Assert-OwnedTask($task) {
    # Get-ScheduledTask may display an unqualified username that is ambiguous
    # when computer and username are identical. The registered XML holds the SID.
    [xml] $registeredXml = Export-ScheduledTask -TaskPath '\' -TaskName $taskName
    $owner = [string] $registeredXml.Task.Principals.Principal.UserId
    if ($task.Description -ne $description -or $owner -ne $sid -or
        @($task.Actions).Count -ne 1 -or
        -not [string]::Equals($task.Actions[0].Execute, $python, [System.StringComparison]::OrdinalIgnoreCase) -or
        $task.Actions[0].Arguments -ne $argumentText -or
        -not [string]::Equals($task.Actions[0].WorkingDirectory, $root, [System.StringComparison]::OrdinalIgnoreCase) -or
        "$($task.Principal.RunLevel)" -ne 'Limited' -or
        "$($task.Principal.LogonType)" -ne 'Interactive') {
        throw 'An existing task differs from this owned limited-user definition. It was not changed.'
    }
    $daily = @($task.Triggers | Where-Object { $_.CimClass.CimClassName -eq 'MSFT_TaskDailyTrigger' })
    $logon = @($task.Triggers | Where-Object { $_.CimClass.CimClassName -eq 'MSFT_TaskLogonTrigger' })
    $logonOwner = if ($logon.Count -eq 1) { $logon[0].UserId } else { '' }
    if ($logonOwner -and $logonOwner -notmatch '^S-\d-') {
        $logonOwner = ([System.Security.Principal.NTAccount] $logonOwner).Translate(
            [System.Security.Principal.SecurityIdentifier]).Value
    }
    if (@($task.Triggers).Count -ne 2 -or $daily.Count -ne 1 -or $logon.Count -ne 1 -or
        -not $daily[0].Enabled -or -not $logon[0].Enabled -or
        $daily[0].DaysInterval -ne 1 -or $logonOwner -ne $sid -or
        ([DateTime] $daily[0].StartBoundary).TimeOfDay -ne ([TimeSpan]::FromHours(3)) -or
        -not $task.Settings.StartWhenAvailable -or -not $task.Settings.Enabled -or
        "$($task.Settings.MultipleInstances)" -ne 'IgnoreNew') {
        throw 'The owned task schedule/settings differ. They were not silently overwritten.'
    }
}

if ($Action -eq 'Check') {
    if ($definition.Principal.LogonType -ne 'Interactive' -or
        $definition.Principal.RunLevel -ne 'Limited' -or
        @($definition.Triggers).Count -ne 2 -or
        @($definition.Actions).Count -ne 1 -or
        $definition.Actions[0].Arguments -ne $argumentText) {
        throw 'Scheduled-task policy validation failed.'
    }
    Write-Output 'Task definition OK: current-user interactive token, limited privileges, no stored password; this check did not change task registration.'
    exit 0
}

$existing = Get-ScheduledTask -TaskPath '\' -TaskName $taskName -ErrorAction SilentlyContinue
if ($null -ne $existing) { Assert-OwnedTask $existing }
if ($Action -eq 'Remove') {
    if ($null -ne $existing) {
        Unregister-ScheduledTask -TaskPath '\' -TaskName $taskName -Confirm:$false
    }
    Write-Output 'Only the matching PaceFit scheduled task was removed; archives were not deleted.'
    exit 0
}
if ($Action -eq 'Install' -and $null -eq $existing) {
    Register-ScheduledTask -TaskPath '\' -TaskName $taskName -InputObject $definition | Out-Null
    $existing = Get-ScheduledTask -TaskPath '\' -TaskName $taskName
    Assert-OwnedTask $existing
}
if ($null -eq $existing) {
    Write-Output '{"scheduled_task_installed":false}'
    exit 0
}

if ($RunNow) {
    $stateDirectory = Join-Path $root 'supabase/.temp/backup-maintenance'
    New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
    $statusPath = Join-Path $stateDirectory 'status.json'
    $previousRun = $null
    if (Test-Path -LiteralPath $statusPath) {
        $previousRun = (Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json).run_id
    }
    # Wait on a filesystem event, not a sleep/poll loop. The runner atomically writes
    # this status once, after completing or failing the actual maintenance.
    $watcher = [System.IO.FileSystemWatcher]::new($stateDirectory, 'status.json')
    try {
        $watcher.EnableRaisingEvents = $true
        Start-ScheduledTask -TaskPath '\' -TaskName $taskName
        $change = $watcher.WaitForChanged(
            [System.IO.WatcherChangeTypes]::Created -bor [System.IO.WatcherChangeTypes]::Renamed,
            600000)
        if ($change.TimedOut -or -not (Test-Path -LiteralPath $statusPath)) {
            throw 'No completed maintenance was observed; inspect the owned task status.'
        }
        $result = Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json
        if ($result.project -ne $project -or $result.run_id -eq $previousRun -or
            -not $result.invoked_by_registered_task -or -not $result.completed_at -or
            -not $result.retention_completed -or -not $result.backup_completed -or
            $result.capture_scope -ne 'all_active_application_accounts' -or
            $result.failure_phase) {
            throw 'Scheduled maintenance did not report a verified successful operation.'
        }
        $proof = [ordered] @{
            project = $project
            task_registered_and_action_verified = $true
            current_user_only = $true
            stored_user_password = $false
            limited_privileges = $true
            daily_and_logon_triggers = $true
            start_when_available = $true
            registered_task_executed = $true
            maintenance_run_id = $result.run_id
            maintenance_completed_at = $result.completed_at
            retention_completed = $result.retention_completed
            managed_expired_files_removed = $result.managed_expired_files_removed
            backup_completed = $result.backup_completed
            backup_created = $result.backup_created
            recent_archive_decrypted_and_validated = $result.recent_archive_decrypted_and_validated
            capture_scope = $result.capture_scope
            provider_or_auth_backup = $false
            computer_off_or_logged_out_retention_guaranteed = $false
            recorded_at = [DateTimeOffset]::Now.ToString('o')
        }
        $proofDirectory = Join-Path $root 'app/build/verification/backup-operation'
        New-Item -ItemType Directory -Path $proofDirectory -Force | Out-Null
        $proofPath = Join-Path $proofDirectory ('result-' + [guid]::NewGuid().ToString('N') + '.json')
        $proof | ConvertTo-Json | Set-Content -LiteralPath $proofPath -Encoding UTF8
        $proof | ConvertTo-Json
    }
    finally {
        $watcher.Dispose()
    }
}
else {
    Write-Output '{"scheduled_task_installed":true,"current_user_only":true,"stored_user_password":false,"limited_privileges":true}'
}
