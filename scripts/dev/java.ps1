function Set-CraveJava {
    $jdkCandidates = @($env:JAVA_HOME, 'C:\Program Files\Java\jdk-21', 'C:\Program Files\Java\jdk-17')
    foreach ($jdkCandidate in $jdkCandidates) {
        if (!$jdkCandidate) { continue }
        $javaExecutable = Join-Path $jdkCandidate 'bin/java.exe'
        $releaseFile = Join-Path $jdkCandidate 'release'
        if (!(Test-Path -LiteralPath $javaExecutable) -or !(Test-Path -LiteralPath $releaseFile)) { continue }
        # The JDK release file also supports GA versions such as "21" without a patch suffix.
        # Avoid java -version: its normal stderr output is a terminating error in PowerShell 5.
        $releaseText = Get-Content -Raw -Encoding UTF8 -LiteralPath $releaseFile
        if ($releaseText -match 'JAVA_VERSION="(?:17|21)(?:\.|")') {
            $env:JAVA_HOME = $jdkCandidate
            $env:Path = "$jdkCandidate\bin;$env:Path"
            return
        }
    }
    throw 'Cần JDK 17 hoặc 21. Đặt JAVA_HOME tới thư mục JDK đã cài.'
}
