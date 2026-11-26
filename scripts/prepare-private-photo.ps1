$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$stream = $image = $bitmap = $graphics = $output = $parameters = $null
try {
    # Paths arrive through stdin, never a command line or a transcript.
    $request = [Console]::In.ReadToEnd() | ConvertFrom-Json
    $file = [IO.FileInfo]::new([string]$request.path)
    if (-not $file.Exists -or $file.Length -gt 20MB -or $file.Length -eq 0) {
        throw 'Unsupported photo'
    }
    $stream = $file.OpenRead()
    $image = [Drawing.Image]::FromStream($stream, $false, $true)
    if ($image.Width -gt 20000 -or $image.Height -gt 20000 -or
        ([long]$image.Width * $image.Height) -gt 40000000) { throw 'Photo too large' }
    if ($image.PropertyIdList -contains 274) {
        $orientation = [BitConverter]::ToUInt16($image.GetPropertyItem(274).Value, 0)
        $rotations = @{
            2='RotateNoneFlipX'; 3='Rotate180FlipNone'; 4='Rotate180FlipX'
            5='Rotate90FlipX'; 6='Rotate90FlipNone'; 7='Rotate270FlipX'; 8='Rotate270FlipNone'
        }
        if ($rotations.ContainsKey([int]$orientation)) {
            $image.RotateFlip([Enum]::Parse([Drawing.RotateFlipType], $rotations[[int]$orientation]))
        }
    }
    $scale = [Math]::Min(1.0, 1600.0 / [Math]::Max($image.Width, $image.Height))
    $width = [Math]::Max(1, [int][Math]::Round($image.Width * $scale))
    $height = [Math]::Max(1, [int][Math]::Round($image.Height * $scale))
    # A fresh bitmap cannot retain source EXIF/GPS/camera fields.
    $bitmap = [Drawing.Bitmap]::new($width, $height, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([Drawing.Color]::White)
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($image, 0, 0, $width, $height)
    $output = [IO.MemoryStream]::new()
    $codec = [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
        Where-Object MimeType -eq 'image/jpeg' | Select-Object -First 1
    $parameters = [Drawing.Imaging.EncoderParameters]::new(1)
    $parameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new([Drawing.Imaging.Encoder]::Quality, [long]80)
    $bitmap.Save($output, $codec, $parameters)
    if ($output.Length -gt 2MB) { throw 'Prepared photo too large' }
    # Only the private parent captures this output. It must never print it.
    [Console]::Out.Write([Convert]::ToBase64String($output.ToArray()))
} catch {
    [Console]::Error.WriteLine('No se pudo preparar la foto privada. No se mostro su ruta ni contenido.')
    exit 1
} finally {
    foreach ($resource in @($parameters, $graphics, $bitmap, $image, $stream, $output)) {
        if ($null -ne $resource) { $resource.Dispose() }
    }
}
