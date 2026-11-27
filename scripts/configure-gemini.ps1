param([switch]$CheckUi)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()
$root = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $root '.env'
if ((Test-Path $destination) -and -not $CheckUi) {
    [void][Windows.Forms.MessageBox]::Show(
        'Ya existe .env. No se sobrescribio ninguna clave. Avisa en el chat SIN enviar secretos.',
        'PaceFit - Configuracion privada')
    exit 1
}

$form = New-Object Windows.Forms.Form
$form.Text = 'PaceFit - Pegar nueva clave privada'
$form.ClientSize = New-Object Drawing.Size(690, 460)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true
$form.Font = New-Object Drawing.Font('Segoe UI', 10)
$form.AutoScaleMode = 'Dpi'
$form.AutoScaleDimensions = New-Object Drawing.SizeF(96, 96)

$instructions = New-Object Windows.Forms.Label
$instructions.SetBounds(20, 15, 650, 85)
$instructions.Text = "1. Revoca la clave enviada al chat y crea OTRA en Google AI Studio.`n2. Pega la nueva abajo con Ctrl+V. Veras puntos, no la clave.`n3. Valida, elige un modelo gratuito y pulsa Guardar y conectar."
$field = New-Object Windows.Forms.TextBox
$field.SetBounds(20, 105, 650, 30)
$field.UseSystemPasswordChar = $true
$field.ShortcutsEnabled = $true
$field.MaxLength = 512
$field.AccessibleName = 'Nueva clave Gemini privada. Pega con Control V.'
$free = New-Object Windows.Forms.CheckBox
$free.SetBounds(20, 145, 650, 45)
$free.Text = 'Confirmo que el proyecto y el modelo elegido tienen cuota gratuita. No activar pagos.'
$validate = New-Object Windows.Forms.Button
$validate.SetBounds(20, 200, 180, 35)
$validate.Text = 'Validar clave'
$modelLabel = New-Object Windows.Forms.Label
$modelLabel.SetBounds(20, 247, 650, 25)
$modelLabel.Text = 'Modelo Flash disponible: comprueba su cuota gratuita en AI Studio.'
$modelsBox = New-Object Windows.Forms.ComboBox
$modelsBox.SetBounds(20, 277, 650, 35)
$modelsBox.DropDownStyle = 'DropDownList'
$modelsBox.Enabled = $false
$status = New-Object Windows.Forms.Label
$status.SetBounds(20, 322, 650, 65)
$status.Text = 'La clave no se mostrara ni entrara en Android o Git. Consultar modelos no genera contenido.'
$save = New-Object Windows.Forms.Button
$save.SetBounds(420, 403, 250, 38)
$save.Text = 'Guardar y conectar'
$save.Enabled = $false
$cancel = New-Object Windows.Forms.Button
$cancel.SetBounds(20, 403, 120, 38)
$cancel.Text = 'Cerrar'
$cancel.DialogResult = [Windows.Forms.DialogResult]::Cancel
$form.CancelButton = $cancel
$form.Controls.AddRange(@($instructions, $field, $free, $validate, $modelLabel, $modelsBox, $status, $save, $cancel))
$script:validatedKey = $null
$script:savedHere = $false

function Invoke-PrivateGeminiValidation([string]$PrivateKey) {
    $process = $null
    try {
        # The key crosses a private pipe, never arguments, clipboard reads or a key file.
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = 'python'
        $start.Arguments = '"' + (Join-Path $PSScriptRoot 'gemini_validation.py') + '"'
        $start.WorkingDirectory = $root
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardInput = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $start
        [void]$process.Start()
        $process.StandardInput.WriteLine((@{ apiKey = $PrivateKey } | ConvertTo-Json -Compress))
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(45000)) {
            $process.Kill()
            return [pscustomobject]@{ ok = $false; code = 'VALIDATOR_TIMEOUT' }
        }
        if ($process.ExitCode -ne 0) {
            return [pscustomobject]@{ ok = $false; code = 'LOCAL_VALIDATION_ERROR' }
        }
        return ($process.StandardOutput.ReadToEnd() | ConvertFrom-Json)
    } catch {
        return [pscustomobject]@{ ok = $false; code = 'LOCAL_VALIDATION_ERROR' }
    } finally {
        $PrivateKey = $null
        if ($null -ne $process) { $process.Dispose() }
    }
}

