package Utils
import groovy.json.JsonSlurper
import java.nio.file.FileSystems
/** Available Method :
 * ScanProject
 * GenerateReport
 * CreateSonarProject
 * generate_Token
 */

/*
 * Scan a Project source code from Project location
 * Parameters :
*/
def generatedtoken = null 
def sonarip = null

def ScanProject_JavaMaven(Pomlocation, SonarHomeLocation, SonarProjectName, MavenLocation, SonarURL)
{
	def sqScannerMsBuildHome = tool SonarHomeLocation
	withSonarQubeEnv(SonarURL) 
	{
		def sonarServerUrl = env.SONAR_HOST_URL
		echo sonarServerUrl
		sonarip = sonarServerUrl
		def mvnlocation = tool MavenLocation
		echo "mvn location is : " + mvnlocation
		def settingsfile = getFilefromname(mvnlocation, "settings.xml")
		echo "Settings.xml located in : " + settingsfile
		def content = "\n<settings> \n<pluginGroups> \n<pluginGroup>org.sonarsource.scanner.maven</pluginGroup> \n</pluginGroups> \n<profiles> \n<profile> \n<id>sonar</id> \n<activation> \n<activeByDefault>true</activeByDefault> \n</activation> \n<properties> \n<sonar.host.url>" + sonarServerUrl + "</sonar.host.url> \n</properties> \n</profile> \n</profiles> \n</settings>"
		fileOperations([fileCreateOperation(fileContent: content, fileName: settingsfile)])
		sleep(20)
		def Projectlocation = workspace + "\\" + Pomlocation
		CreateSonarProject(sqScannerMsBuildHome, SonarProjectName, sonarServerUrl, "admin", "admin")
		def token = generate_Token(SonarProjectName, sonarServerUrl, "admin", "admin")
		generatedtoken = token
		def cmd = null;
		cmd = "cd /d "+ Projectlocation + " && \"" + mvnlocation + "\\bin\\" + "mvn\" sonar:sonar -Dsonar.projectKey=" + SonarProjectName + " -Dsonar.host.url=" + sonarServerUrl +" -Dsonar.login=" + token
		println "cmd: " + cmd
		def status = bat label: 'runcommand', returnStatus: true, script:"(cmd/c \"${cmd}\")"
		return status
	}
}

def ScanProject_JavaAnt(Projectname, buildxmllocation, sonarantjarlocation, SonarHomeLocation, SonarURL)
{
	def sqScannerMsBuildHome = tool SonarHomeLocation
	withSonarQubeEnv(SonarURL) 
	{
		def sonarServerUrl = env.SONAR_HOST_URL
		echo sonarServerUrl
		sonarip = sonarServerUrl
		CreateSonarProject(sqScannerMsBuildHome, SonarProjectName, sonarServerUrl, "admin", "admin")
		def token = generate_Token(SonarProjectName, sonarServerUrl, "admin", "admin")
		generatedtoken = token
		def buildlocationpath = workspace + "\\" + buildxmllocation
		echo "Build.xml path is : " + buildlocationpath
		def projectlocation = getParentfolder(buildlocationpath)
		def filecontent = readFile buildlocationpath
		echo filecontent
		def val = filecontent.split("\n")
		def command = ""
		for(def i = 0 ; i < val.size() ; i++)
		{
			if(val[i].trim() != "")
			{
				if(val[i].startsWith("<project"))
				{
					command = command + " \n " + val[i].split(">")[0] + " xmlns:sonar=\"antlib:org.sonar.ant\">"
				}
				else if(val[i].trim().endsWith("project>"))
				{
					command = command + " \n <property name=\"sonar.host.url\" value=\"" + sonarServerUrl +"\" />"
					command = command + " \n <property name=\"sonar.projectKey\" value=\"org.sonarqube:sonarqube-scanner-ant\" />"
					command = command + " \n <property name=\"sonar.projectName\" value=\"" + Projectname + "\" />"
					command = command + " \n <property name=\"sonar.projectVersion\" value=\"1.0\" />"
					command = command + " \n <property name=\"sonar.sources\" value=\"" + projectlocation + "\" />"
					command = command + " \n <property name=\"sonar.java.binaries\" value=\"" + projectlocation + "\" />"
					command = command + " \n <property name=\"sonar.java.libraries\" value=\"" + projectlocation + "\" />"
					command = command + " \n <target name=\"sonar\">"
					command = command + " \n <taskdef uri=\"antlib:org.sonar.ant\" resource=\"org/sonar/ant/antlib.xml\">"
					command = command + " \n <classpath path=\"" + sonarantjarlocation + "\" />"
					command = command + " \n </taskdef>"
					command = command + " \n <sonar:sonar />"
					command = command + " \n </target>"
					command = command + " \n </project>"
				}
				else
					command = command + " \n " + val[i]
			}
		}
		echo "Lines to be added : " + command
		fileOperations([fileCreateOperation(fileContent: command , fileName: buildlocationpath)])
		def cmd = "cd /" + projectlocation + "&& ant sonar -v"
		bat label: '', script: cmd
	}
}

