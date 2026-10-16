[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Email,
    [Parameter(Mandatory = $true)][string]$FullName,
    [Parameter(Mandatory = $true)][string]$Phone,
    [ValidateSet('ADMIN', 'MENU_MANAGER', 'ORDER_STAFF', 'PROMOTION_MANAGER', 'HR_MANAGER')]
    [string]$Role = 'ADMIN'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java.ps1')
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$previousPassword = $env:CRAVE_ADMIN_PASSWORD
Push-Location -LiteralPath $projectDirectory
try {
    Set-CraveJava
    & mvn -B -ntp -f backend/pom.xml -Pdev -DskipTests test-compile org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath '-DincludeScope=test' '-Dmdep.outputFile=target/operator-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Không build được công cụ tạo tài khoản.' }
    if (!$env:CRAVE_ADMIN_PASSWORD) {
        $securePassword = Read-Host 'Mật khẩu (8–72 ký tự)' -AsSecureString
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        try { $env:CRAVE_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    }
    $dependencyClasspath = (Get-Content -Raw -Encoding UTF8 backend/target/operator-classpath.txt).Trim()
    # Encode Unicode arguments because the Windows JVM may use an ANSI native code page.
    # The password stays in the environment and is never part of the command line.
    $encodedArguments = @($Email, $FullName, $Phone, $Role) | ForEach-Object {
        [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($_))
    }
    & java '-Dfile.encoding=UTF-8' -cp "backend/target/test-classes;backend/target/classes;$dependencyClasspath" com.foodordering.dev.CreateEmployee --encoded-args @encodedArguments
    if ($LASTEXITCODE -ne 0) { throw 'Không tạo được tài khoản nhân viên.' }
} finally {
    $env:CRAVE_ADMIN_PASSWORD = $previousPassword
    Pop-Location
}