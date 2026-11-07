# ToothLock - Automated APK Build & Release Script

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "      ToothLock - יוצר קובץ ה-APK        " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$choice = Read-Host "בחר אפשרות:
1. בנה בענן (GitHub Actions) והורד את ה-APK המעודכן ביותר [מומלץ - ללא תלות בכלים במחשב]
2. קמפל מקומית במחשב (דורש JDK 17) והתקן ישירות לטלפון ב-ADB
הזן מספר (1 או 2)"

if ($choice -eq "2") {
    Write-Host "`n[מצב מקומי] מפעיל את תהליך הקימפול המקומי..." -ForegroundColor Yellow
    if (!(Get-Command java -ErrorAction SilentlyContinue)) {
        Write-Host "שגיאה: JDK 17 אינו מותקן במחשב." -ForegroundColor Red
        Write-Host "להתקנה מהירה הרץ: winget install Microsoft.OpenJDK.17" -ForegroundColor Yellow
        exit 1
    }
    & .\gradlew.bat assembleDebug
    $localApk = "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $localApk) {
        Copy-Item $localApk "ToothLock.apk" -Force
        Write-Host "`nקובץ ה-APK נוצר בהצלחה בנתיב הפרויקט: ToothLock.apk" -ForegroundColor Green
    }
    exit 0
}

# Cloud build via GitHub Actions (Default / Option 1)
Write-Host "`n[מצב ענן - GitHub Actions]" -ForegroundColor Cyan
Write-Host "מפעיל את ה-Workflow של בניית ה-APK ב-GitHub..." -ForegroundColor Yellow

# Ensure latest changes are committed & pushed
git add .
$status = git status --porcelain
if ($status) {
    git commit -m "feat: Update ToothLock app and trigger APK build"
    git push origin main
}

# Trigger workflow dispatch
gh workflow run build_apk.yml --ref main
Start-Sleep -Seconds 3

Write-Host "הבנייה הופעלה בענן! ממתין לסיום הבנייה ב-GitHub Actions..." -ForegroundColor Green
gh run watch

Write-Host "`nהבנייה הסתיימה! מוריד את ה-APK מתוך ה-Release..." -ForegroundColor Cyan

# Fetch latest release download URL
$releaseJson = gh release view --json assets,tagName | ConvertFrom-Json
$apkAsset = $releaseJson.assets | Where-Object { $_.name -like "*.apk" } | Select-Object -First 1

if ($apkAsset) {
    Write-Host "מוריד: $($apkAsset.name) (גרסה $($releaseJson.tagName))..." -ForegroundColor Yellow
    gh release download $releaseJson.tagName --pattern "*.apk" --clobber
    Write-Host "`nה-APK הורד בהצלחה לנתיב הפרויקט: ToothLock.apk!" -ForegroundColor Green
    Write-Host "קישור להורדה ישירה ב-GitHub Releases:" -ForegroundColor Cyan
    Write-Host $apkAsset.url -ForegroundColor White
} else {
    Write-Host "ה-Release נוצר, פותח את דף ה-Releases בדפדפן:" -ForegroundColor Yellow
    gh release view --web
}

# Check if phone is connected via ADB
$adb = "adb"
if (!(Get-Command adb -ErrorAction SilentlyContinue) -and (Test-Path "C:\platform-tools\adb.exe")) {
    $adb = "C:\platform-tools\adb.exe"
}

if (Get-Command $adb -ErrorAction SilentlyContinue) {
    $devices = & $adb devices | Select-String -Pattern "\bdevice\b" | Where-Object { $_ -notmatch "List of devices" }
    if ($devices -and (Test-Path "ToothLock.apk")) {
        $installNow = Read-Host "`nזוהה טלפון סמסונג מחובר ב-USB! האם להתקין את ה-APK עכשיו? (y/n)"
        if ($installNow -eq "y" -or $installNow -eq "Y") {
            Write-Host "מתקין את ToothLock.apk בטלפון..." -ForegroundColor Yellow
            & $adb install -r "ToothLock.apk"
            & $adb shell am start -n com.toothlock.app/.MainActivity
            Write-Host "ההתקנה הושלמה בהצלחה והאפליקציה נפתחה במכשיר!" -ForegroundColor Green
        }
    }
}
