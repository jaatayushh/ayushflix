[Setup]
#include "version.iss"
AppId={{C626E83F-8C3A-4D78-B5B3-FA19FE223E0C}}
AppName=Ayushflix Desktop
AppVersion={#AppVersion}
AppPublisher=Ayush
AppPublisherURL=https://github.com/jaatayushh/ayushflix
AppSupportURL=https://github.com/jaatayushh/ayushflix
AppUpdatesURL=https://github.com/jaatayushh/ayushflix
DefaultDirName={autopf}\Ayushflix
DefaultGroupName=Ayushflix
AllowNoIcons=yes
SetupIconFile=..\desktop-app\src\main\resources\app_icon.ico
UninstallDisplayIcon={app}\Ayushflix-Desktop.exe
; Output directory for the compiled installer
OutputDir=..\desktop-app\build\outputs
OutputBaseFilename=Ayushflix-Setup
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
TimeStampsInUTC=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Copy all files and folders from the AppImage output
Source: "..\desktop-app\build\compose\binaries\main\app\Ayushflix-Desktop\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Ayushflix"; Filename: "{app}\Ayushflix-Desktop.exe"
Name: "{group}\{cm:UninstallProgram,Ayushflix}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Ayushflix"; Filename: "{app}\Ayushflix-Desktop.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Ayushflix-Desktop.exe"; Description: "{cm:LaunchProgram,Ayushflix}"; Flags: nowait postinstall skipifsilent runasoriginaluser