function Write-PublicGeminiDiagnostic($Result) {
    # Do not persist the private request, key, provider message, headers or metadata.
    $record = @{
        operation = 'Google model list; no inference'
        recordedAt = [DateTime]::UtcNow.ToString('o')
        ok = [bool]$Result.ok
    }
    if ($Result.code) { $record.code = [string]$Result.code }
    if ($Result.httpStatus) { $record.httpStatus = [int]$Result.httpStatus }
    if ($Result.googleStatus) { $record.googleStatus = [string]$Result.googleStatus }
    try {
        $directory = Join-Path $root 'supabase\.temp'
        [void][IO.Directory]::CreateDirectory($directory)
        [IO.File]::WriteAllText((Join-Path $directory 'gemini-validation.json'),
            ($record | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
    } catch {
        # Optional public diagnostics must not break validation or disclose an exception.
    }
}

$field.Add_TextChanged({
    $script:validatedKey = $null
    $save.Enabled = $false
    $modelsBox.Enabled = $false
    $modelsBox.Items.Clear()
})
$free.Add_CheckedChanged({ $save.Enabled = $free.Checked -and $null -ne $script:validatedKey })
$validate.Add_Click({
    if (-not $free.Checked) {
        $status.Text = 'Comprueba el nivel gratuito y marca la casilla. No se activara facturacion.'
        return
    }
    $key = $field.Text.Trim()
    # Do not assume the legacy AIza prefix: Google now also issues authorization keys.
    if ($key -cnotmatch '^[A-Za-z0-9_.-]{20,512}$') {
        $status.Text = 'Pega una clave completa, sin espacios ni saltos de linea.'
        return
    }
    $save.Enabled = $false
    $script:validatedKey = $null
    $modelsBox.Enabled = $false
    $modelsBox.Items.Clear()
    $form.UseWaitCursor = $true
    $validate.Enabled = $false
    $status.Text = 'Comprobando con Google por HTTPS. No se esta generando contenido...'
    $form.Refresh()
    try {
        # Use the same certificate-verified validator as server-secret deployment.
        $result = Invoke-PrivateGeminiValidation $key
        Write-PublicGeminiDiagnostic $result
        if (-not $result.ok) {
            $advice = switch ($result.code) {
                'INVALID_KEY_FORMAT' { 'Pega la clave completa, sin espacios.' }
                'KEY_REPORTED_LEAKED' { 'Google marca esta clave como filtrada. Revocala y crea otra.' }
                'API_KEY_INVALID' { 'Google no reconoce la clave. Copia una nueva desde AI Studio.' }
                'API_KEY_EXPIRED' { 'La clave caduco. Crea otra en AI Studio.' }
                'SERVICE_DISABLED' { 'La API Generative Language no esta habilitada en ese proyecto. No actives pagos.' }
                'API_KEY_SERVICE_BLOCKED' { 'La clave no permite la API Generative Language. Revisa sus permisos.' }
                'API_KEY_HTTP_REFERRER_BLOCKED' { 'Las restricciones web no permiten este cliente de servidor.' }
                'API_KEY_IP_ADDRESS_BLOCKED' { 'Las restricciones de IP no permiten este cliente.' }
                'ACCESS_TOKEN_SCOPE_INSUFFICIENT' { 'La cuenta asociada no tiene permisos suficientes.' }
                'IAM_PERMISSION_DENIED' { 'Revisa los permisos de la cuenta asociada a la clave.' }
                'BILLING_DISABLED' { 'No actives pagos. Revisa la elegibilidad del nivel gratuito.' }
                'NO_COMPATIBLE_FLASH_MODEL' { 'Google acepto la consulta, pero no aparecen modelos Flash estables compatibles.' }
                'NETWORK_TLS_OR_TIMEOUT' { 'No se pudo completar HTTPS. Revisa conexion, certificado o proxy.' }
                'VALIDATOR_TIMEOUT' { 'Se agoto el tiempo de conexion. Revisa la red.' }
                'LOCAL_VALIDATION_ERROR' { 'Fallo el validador local. Revisa que Python este disponible.' }
                'INVALID_PROVIDER_RESPONSE' { 'La respuesta no tiene el formato esperado.' }
                default { 'Revisa la clave, permisos y elegibilidad gratuita en AI Studio. No actives pagos.' }
            }
            $diagnostic = [string]$result.code
            if ($result.httpStatus) { $diagnostic += ' HTTP ' + [int]$result.httpStatus }
            if ($result.googleStatus) { $diagnostic += ' ' + [string]$result.googleStatus }
            $status.Text = "$diagnostic. $advice No se guardo la clave."
            return
        }
        foreach ($candidate in $result.models) { [void]$modelsBox.Items.Add([string]$candidate) }
        $modelsBox.SelectedIndex = 0
        $modelsBox.Enabled = $true
        $script:validatedKey = $key
        $save.Enabled = $true
        $status.Text = 'Clave aceptada por Google. Elige un modelo con cuota gratuita. Aun no se ha guardado.'
    } catch {
        $status.Text = 'Fallo el validador local. No se guardo la clave. Avisa sin enviar secretos.'
    } finally {
        $key = $null
        $validate.Enabled = $true
        $form.UseWaitCursor = $false
    }
})
$save.Add_Click({
    if (-not $free.Checked -or $null -eq $script:validatedKey -or $modelsBox.SelectedIndex -lt 0) { return }
    $save.Enabled = $false
    $validate.Enabled = $false
    $form.UseWaitCursor = $true
    $status.Text = 'Guardando en privado y conectando al servidor autorizado. No se solicitara inferencia...'
    $form.Refresh()
    try {
        if (-not $script:savedHere) {
            # CreateNew prevents overwriting another configuration created while this dialog was open.
            $stream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
            try {
                $text = "GEMINI_API_KEY=$($script:validatedKey)`nGEMINI_MODEL=$($modelsBox.SelectedItem)`n"
                $bytes = [Text.UTF8Encoding]::new($false).GetBytes($text)
                $stream.Write($bytes, 0, $bytes.Length)
                $stream.Flush($true)
                $script:savedHere = $true
            } finally { $stream.Dispose(); $text = $null; $bytes = $null }
            $field.Enabled = $false
            $modelsBox.Enabled = $false
            $free.Enabled = $false
        }
        & python (Join-Path $PSScriptRoot 'cloud-admin.py') 'deploy-gemini' 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            $status.Text = 'Guardado localmente, pero la conexion al servidor necesita revision. Avisa en el chat sin enviar la clave.'
            $save.Text = 'Reintentar conexion'
            $save.Enabled = $true
            return
        }
        $status.Text = 'Gemini conectado al servidor. No se genero contenido. Cierra y avisa LISTO en el chat.'
        $field.Clear()
        $script:validatedKey = $null
        $save.Enabled = $false
    } catch {
        $status.Text = 'No se completo la configuracion. No se sobrescribieron secretos existentes ni se mostro la clave.'
    } finally {
        $form.UseWaitCursor = $false
        $validate.Enabled = -not $script:savedHere
    }
})

if ($CheckUi) {
    if (-not $field.UseSystemPasswordChar -or -not $field.ShortcutsEnabled) { throw 'Unsafe key entry control.' }
    if ($form.Controls.Count -ne 9 -or $save.Enabled) { throw 'Invalid initial UI state.' }
    $bridge = Invoke-PrivateGeminiValidation 'not-a-valid-key'
    if ($bridge.ok -or $bridge.code -ne 'INVALID_KEY_FORMAT') { throw 'Private validation bridge failed.' }
    $form.Dispose()
    Write-Output 'Private Gemini dialog controls and stdin bridge OK; no clipboard access, real key, network call or file write.'
    exit 0
}
try { [void]$form.ShowDialog() }
finally { $field.Clear(); $script:validatedKey = $null; $form.Dispose() }
