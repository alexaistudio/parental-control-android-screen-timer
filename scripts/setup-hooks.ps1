$ErrorActionPreference = 'Stop'

$git = Get-Command git -ErrorAction SilentlyContinue
if (-not $git) {
    $candidate = Join-Path $env:ProgramFiles 'Git\cmd\git.exe'
    if (-not (Test-Path -LiteralPath $candidate)) {
        throw 'Git executable was not found.'
    }
    $gitExecutable = $candidate
} else {
    $gitExecutable = $git.Source
}

& $gitExecutable config core.hooksPath .githooks
Write-Output 'Git hooks enabled from .githooks'