def ScanProject_Csharp(SonarProjectName, SonarHomeLocation, SonarURL, MSBuildLocation)
{
	FileOperations filesop = new FileOperations()
	def projfile = null
	def sqScannerMsBuildHome = tool SonarHomeLocation
	withSonarQubeEnv(SonarURL) 
	{
		echo sqScannerMsBuildHome
		def sonarServerUrl = env.SONAR_HOST_URL
		echo sonarServerUrl
		sonarip = sonarServerUrl
		CreateSonarProject(sqScannerMsBuildHome, SonarProjectName, sonarServerUrl, "admin", "admin")
		def token = generate_Token(SonarProjectName, sonarServerUrl, "admin", "admin")
		generatedtoken = token
		def projectfilelocation = getFilefromextension(workspace, "csproj")
		def projectlocation = getParentfolder(projectfilelocation)
		echo "The directory to be used : " + projectlocation
		def MSBuildpath = tool MSBuildLocation
		echo("MSBuild exe location : " + MSBuildpath)
		echo("Scanner for MS Build : " + sqScannerMsBuildHome)
		echo ("cmd1 : "+ sqScannerMsBuildHome + "\\SonarScanner.MSBuild.exe begin /k:" + SonarProjectName + " /d:sonar.host.url=" + sonarServerUrl + " /d:sonar.login=" + token)
		echo("cmd2 : \"" + MSBuildpath + "\\MSBuild.exe\" " + projectfilelocation + " /t:Rebuild")
		bat "cd /d " + projectlocation
		bat "" + sqScannerMsBuildHome + "\\SonarScanner.MSBuild.exe begin /k:" + SonarProjectName + " /d:sonar.host.url=" + sonarServerUrl + " /d:sonar.login=" + token
		bat "\"" + MSBuildpath + "\\MSBuild.exe\" " + projectfilelocation + " /t:Rebuild"
		//bat "MSBuild.exe " + projectfilelocation + " /t:Rebuild"
		bat "" + sqScannerMsBuildHome + "\\SonarScanner.MSBuild.exe end /d:sonar.login=" + token
	}
}

def ScanProject_Others(SonarProjectName, SonarHomeLocation, SonarURL, Projectlocation)
{
	def sqScannerMsBuildHome = tool SonarHomeLocation
	withSonarQubeEnv(SonarURL) 
	{
		echo sqScannerMsBuildHome
		def sonarServerUrl = env.SONAR_HOST_URL
		echo sonarServerUrl
		sonarip = sonarServerUrl
		CreateSonarProject(sqScannerMsBuildHome, SonarProjectName, sonarServerUrl, "admin", "admin")
		def token = generate_Token(SonarProjectName, sonarServerUrl, "admin", "admin")
		generatedtoken = token
		def projectlocation = workspace + "\\" + Projectlocation
		if(!isUnix())
		{
			echo "Windows machine node"
			echo("Scanner Directory : " + sqScannerMsBuildHome)
			echo("directory of the project is : " + projectlocation)
			def batlocation = getFilefromname(sqScannerMsBuildHome, "sonar-scanner.bat")
			echo("cmd: \"" + batlocation + "\" -D\"sonar.projectKey=" + SonarProjectName + "\" -D\"sonar.sources=.\" -D\"sonar.host.url=" + sonarip + "\" -D\"sonar.login=" + token + "\"")
			bat "cd /d " + projectlocation
			bat "\"" + batlocation + "\" -D\"sonar.projectKey=" + SonarProjectName + "\" -D\"sonar.sources=.\" -D\"sonar.host.url=" + sonarip + "\" -D\"sonar.login=" + token + "\""
		}
		else
		{
			def cmd = "cd /" + projectlocation + "&& sonar-scanner -Dsonar.projectKey=" + SonarProjectName + " -Dsonar.sources=. -Dsonar.host.url=" + sonarServerUrl + " -Dsonar.login=" + token
			sh label: '', script: cmd
		}
	}
}

