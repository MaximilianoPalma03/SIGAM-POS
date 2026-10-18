<#
 .SYNOPSIS
   Crea SIGAM-POS-Setup.exe (instalador + desinstalador) para Windows 10/11 x64.
 .DESCRIPTION
   En la PC DE DESARROLLO requiere JDK 17+, Maven, Inno Setup 6, ZIP OFICIAL
   MySQL Community Server 9.7.1 LTS Windows x64 y VC++ Redistributable x64 oficial.
   La PC de destino NO necesita Java, Maven, MySQL ni Workbench instalados.
   NO elimina ni modifica bases de datos existentes durante el empaquetado.
 .EXAMPLE
   .\distribucion\Crear-SIGAM-POS-Windows.ps1 -MySqlZip 'C:\Descargas\mysql-9.7.1-winx64.zip'
 .EXAMPLE
   .\distribucion\Crear-SIGAM-POS-Windows.ps1 -MySqlZip 'C:\Descargas\mysql-9.7.1-winx64.zip' -VCRedistExe 'C:\Descargas\vc_redist.x64.exe'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$MySqlZip,
    [string]$VCRedistExe = '',
    [string]$InnoSetupCompiler = '',
    [string]$Destino = 'dist'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not [Environment]::Is64BitOperatingSystem) { throw 'Se requiere Windows de 64 bits.' }
if ($env:OS -ne 'Windows_NT') { throw 'Este constructor debe ejecutarse en Windows 10/11.' }
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$mysqlZipPath = (Resolve-Path -LiteralPath $MySqlZip -ErrorAction Stop).Path
if (-not ([IO.Path]::GetFileName($mysqlZipPath) -match '(?i)^mysql-9\.7\.1-winx64\.zip$')) {
    throw 'Se necesita MySQL Community Server 9.7.1 LTS, Windows x64 ZIP Archive (no MSI, no Workbench).'
}
$destinoAbs = if ([IO.Path]::IsPathRooted($Destino)) { $Destino } else { Join-Path $raiz $Destino }
$imagen = Join-Path $destinoAbs 'SIGAM-POS'
$setup = Join-Path $destinoAbs 'SIGAM-POS-Setup.exe'
$temporal = Join-Path ([IO.Path]::GetTempPath()) 'sigam-pos-build-1.0.0-rc1'

function Exigir-Comando([string]$nombre) {
    if (-not (Get-Command $nombre -ErrorAction SilentlyContinue)) {
        throw "Falta $nombre en PATH en la PC de desarrollo."
    }
}
function Exigir-Archivo([string]$ruta) {
    if (-not (Test-Path -LiteralPath $ruta -PathType Leaf)) { throw "Archivo requerido ausente: $ruta" }
}
function Ejecutar([string]$descripcion,[string]$archivo,[string[]]$argumentos) {
    Write-Host $descripcion -ForegroundColor Cyan
    & $archivo @argumentos
    if ($LASTEXITCODE -ne 0) { throw "Falló $descripcion (salida $LASTEXITCODE)." }
}
function Encontrar-ISCC {
    if ($InnoSetupCompiler -ne '') { return (Resolve-Path -LiteralPath $InnoSetupCompiler).Path }
    $cmd = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($carpeta in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, (Join-Path $env:LOCALAPPDATA "Programs"))) {
        if ([string]::IsNullOrWhiteSpace($carpeta)) { continue }
        $ruta = Join-Path $carpeta 'Inno Setup 6\ISCC.exe'
        if (Test-Path $ruta) { return $ruta }
    }
    throw "Falta Inno Setup 6. Instalalo en tu PC de desarrollo con: winget install --id JRSoftware.InnoSetup -e"
}

