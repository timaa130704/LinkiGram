param([string]$JsonJar)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
if (-not $JsonJar) {
    $JsonJar = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.json/json') -Filter '*.jar' -Recurse | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $JsonJar -or -not (Test-Path -LiteralPath $JsonJar)) { throw 'Pass -JsonJar pointing to an existing org.json JAR. No downloads are performed.' }
$out = Join-Path $repo '.tmp-ai-client-check/classes'
$cache = Join-Path $repo '.tmp-ai-client-check/cache'
New-Item -ItemType Directory -Force $out, $cache | Out-Null
& javac -encoding UTF-8 -cp $JsonJar -d $out (Join-Path $PSScriptRoot 'android/os/SystemClock.java') (Join-Path $PSScriptRoot 'org/telegram/messenger/ApplicationLoader.java') (Join-Path $repo 'TMessagesProj/src/main/java/app/nimarkogram/messenger/ai/AutoReplyClient.java') (Join-Path $PSScriptRoot 'ClientCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Client compilation failed' }
& java "-Djava.io.tmpdir=$cache" -cp "$out;$JsonJar" ClientCheck
if ($LASTEXITCODE -ne 0) { throw 'Client assertions failed' }
