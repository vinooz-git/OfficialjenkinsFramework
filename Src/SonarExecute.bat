cd /d %1

E:\sonar-scanner-msbuild-4.6.0.1930-net46\SonarScanner.MSBuild.exe begin /k:%2 /d:sonar.host.url=http://%4:9000 /d:sonar.login="%3" > C:\MSBuildResult.txt

"C:\Program Files (x86)\MSBuild\14.0\Bin\MsBuild.exe" /t:Rebuild > C:\SonarResult.txt

E:\sonar-scanner-msbuild-4.6.0.1930-net46\SonarScanner.MSBuild.exe end /d:sonar.login="%3" > C:\SonarScanResult.txt