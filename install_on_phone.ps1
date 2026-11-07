# ToothLock - Script to Build & Install directly to your Samsung phone

Write-Host "=== ToothLock - התקנה ישירה לטלפון ===" -ForegroundColor Cyan

# Locate ADB
$adb = "adb"
if (!(Get-Command adb -ErrorAction SilentlyContinue)) {
    if (Test-Path "C:\platform-tools\adb.exe") {
        $adb = "C:\platform-tools\adb.exe"
    } else {
        Write-Host "שגיאה: adb לא נמצא. ודא ש-ADB מותקן." -ForegroundColor Red
        exit 1
    }
}

# Check connected Android device
Write-Host "בודק חיבור מכשיר אנדרואיד..." -ForegroundColor Yellow
$devices = & $adb devices | Select-String -Pattern "\bdevice\b" | Where-Object { $_ -notmatch "List of devices" }

if (!$devices) {
    Write-Host "לא זוהה טלפון מחובר!" -ForegroundColor Red
    Write-Host "אנא חבר את ה-Galaxy שלך עם כבל USB ובדוק ש'ניפוי באגים ב-USB' (USB Debugging) מופעל בהגדרות המפתח." -ForegroundColor Yellow
    exit 1
}

Write-Host "מכשיר מחובר זוהה בהצלחה!" -ForegroundColor Green

# Check Java
if (!(Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "שים לב: דרוש JDK 17 כדי לקמפל את קוד ה-Android." -ForegroundColor Yellow
    Write-Host "תוכל להתקין בקלות על ידי הרצת הפקודה:" -ForegroundColor Cyan
    Write-Host "winget install Microsoft.OpenJDK.17" -ForegroundColor White
    exit 1
}

# Build debug APK
Write-Host "מקמפל את האפליקציה (Gradle build)..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    Write-Host "מתקין את ToothLock בטלפון..." -ForegroundColor Yellow
    & $adb install -r $apkPath
    Write-Host "מפעיל את האפליקציה בטלפון..." -ForegroundColor Green
    & $adb shell am start -n com.toothlock.app/.MainActivity
    Write-Host "ההתקנה הושלמה בהצלחה! האפליקציה פתוחה כעת במכשיר שלך." -ForegroundColor Cyan
} else {
    Write-Host "קובץ ה-APK לא נוצר. בדוק את פלט הקימפול." -ForegroundColor Red
}
