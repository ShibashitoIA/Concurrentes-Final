param(
    [string]$FilePath = "test-data.txt",
    [string]$LeaderUrl = "http://localhost:8001"
)

if (!(Test-Path $FilePath)) {
    Write-Host "Error: No encuentro el archivo $FilePath"
    exit 1
}

$bytes = [IO.File]::ReadAllBytes((Resolve-Path $FilePath))
$name = Split-Path $FilePath -Leaf
$size = $bytes.Length

$md5 = [Security.Cryptography.MD5]::Create()
$hash = [BitConverter]::ToString($md5.ComputeHash($bytes)).Replace("-","").ToLower()

$base64 = [Convert]::ToBase64String($bytes)

$cmd = "STORE_FILE|$name|$hash|$size|$base64"

Write-Host "Enviando: $name ($size bytes)"
Write-Host "Checksum: $hash"

$result = Invoke-WebRequest -Uri "$LeaderUrl/command" -Method POST -Body $cmd
Write-Host $result.Content

Write-Host "`nVerifica en: $LeaderUrl/files"
