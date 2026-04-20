# 1. Environment Setup
$env:JAVA_HOME = "C:\Users\XEF1CNG\.jdks\corretto-21.0.5"
$MavenBin = "C:\Users\XEF1CNG\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin"
$env:Path = "$MavenBin;$env:JAVA_HOME\bin;$env:Path"

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
# SPRING_PROFILES_ACTIVE env var is the highest-priority profile source and survives any
# DevTools restart classloader split; spring-boot.run.profiles is unreliable in 4.x without fork.
$env:SPRING_PROFILES_ACTIVE = "local"
# spring.devtools.restart.enabled=false: prevents the restart classloader from shadowing
# target/classes config files (which caused port 8080 / datasource-not-found symptoms).
Write-Host ">>> Launching Spring Boot with 'local' profile..." -ForegroundColor Cyan
try {
    mvn --no-transfer-progress spring-boot:run -Plocal -DskipTests
}
finally {
    Write-Host "`n>>> Stopping Watcher Job..." -ForegroundColor Red
    if ($job) {
        Stop-Job $job
        Remove-Job $job
    }
    Write-Host ">>> Dev Mode Closed Safely." -ForegroundColor Red
}