# 1. Environment Setup
$env:JAVA_HOME = "C:\Users\XEF1CNG\.jdks\corretto-21.0.5"
$MavenBin = "C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin"
$env:Path = "$MavenBin;$env:JAVA_HOME\bin;$env:Path"

# Set console encoding to UTF-8 for Chinese character display
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"

# # 2. Define Watcher Logic (Background Job)
# $WatchScript = {
#     $fsw = New-Object IO.FileSystemWatcher
#     $fsw.Path = "src"
#     $fsw.IncludeSubdirectories = $true
#     $fsw.EnableRaisingEvents = $true

#     $action = {
#         $timeStamp = Get-Date -Format "HH:mm:ss"
#         Write-Host "[$timeStamp] Change detected. Compiling..." -ForegroundColor Yellow
#         # Use full path to mvn.cmd for the background job
#         & "C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd" compile -Plocal -DskipTests
#     }

#     Register-ObjectEvent $fsw Changed -Action $action | Out-Null
#     while ($true) { Start-Sleep 2 }
# }

# # 3. Launch Watcher
# Write-Host ">>> Starting Background File Watcher..." -ForegroundColor Cyan
# $job = Start-Job -ScriptBlock $WatchScript

# 4. Run Spring Boot
Write-Host ">>> Launching Spring Boot with 'local' profile..." -ForegroundColor Cyan
try {
    # Quoting -D parameters is mandatory in PowerShell
    # compile (not clean) + fork=true prevents internal class loading issues
    # UTF-8 encoding fixes Chinese character display in Windows console
    mvn --no-transfer-progress compile spring-boot:run -Plocal `
        "-Dspring-boot.run.profiles=local" `
        -DskipTests `
        "-Dspring-boot.run.fork=true" `
        "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8 -DconsoleEncoding=UTF-8 -Dsun.stdout.encoding=UTF-8"
}
finally {
    Write-Host "`n>>> Stopping Watcher Job..." -ForegroundColor Red
    if ($job) {
        Stop-Job $job
        Remove-Job $job
    }
    Write-Host ">>> Dev Mode Closed Safely." -ForegroundColor Red
}