Exigir-Comando 'mvn'
Exigir-Comando 'jpackage'
Exigir-Comando 'java'
$iscc = Encontrar-ISCC
$versionJdk = & jpackage --version
if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar la version del JDK con jpackage." }
Write-Host "Java de compilación: $versionJdk"
Write-Host "Inno Setup: $iscc"
if (Test-Path -LiteralPath $imagen) {
    throw "La imagen anterior todavía existe: $imagen. Con SIGAM-POS cerrado, renombrala o borrala. NO toques %LOCALAPPDATA%\SIGAM-POS."
}
if (Test-Path -LiteralPath $setup) {
    throw "El instalador anterior todavía existe: $setup. Renombralo antes de crear otro."
}
New-Item -ItemType Directory -Path $destinoAbs -Force | Out-Null
# El proyecto puede estar en una ruta Windows muy larga. El archivo MySQL ZIP se extrae
# en TEMP, fuera de la carpeta de datos reales y con una ruta de trabajo corta.
if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Recurse -Force }
New-Item -ItemType Directory -Path $temporal -Force | Out-Null
Push-Location $raiz
try {
    Ejecutar '1/7 Compilando el proyecto...' 'mvn' @('clean','test-compile')
    $entrada = Join-Path $temporal 'app'
    $bibliotecas = Join-Path $entrada 'lib'
    New-Item -ItemType Directory -Path $bibliotecas -Force | Out-Null
    Ejecutar '2/7 Creando JAR y copiando dependencias...' 'mvn' @('package','dependency:copy-dependencies',"-DoutputDirectory=$bibliotecas")
    $jar = Get-ChildItem (Join-Path $raiz 'target') -Filter 'sigam-pos-*.jar' |
      Where-Object { $_.Name -notmatch 'sources|javadoc|original' } | Select-Object -First 1
    if (-not $jar) { throw 'No se encontró el JAR de la aplicación.' }
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $entrada $jar.Name) -Force
    $jdbc = Get-ChildItem $bibliotecas -Filter 'mysql-connector-j-*.jar' | Select-Object -First 1
    $flat = Get-ChildItem $bibliotecas -Filter 'flatlaf-*.jar' | Select-Object -First 1
    if (-not $jdbc -or -not $flat) { throw 'Faltan bibliotecas mysql-connector-j o FlatLaf en el paquete.' }

    Write-Host '3/7 Ejecutando las pruebas Java sin conexión MySQL...' -ForegroundColor Cyan
    $pruebas = @('PruebaInicioSeguro','PruebaDominio','PruebaTurnos','PruebaImportes',
      'PruebaCarrito','PruebaMediosDigitales','PruebaAnalisis','PruebaPeriodosAnalisis',
      'PruebaAlertas','PruebaExportacion','PruebaFormatoCsv','PruebaDeteccionBackup',
      'PruebaPanelAnalisis','PruebaTemaVisual','PruebaVisual02','PruebaVisual03',
      'PruebaDistribucionV1')
    $cp = "$(Join-Path $raiz 'target\classes');$(Join-Path $raiz 'target\test-classes');$(Join-Path $bibliotecas '*')"
    foreach ($nombre in $pruebas) {
        Ejecutar "   Ejecutando $nombre" 'java' @('-ea','-Djava.awt.headless=true','-cp',$cp,"ar.com.sigampos.$nombre")
    }

    # Debe existir el JDK nativo Windows; jpackage.exe genera los binarios Windows.
    $argsJpackage = @('--type','app-image','--name','SIGAM-POS',
      '--app-version','1.0.0','--input',$entrada,'--main-jar',$jar.Name,
      '--main-class','ar.com.sigampos.Main','--dest',$destinoAbs,
      '--java-options','-Dfile.encoding=UTF-8',
      '--add-modules','java.desktop,java.sql,java.naming,java.logging,java.management,java.xml,java.prefs,java.security.jgss,jdk.crypto.ec,jdk.unsupported')
    Ejecutar '4/7 Creando SIGAM-POS.exe con Java incluido...' 'jpackage' $argsJpackage
    Exigir-Archivo (Join-Path $imagen 'SIGAM-POS.exe')
    $configJpackage = Get-ChildItem $imagen -Recurse -Filter 'SIGAM-POS.cfg' -File | Select-Object -First 1
    if (-not $configJpackage) { throw 'jpackage no generó la configuración del launcher.' }
    $cfg = Get-Content $configJpackage.FullName -Raw
    if ($cfg -notmatch 'mysql-connector-j' -or $cfg -notmatch 'flatlaf') {
        throw 'El EXE no está configurado para cargar JDBC/FlatLaf. No se creará instalador incompleto.'
    }

    Write-Host '5/7 Incorporando MySQL 9.7.1 LTS x64 al instalador...' -ForegroundColor Cyan
    $extraccion = Join-Path $temporal 'mysql-zip'
    if (Test-Path $extraccion) { Remove-Item $extraccion -Recurse -Force }
    Expand-Archive -LiteralPath $mysqlZipPath -DestinationPath $extraccion
    $mysqld = Get-ChildItem -Path $extraccion -Filter 'mysqld.exe' -File -Recurse |
        Where-Object {$_.Directory.Name -eq 'bin'} | Select-Object -First 1
    if (-not $mysqld) { throw 'El archivo ZIP no contiene mysqld.exe' }
    $mysqlHome = $mysqld.Directory.Parent.FullName
    # Evita empaquetar un ZIP renombrado de otra arquitectura o producto.
    $nombreZipInterno = (Split-Path -Leaf $mysqlHome)
    if ($nombreZipInterno -notmatch '(?i)^mysql-9\.7\.1-winx64$') {
        throw "El ZIP contiene '$nombreZipInterno': se esperaba mysql-9.7.1-winx64."
    }
    $destinoMySQL = Join-Path $imagen 'mysql'
    New-Item -ItemType Directory -Path $destinoMySQL -Force | Out-Null
    foreach ($dir in @('bin','lib','share')) {
        $src = Join-Path $mysqlHome $dir
        if (-not (Test-Path $src)) { throw "ZIP MySQL incompleto: falta $dir" }
        Copy-Item -LiteralPath $src -Destination $destinoMySQL -Recurse -Force
    }
    $licenciaOrigen = @('LICENSE','LICENSE.txt') | ForEach-Object {Join-Path $mysqlHome $_} |
        Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
    if (-not $licenciaOrigen) { throw 'El ZIP de MySQL no contiene la licencia. No se redistribuirá.' }
    foreach ($license in @('LICENSE','LICENSE.txt','README','README.txt')) {
        $src = Join-Path $mysqlHome $license
        if (Test-Path $src) { Copy-Item -LiteralPath $src -Destination $destinoMySQL -Force }
    }
    foreach ($exe in @('mysqld.exe','mysqldump.exe','mysqladmin.exe')) {
        Exigir-Archivo (Join-Path $destinoMySQL "bin\$exe")
    }
    Exigir-Archivo (Join-Path $destinoMySQL 'share\english\errmsg.sys')

    Write-Host '6/7 Preparando Microsoft Visual C++ Redistributable x64...' -ForegroundColor Cyan
    $vcDestino = Join-Path $temporal 'vc_redist.x64.exe'
    if ($VCRedistExe -ne '') {
        Copy-Item -LiteralPath (Resolve-Path $VCRedistExe).Path -Destination $vcDestino -Force
    } else {
        $url = 'https://aka.ms/vs/17/release/vc_redist.x64.exe'
        Write-Host 'Descargando la dependencia oficial para incluirla SIN necesidad de Internet en la PC del profesor.'
        Invoke-WebRequest -Uri $url -OutFile $vcDestino -UseBasicParsing
    }
    Exigir-Archivo $vcDestino
    $firma = Get-AuthenticodeSignature -FilePath $vcDestino
    if ($firma.Status -ne 'Valid' -or $null -eq $firma.SignerCertificate -or
        $firma.SignerCertificate.Subject -notmatch 'Microsoft Corporation') {
        throw 'No pudo verificarse la firma de Microsoft del Visual C++ Redistributable. No se empaquetará.'
    }

    Write-Host '7/7 Creando Setup.exe + desinstalador mediante Inno Setup...' -ForegroundColor Cyan
    $iss = Join-Path $PSScriptRoot 'SIGAM-POS-Setup.iss'
    Exigir-Archivo $iss
    Ejecutar 'Compilando instalador Windows' $iscc @(
      "/DAppImage=$imagen", "/DOutputDir=$destinoAbs", "/DVCRedist=$vcDestino", $iss)
    Exigir-Archivo $setup
    if ((Get-Item $setup).Length -lt 5000000) { throw 'El instalador generado es demasiado pequeño: podría estar incompleto.' }
    $hash = Get-FileHash -Path $setup -Algorithm SHA256
    Write-Host "Instalador creado: $setup" -ForegroundColor Green
    Write-Host "SHA256: $($hash.Hash)" -ForegroundColor Green
    Write-Host 'No incluye datos reales de la base. Desinstalar conserva %LOCALAPPDATA%\SIGAM-POS.'
} finally {
    Pop-Location
}
