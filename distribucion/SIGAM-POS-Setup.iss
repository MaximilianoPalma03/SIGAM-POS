; SIGAM-POS - instalador Windows 10/11 x64 (Inno Setup 6).
; IMPORTANTE: las bases y credenciales permanecen en %LOCALAPPDATA%\SIGAM-POS.
; Este instalador solo copia la aplicación y MySQL, y NO ejecuta SQL.
#ifndef AppImage
  #error "Falta /DAppImage=<carpeta de imagen jpackage>"
#endif
#ifndef OutputDir
  #error "Falta /DOutputDir=<carpeta de salida>"
#endif
#ifndef VCRedist
  #error "Falta /DVCRedist=<vc_redist.x64.exe oficial>"
#endif

#define AppName "SIGAM-POS"
#define AppVersion "1.0.0-RC1"

[Setup]
; Mismo AppId en futuras versiones: Inno Setup reconoce reinstalaciones/actualizaciones.
AppId={{C1AB174C-4718-41D2-BADF-C0D993244A7D}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName=SIGAM-POS v{#AppVersion}
VersionInfoVersion=1.0.0.0
AppPublisher=SIGAM-POS (Proyecto académico)
DefaultDirName={autopf}\SIGAM-POS
DefaultGroupName=SIGAM-POS
UninstallDisplayName=SIGAM-POS
UninstallDisplayIcon={app}\SIGAM-POS.exe
OutputDir={#OutputDir}
OutputBaseFilename=SIGAM-POS-Setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
CloseApplications=yes
RestartApplications=no
DisableProgramGroupPage=yes
SetupLogging=yes

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear acceso directo en el escritorio"; GroupDescription: "Accesos directos:"; Flags: checkedonce

[Files]
; Java runtime, aplicación y MySQL incluidos: instalador sin descargas en destino.
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#VCRedist}"; DestDir: "{tmp}"; DestName: "vc_redist.x64.exe"; Flags: deleteafterinstall

[Icons]
Name: "{autoprograms}\SIGAM-POS"; Filename: "{app}\SIGAM-POS.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\SIGAM-POS"; Filename: "{app}\SIGAM-POS.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
; MySQL portátil para Windows requiere Visual C++ Redistributable x64.
; Instalación silenciosa; Windows puede requerir reinicio si VC++ estaba en uso.
Filename: "{tmp}\vc_redist.x64.exe"; Parameters: "/install /quiet /norestart"; StatusMsg: "Comprobando dependencias de Windows..."; Flags: waituntilterminated
Filename: "{app}\SIGAM-POS.exe"; Description: "Abrir SIGAM-POS"; Flags: nowait postinstall skipifsilent


; A PROPÓSITO no hay [UninstallDelete], [UninstallRun] destructivos ni entradas
; en {localappdata}\SIGAM-POS. El desinstalador de Inno Setup conserva los datos.
