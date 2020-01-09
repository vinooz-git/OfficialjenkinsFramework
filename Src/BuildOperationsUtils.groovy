package Utils
import java.text.DateFormat;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.io.File 
/**
 * Available Methods
 * DownloadByHttpReq
 * RunExternalFile
 * SVNCheckOut
 */

/*
 * Download file by using Http request
 * Parameters :
 */
def DownloadByHttpReq(outputfileLoc,BuildUrl)
{
	try 
	{
		httpRequest ignoreSslErrors: true, outputFile: outputfileLoc, responseHandle: 'NONE', url: BuildUrl	
	}		
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}	

/*
 * Run a executable file by Bat command
 * Parameters :
 */
def RunExternalFile(ExecFileLoc)
{
	try{
		def status = bat label: 'RunFile', returnStatus: true, script: "(cmd/c call \"${ExecFileLoc}\")"
		if(status != 0)
		{
			echo "Failed Bat command is "+command
			currentBuild.result = 'Failure'
		}
		return status;
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

/*
 * Run a executable file by shell command
 * Parameters :
 */
def RunLinuxFile(ExecFileLoc)
{
	try{
		def status = sh label: 'RunShFile', returnStatus: true, script: "(sh \"${ExecFileLoc}\")"
		if(status != 0)
		{
			echo "Failed Linux command is "+command
			currentBuild.result = 'Failure'
		}
		return status;
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

/*
 * Run a batch command
 * Parameters :
 */
def RunBatCmd(command)
{
	try{
		echo "Windows Exe Command :"+command
		
		def status=bat label: 'RunBatCmd',returnStatus: true,  script: "${command}"
		echo "status of Bat command  "  + status
		if(status != 0)
		{
			echo "Failed Bat command is "+command
			currentBuild.result = 'Failure'
		}
		return status;
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

/*
 * Run a Shell command
 * Parameters :
 */
def RunShellCmd(command)
{
 try{
	echo "Linux Exe Command :"+command
	def status=sh label: 'RunshellCommmand',returnStatus: true,  script: "${command}"
	if(status != 0)
		{
			echo "Failed Shell command is "+command
			currentBuild.result = 'Failure'
		}
	return status;
 }
 catch(Exception e)
 {
	echo 'Exception reason: ' + e
	echo 'Stacktrace: ' + e.getStackTrace()
	currentBuild.result = 'Failure'
 }
}

/*
 * Checking out files from SVN
 * Parameters :
 */
def SVNCheckOut(RepoUrl)
{
	
	try
	{
		//checkout([$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', locations: [[cancelProcessOnExternalsFail: true, credentialsId: '3c8a141a-24e6-4010-a36b-8ab9209b42d8', depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: 'http://brdc-svn.network.internal/svn/AutomationFrameworks/branches/7.1_Universal%20Viewer/Functional_Automation/ServerExecutionFiles/']], quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']])
		checkout([$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', locations: [[cancelProcessOnExternalsFail: true, credentialsId: "${env.SVNCredential}" , depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: RepoUrl.toString()]], quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']])
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

/*
 * Checking out files from GIT
 * Parameters :
 */
def GITCheckOut(RepoUrl,BranchName)
{
	try
	{
		//git credentialsId: CredentialsId, url: RepoUrl
		//checkout([$class: 'GitSCM', branches: [[name: '"+BranchName+"']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${env.gitCredential}", url: RepoUrl]]])
		//git branch: "${BranchName}", credentialsId: "${env.gitCredential}", url: "${RepoUrl}"
		checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], gitTool: 'Windows git', submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'c3cvmjyh_readonly_ghe_token', url: 'https://github.ibm.com/WH-Imaging/process-tools.git']]])
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

def ExeCmd_WindowsSchedular(command)
{
try{
	//Get Current time and Scheduling time
	def timelog = "${env.WORKSPACE}"+"\\time.txt"
    bat "time /T > ${timelog}"
    def date = readFile"${timelog}"
    SimpleDateFormat newFormat = new SimpleDateFormat("HH:mm",Locale.ENGLISH);
    Date parsedDate = newFormat.parse(date);
    def dateAfteroneMin=new Date(parsedDate.getTime() + (2 * 60000));
    println "dateAfteroneMin "+dateAfteroneMin
    String time = dateAfteroneMin.format("HH:mm:ss")
    println "Batch file scheduled to start in " + time
    def SchedulrCmd = "schtasks /create /tn JenkinsScheduledtask /tr ${command} /sc once /st ${time}"
	def SchedulrCmd1 = "\"${SchedulrCmd}\""
	echo "SchedulrCmd :"+SchedulrCmd1
	def status = bat label: 'RunBatCmd',returnStatus: true,  script: "${SchedulrCmd1}"
	if(status != 0)
		{
			echo "Failed bat command is "+command
			currentBuild.result = 'Failure'
		}
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

/*
 * Executing windows batch command in Jenkins without waiting for batch execution results and status
 * Parameters :'command'- command to execute
 */
def ExecuteCmd_WithoutWait(command)
{	 
	def filename = env.WORKSPACE+"\\ExecuteCmd.bat"
	echo "env.WORKSPACE: "+filename
	if(fileExists (filename))
	{
	println"Already file exists"fileOperations([fileDeleteOperation(excludes: filename, includes: '')])
	println"File deleted:"+filename
	}
	fileOperations([fileCreateOperation(fileContent: command, fileName: filename)])
	println "File created to path "+filename
	
	try{
		def exeCommand = "start /b "+ env.WORKSPACE+"\\ExecuteCmd.bat"
		echo "Windows Exe Command :"+exeCommand
		def status = bat label: 'RunBatCmd',returnStatus: true,  script: "${exeCommand}"
		if(status != 0)
		{
			echo "Failed Bat command is "+command
			currentBuild.result = 'Failure'
		}
		return status;
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

def ExecuteInPowerShell(command)
{	 
	def filename = env.WORKSPACE+"\\ExecuteCmd.bat"
	echo "env.WORKSPACE: "+filename
	if(fileExists (filename))
	{
	println"Already file exists"
	//fileOperations([fileDeleteOperation(excludes: '', includes: filename)])
	new File(filename).delete()	
	println"File deleted:"+filename
	}
	fileOperations([fileCreateOperation(fileContent: command, fileName: filename)])
	println "File created to path "+filename
	
	try{
		def exeCommand = "cmd.exe /c "+ env.WORKSPACE+"\\ExecuteCmd.bat"
		echo "ExecuteInPowerShell :"+ exeCommand
		//def status = powershell label: '', returnStatus: true, script: "exeCommand"
		status = RunPowerShell(exeCommand)
		if(status != 0)
		{
			echo "Failed Bat command is "+command+" and return status is "+status
			//currentBuild.result = 'Failure'
		}
		return status;
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		//currentBuild.result = 'Failure'
	}
}

def RunPowerShell(psCmd) {
	psCmd=psCmd.replaceAll("%", "%%")
	def status=bat label: 'RunBatCmd',returnStatus: true,  script: "powershell.exe -NonInteractive -ExecutionPolicy Bypass -Command \"\$ErrorActionPreference='Stop';[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;$psCmd;EXIT \$global:LastExitCode\""
	return status
	}

def RunTestExecute(arg,projectpath,username,password)
{
	try
	{		
		testcompletetest actionOnErrors: 'NONE', commandLineArguments: arg.toString(), publishJUnitReports: false, suite: projectpath.toString(), useTCService: true, userName: username.toString(), userPassword: password.toString()
		
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

