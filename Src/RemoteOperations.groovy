package Utils
import jenkins.*
import hudson.*

def makeDirectoryonAgent(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, directory)
{      
   def mkdir = sh label: 'mkdir', returnStatus: true, script: """#!/bin/bash +x
   perl ${guestOPScript} -server ${vcenter} -username ${Vspherecre_USR} -password ${Vspherecre_PSW} -vm ${host} -operation mkdir -guestusername ${domain} -guestpassword ${guestPwd} -filepath_src ${directory}"""
   if (mkdir != 0) {
      echo "Credentials might be invalid or Folder already exists in the Target machine"
    }	
}

def CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, srcPath, destPath)
{	
   def cpyFiles = sh label: 'cpyFiles', returnStatus: true, script: """#!/bin/bash +x
   perl ${guestOPScript} --server ${vcenter} --username ${Vspherecre_USR} --password ${Vspherecre_PSW} --vm ${host} --operation copytoguest --guestusername ${domain} --guestpassword ${guestPwd} --filepath_src ${srcPath} --filepath_dst ${destPath}"""	
   if (cpyFiles != 0) {	   
      echo "Credentials might be invalid or File already exists in the Target machine"
   }	
}

def StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, script)
{
   def strtprgthruVghetto = sh label: 'strtprgthruVghetto', returnStatus: true, script: """#!/bin/bash +x
   perl ${guestOPScript} --server ${vcenter} --username ${Vspherecre_USR} --password ${Vspherecre_PSW} --vm ${host} --operation startprog --guestusername ${domain} --guestpassword ${guestPwd} --working_dir ${WinrmWrkDir} --program_path '${WinrmWrkDir}\\WinrmEnable.bat' --program_args '${script}'"""
   if (strtprgthruVghetto != 0) {
     echo "## Credentials might be invalid or File already exists in the Target machine"
   }		   
}

def StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, script)
{
   def strtprgthruWinexe = sh label: 'strtprgthruWinexe', returnStatus: true, script: """#!/bin/bash +x
   winexe -U ${hostIP}/\'${guestuser}'%${guestPwd} //${hostIP} 'PowerShell.exe ${WinrmWrkDir}\\${script}'"""
   if (strtprgthruWinexe != 0) {
     echo "## Credentials might be invalid or File already exists in the Target machine"
   }		
}

//@NonCPS
def CheckAutoLogon(host)
{
	def vmOperations = new Utils.VmOperationsUtils()
	def ReusableFunc = new Utils.GeneralReusables()
	node(host.toString()) {
	if(!isUnix()){
		def command = "tasklist >${env.WORKSPACE}\\tasklist.txt"
		def status=bat label: 'tasklistCMD',returnStatus: true,  script: "${command}"
		//echo "status of tasklistCMD command  "  + status
		if(status == 0)
		{
			tasklistFilePath = "${env.WORKSPACE}\\tasklist.txt"
			channel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel()
			tasklistFile = new FilePath(channel, "${tasklistFilePath}")  
			 BufferedReader reader = new BufferedReader(new StringReader(tasklistFile.readToString()));
			    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
						if (line.contains ("LogonUI.exe") || line.contains ("logon.scr")) {
					    print line;
						def nodeName = host.toString();
						def readFileContents = ReusableFunc.ReadXml()					// Read Xml input file
						def config = new XmlSlurper().parseText(readFileContents)
						def vcenter = config.MachineDetails.'**'.find { node -> node['@host'] == nodeName}['@vcenter'].toString() 
						vmOperations.VmPowerOff(nodeName, vcenter)
						sleep 10
					    vmOperations.VmPowerOn(nodeName, vcenter)
				    }
			    }
		}
		else
		{
		echo 'Error in CheckAutoLogon MethoException. tasklistCMD return status' + status
		}
	  }
   }
}
/*
Check windows machines, whether it requires restart after windows update. Restart the machine If its required.  
*/
def CheckRestartRequired(host)
{
 def vmOperations = new Utils.VmOperationsUtils()
 def ReusableFunc = new Utils.GeneralReusables()
 node(host.toString())
 {
   try
     {
	def returnValue = bat label: '', returnStdout: true, script: 'REG QUERY \"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\" /v PendingFileRenameOperations'
	//print "returnValue :"+returnValue
        if(returnValue.contains("HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\Session Manager"))
	  {
	        print "Due to Windows Update, Restart is pending for ${host}. ${host} going to restart by Jenkins"
		print "Please wait this process may take a few minutes"
		def nodeName = host.toString();
		def readFileContents = ReusableFunc.ReadXml()	// Read Xml input file
		def config = new XmlSlurper().parseText(readFileContents)
		def vcenter = config.MachineDetails.'**'.find { node -> node['@host'] == nodeName}['@vcenter'].toString() 
		if(!vcenter.contains("Physical Machine"))
		{
		  vmOperations.VmPowerOff(nodeName, vcenter)
		  sleep 10
		  vmOperations.VmPowerOn(nodeName, vcenter)
		  sleep 10
		}
		else
		{
		  print "WARNING: ${host} is a Physical Machine. Please restart the Machine Manually And Start Execution Again"	
		}
		/*isPatchUpdated = false
		print "Build Marked as Failed due to restart ${host} Computer ID " +  computerID
		currentBuild.result = 'Failure'*/
         }
     }
     catch(Exception e)
     {
	echo"Ignore Message: "+e.toString()
	print "Restart not required for this node ${host}"
     }			
 }
	
}
