[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:5055')

$ErrorActionPreference = 'Stop'
$health = Invoke-RestMethod -Uri "$BaseUrl/health" -TimeoutSec 5
if ($health.status -ne 'ok') { throw 'Health endpoint is degraded.' }
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$get = Invoke-RestMethod -Uri "$BaseUrl/?id=AND-0000000000000001&lat=10.1&lon=106.1&timestamp=$timestamp&speed=1&accuracy=5" -TimeoutSec 5
if (-not $get.accepted) { throw 'OsmAnd GET smoke test was rejected.' }
$body = @{ id='AND-0000000000000002'; lat=10.2; lon=106.2; timestamp=$timestamp; speed=2; accuracy=6 } | ConvertTo-Json
$post = Invoke-RestMethod -Uri "$BaseUrl/api/locations" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 5
if (-not $post.accepted) { throw 'JSON POST smoke test was rejected.' }
Invoke-RestMethod -Uri "$BaseUrl/api/stats" -TimeoutSec 5
