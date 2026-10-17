param([string]$JsonJar)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
if(-not $JsonJar){$JsonJar=Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.json/json') -Filter '*.jar' -Recurse | Select-Object -First 1 -ExpandProperty FullName}
if(-not $JsonJar){throw 'Pass -JsonJar pointing to an existing org.json JAR. No downloads are performed.'}
$out=Join-Path $repo '.tmp-ai-config-check/classes';$cache=Join-Path $repo '.tmp-ai-config-check/cache';New-Item -ItemType Directory -Force $out,$cache | Out-Null
$sources=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
$sources+=Join-Path $repo 'TMessagesProj/src/main/java/app/nimarkogram/messenger/ai/AutoReplyConfig.java'
& javac -encoding UTF-8 -cp $JsonJar -d $out $sources
if($LASTEXITCODE -ne 0){throw 'Config compilation failed'}
& java "-Djava.io.tmpdir=$cache" -cp "$out;$JsonJar" ConfigCheck
if($LASTEXITCODE -ne 0){throw 'Config assertions failed'}
