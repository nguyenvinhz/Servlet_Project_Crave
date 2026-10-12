[CmdletBinding()]
param(
    [ValidateRange(1, 65535)][int]$Port = 8080,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java.ps1')
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location -LiteralPath $projectDirectory
try {
    Set-CraveJava
    if (!(Get-Command java -ErrorAction SilentlyContinue) -or !(Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw 'Cần cài JDK 17/21 và Maven, rồi thêm vào PATH.'
    }
    if (!$SkipBuild) {
        & mvn -B -ntp -f backend/pom.xml -Pdev -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Build WAR thất bại.' }
    }
    & mvn -B -ntp -f backend/pom.xml -Pdev org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath '-DincludeScope=test' '-DincludeGroupIds=org.apache.tomcat.embed,org.apache.tomcat,org.eclipse.jdt' '-Dmdep.outputFile=target/dev-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Không tạo được classpath cho máy chủ phát triển.' }
    $dependencyClasspath = (Get-Content -Raw -Encoding UTF8 backend/target/dev-classpath.txt).Trim()
    $serverClasses = Join-Path $projectDirectory 'backend/target/test-classes'
    $warFile = Join-Path $projectDirectory 'backend/target/crave.war'
    & java '-Dfile.encoding=UTF-8' -cp "$serverClasses;$dependencyClasspath" com.foodordering.dev.DevServer $warFile $Port
    if ($LASTEXITCODE -ne 0) { throw 'Máy chủ phát triển đã dừng do lỗi.' }
} finally {
    Pop-Location
}