/*
 * Generate a Report by using Jar files
 * Parameters :
 */
def GenerateReport(SonarProjectName, jarlocation , ip, token)
{
	println"Generating Report"
	cmd = "cd /d "+ jarlocation + " && " + "java -jar cnesreport.jar -p " + SonarProjectName + " -s " + ip + " -t " + token
	println"cmd : " + cmd
	def status = bat label: 'runcommand', returnStatus: true, script:"(cmd/c \"${cmd}\")"
}
 
//delete previously existing project
def DeleteSonarProject(SonarProjectName, ip, username, password)
{
	println "started deletesonarproject method"
	if((username == null || username == "") && (password == null || password == ""))
	{
		username = "admin"
		password = "admin"
	}
	println "project name in SonarQube is : " + SonarProjectName
	def tokenName = SonarProjectName
	if (!(tokenName)) 
	{
	    println("Token name MUST be specified on the command line.")
	    exit(1)
	}
	def sonarHost = ip
	println("SonarQube Host: " + sonarHost)
	def url = sonarHost + "/api/projects/delete?project=" + tokenName
	println("Delete url = " + url)
	def deletepost = new URL(url).openConnection()
	def deletemessage = "name=" + tokenName + "&login=" + username
	deletepost.setRequestMethod("POST")
	deletepost.setDoOutput(true)
	deletepost.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
	def deleteauthString = "${username}:${password}".bytes.encodeBase64().toString()
	deletepost.setRequestProperty("Authorization", "Basic ${deleteauthString}")
	deletepost.getOutputStream().write(deletemessage.getBytes("UTF-8"))
	def deleteres = deletepost.getResponseCode()
	echo "delete project response obtained is : " + deleteres
	if (deleteres == 200 || deleteres == 204) 
	{
		echo("Project deleted successfully")
	}
	else
	{
		println("Request failed")
		println(deletepost.getErrorStream())
	}
}


/*
 * Create a sonar Project in SonarQube application UI
 * Parameters :
*/
def CreateSonarProject(SonarLocation, SonarProjectName, ip, username, password)
{
	def sonarhome = SonarLocation
	echo sonarhome
	def filelocation = getFilefromextension(sonarhome , "properties")
	echo ("filelocation is : " + filelocation)
	def sonarconffolder = getParentfolder(filelocation)
	echo ("sonarconffolder is : " + sonarconffolder)
	String[] valfile = filelocation.split("\\\\")
	def filename = valfile[valfile.size() - 1]
	echo ("filename is : " + filename)
	def tempfile = workspace + "\\" + filename
	echo ("Temp file location is : " + tempfile)
	
	if((username == null || username == "") && (password == null || password == ""))
	{
		username = "admin"
		password = "admin"
	}
	println "project name in SonarQube is : " + SonarProjectName
	def tokenName = SonarProjectName
	if (!(tokenName)) 
	{
	    println("Token name MUST be specified on the command line.")
	    exit(1)
	}

	def sonarHost = ip
	println("SonarQube Host: " + sonarHost)
	DeleteSonarProject(tokenName, ip, username, password)
	def createpost = new URL(sonarHost + "/api/projects/create?name=" + tokenName + "&project=" + tokenName + "&visibility=private").openConnection()
	def createmessage = "name=${tokenName}&login=${username}"
	createpost.setRequestMethod("POST")
	createpost.setDoOutput(true)
	createpost.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
	def createauthString = "${username}:${password}".bytes.encodeBase64().toString()
	createpost.setRequestProperty("Authorization", "Basic ${createauthString}")
	createpost.getOutputStream().write(createmessage.getBytes("UTF-8"))
	def createprojres = createpost.getResponseCode()
	echo "create project response obtained is : " + createprojres
	if (createprojres == 200 || createprojres == 204) 
	{
		echo("Project created successfully")
	}
	else
	{
		println("Request failed")
		println(createpost.getErrorStream())
	}
}
 
