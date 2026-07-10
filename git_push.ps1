$ErrorActionPreference = "Continue"
Set-Location "D:\qwq\项目\mapadd"

Write-Host "=== Git Status ==="
git status

Write-Host "`n=== Adding files ==="
git add -A

Write-Host "`n=== Committing ==="
git commit -m "fix: 添加 WarMenu 缺失的 DiplomacyService 和 DiplomacyRelation 导入"

Write-Host "`n=== Pushing to GitHub ==="
git push origin main

Write-Host "`n=== Done! ==="
Pause
