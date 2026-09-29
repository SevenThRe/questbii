# deploy.ps1 — 重新构建并把 questbii jar 部署到整合包 mods/
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& .\gradlew.bat build --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# 工程位于 mods/.trae/project/questbii，mods 根目录需上溯三级
Copy-Item "build\libs\questbii-1.0.0.jar" "..\..\..\" -Force
Write-Host "deployed questbii-1.0.0.jar to mods/"