/*
 * Create a Token for SonarQube project
 * Parameters :
*/
def generate_Token(SonarProjectName, ip, username, password)
{
	if((username == null || username == "") && (password == null || password == ""))
	{
		username = "admin"
		password = "admin"
	}
	println "Token to be generated for the project " + SonarProjectName
	def tokenName = SonarProjectName
	if (!(tokenName)) 
	{
	    println("Token name MUST be specified on the command line.")
	    exit(1)
	}
	Revoke_Token(tokenName, ip, username,password)
	def sonarHost = ip
	println("SonarQube Host: ${sonarHost}")

	def post = new URL(sonarHost + "/api/user_tokens/generate").openConnection()
	def message = "name=${tokenName}&login=${username}"
	post.setRequestMethod("POST")
	post.setDoOutput(true)
	post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
	def authString = "${username}:${password}".bytes.encodeBase64().toString()
	post.setRequestProperty("Authorization", "Basic ${authString}")
	post.getOutputStream().write(message.getBytes("UTF-8"))
	def res = post.getResponseCode()

	if (res == 200) 
	{
		def jsonBody = post.getInputStream().getText()
		def jsonParser = new JsonSlurper()
		def data = jsonParser.parseText(jsonBody)
		def token = data.token
		println("Auth Token: ${token}")
		return token
	} 
	else 
	{
		println("Request failed")
	    	println(post.getErrorStream().getText())
		return null
	}
}

//Revoke token for SonarProject which has been generated previously
def Revoke_Token(SonarProjectName, ip, username,password)
{
	if((username == null || username == "") && (password == null || password == ""))
	{
		username = "admin"
		password = "admin"
	}
	println "Token to be revoked for the project " + SonarProjectName
	def tokenName = SonarProjectName
	if (!(tokenName)) 
	{
	    println("Token name MUST be specified on the command line.")
	    exit(1)
	}

	def sonarHost = ip
	println("SonarQube Host: ${sonarHost}")
	def revokepost = new URL(sonarHost + "/api/user_tokens/revoke?name=" + tokenName + "&login=" + username).openConnection()
	def revokemessage = "name=${tokenName}&login=${username}"
	revokepost.setRequestMethod("POST")
	revokepost.setDoOutput(true)
	revokepost.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
	def revokeauthString = "${username}:${password}".bytes.encodeBase64().toString()
	revokepost.setRequestProperty("Authorization", "Basic ${revokeauthString}")
	revokepost.getOutputStream().write(revokemessage.getBytes("UTF-8"))
	def revokeres = revokepost.getResponseCode()
	echo "token revoked response obtained is : " + revokeres
	if (revokeres == 200 || revokeres == 204) 
	{
		echo("Token revoked successfully")
	} 
	else 
	{
		println("Revoke Token failed")
		println(revokepost.getErrorStream())
	}
}

//SCM Settings	
def SCMSetting_Sonar( ip, username, password, svnusername, svnpassword)
{
	if((username == null || username == "") && (password == null || password == ""))
	{
		username = "admin"
		password = "admin"
	}
	def tokenName = "testsamp1"
	if (!(tokenName)) 
	{
	    println("Token name MUST be specified on the command line.")
	    exit(1)
	}

	def sonarHost = ip
	println("SonarQube Host: ${sonarHost}")
	def settpost = new URL(sonarHost + "/api/settings/set?key=sonar.scm.disabled&value=true").openConnection()
	def setmessage = "name=${tokenName}&login=${username}"
	settpost.setRequestMethod("POST")
	settpost.setDoOutput(true)
	settpost.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
	def setauthString = "${username}:${password}".bytes.encodeBase64().toString()
	settpost.setRequestProperty("Authorization", "Basic ${setauthString}")
	settpost.getOutputStream().write(setmessage.getBytes("UTF-8"))
	def setres = settpost.getResponseCode()
	echo "SCM disabled response obtained is : " + setres
	if (setres == 200 || setres == 204) 
	{
		echo("SCM disabled set to true")
	}
	else
	{
		println("SCM disabled not enabled")
		println(settpost.getErrorStream())
	}

	def settpost1 = new URL(sonarHost + "/api/settings/set?key=sonar.svn.username&value=" + svnusername).openConnection()
	def setmessage1 = "name=${tokenName}&login=${username}"
	settpost1.setRequestMethod("POST")
	settpost1.setDoOutput(true)
	settpost1.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
	def setauthString1 = "${username}:${password}".bytes.encodeBase64().toString()
	settpost1.setRequestProperty("Authorization", "Basic ${setauthString1}")
	settpost1.getOutputStream().write(setmessage1.getBytes("UTF-8"))
	def setres1 = settpost1.getResponseCode()
	echo "Set SVN Username response obtained is : " + setres1
	if (setres1 == 200 || setres1 == 204) 
	{
	    	echo("SVN Username has been set")
	}
	else
	{
	    	println("SVN Username not set")
		println(settpost1.getErrorStream())
	}
	
	def settpost2 = new URL(sonarHost + "/api/settings/set?key=sonar.svn.password.secured&value=" + svnpassword).openConnection()
	def setmessage2 = "name=${tokenName}&login=${username}"
	settpost2.setRequestMethod("POST")
	settpost2.setDoOutput(true)
	settpost2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
	def setauthString2 = "${username}:${password}".bytes.encodeBase64().toString()
	settpost2.setRequestProperty("Authorization", "Basic ${setauthString2}")
	settpost2.getOutputStream().write(setmessage2.getBytes("UTF-8"))
	def setres2 = settpost2.getResponseCode()
	echo "Set SVN Password response obtained is : " + setres2
	if (setres2 == 200 || setres2 == 204) 
	{
		echo("SVN Password set successfully")
	}
	else
	{
		println("SVN Password not set")
		println(settpost2.getErrorStream())
	}
}
	
def getFilefromextension(FolderLocation, extension)
{
	echo "Directory used is : " + FolderLocation
	echo "extension to be searched for is : " + extension
	def filepath = null
	def command = 'dir ' + FolderLocation +  '/s /b /o:gn'
	def val = bat label: '', script: command , returnStdout: true
	def a = val.split("\n")
	for(def i = 0 ; i < a.size() ; i++)
	{
		if(a[i].trim() != "")
		{
	     		def temp = a[i].trim().split("\\.")
			if(temp[temp.size() - 1].trim().contains(extension))
			{
				echo "The file being searched is  : " + a[i].trim()
				filepath = a[i].trim()
				def projectlocation = getParentfolder(filepath)
				echo "Project parent : " + projectlocation
				break
			}
		}
	}
	return filepath
}

def getFilefromname(FolderLocation, name)
{
	def filename = name.split("\\.")[0]
	def extension = name.split("\\.")[1]
	echo "Directory used is : " + FolderLocation
	echo "filename to be searched for is : " + filename
	echo "extension to be searched for is : " + extension
	def filepath = null
	def command = 'dir ' + FolderLocation +  '/s /b /o:gn'
	def val = bat label: '', script: command , returnStdout: true
	def a = val.split("\n")
	for(def i = 0 ; i < a.size() ; i++)
	{
		if(a[i].trim() != "")
		{
	     		def temp = a[i].trim().split("\\.")
			if(temp[temp.size() - 1].trim().contains(extension))
			{
				def splittedpath = temp[temp.size() - 2].split("\\\\")
				if(splittedpath[splittedpath.size() - 1].trim().contains(filename))
				{
					echo "The file being searched is  : " + a[i].trim()
					filepath = a[i].trim()
					def projectlocation = getParentfolder(filepath)
					echo "Project parent : " + projectlocation
					break
				}
			}
		}
	}
	return filepath
}

def getParentfolder(filename)
{
	def filearray = filename.split("\\\\")
	def foldername = ""
	for(def i = 0 ; i < filearray.size() - 1; i++)
	{
		if(i == 0)
			foldername = foldername + filearray[i]
		else
			foldername = foldername + "\\" + filearray[i]
	}
	echo "Project location is : " + foldername
	return foldername
}
