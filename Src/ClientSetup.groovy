/**
 * Perform Client Operations - Download a build from Jenkins, copy a build from other network, Checkout SCM and Execute commands
 */
package ClientSetup

import groovy.json.JsonSlurperClassic
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.XmlParser
import groovy.util.slurpersupport.GPathResult
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import hudson.model.Node
import hudson.model.Slave
import jenkins.model.Jenkins
import java.util.regex.* 
import hudson.FilePath
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.*;

def ClientOperations()
{
	def ReusableFunc = new Utils.GeneralReusables()
	def BuildUtils = new Utils.BuildOperationsUtils()
	def FileOperations = new Utils.FileOperations()
	def Email = new Utils.EmailUtils()
	def remoteOperation = new Utils.RemoteOperations()
	// InputFile Keywords and given variables should be matched
	def BuildDownload = "DownloadBuild"
	def BuildCopy = "CopyBuild"
	def ExecuteFile = "ExecuteFile"
	def SVNCheckOut = "SvnCheckout"
	def GITCheckOut = "GitCheckout"
	def ExecuteCommand = "ExecuteCommand"
	def TestExecuteCmd ="TestExecuteCommand"
	def SeleniumCmd = "SeleniumCommand"
	def xmlNode = "ClientSetup"
	def parallelExec = [:]         // Empty map for parallel execution
	def hostnames = []	
	def labelnames = []
	def readFileContents = ReusableFunc.ReadXml()
    //def readFileContents = new File("${env.xmlPath}").getText()			// Read Xml input file
    def config = new XmlSlurper().parseText(readFileContents)
	boolean stageExist = ReusableFunc.isXmlNodeExists(xmlNode)			// Checks ServerSetup Stage in input XMl file
	if(!stageExist)
	{
	config.ClientSetup.Machine.each {node -> hostnames.push(node['@host'].toString())}			// Collect all nodes from input file
	config.ClientSetup.Machine.each {node -> labelnames.push(node['@label'].toString())}
	labelnames = labelnames.minus("");
        	
	def NodeNames = []
	
	for(int i=0;i<labelnames.size(); i++)
	{
		NodeNames = ReusableFunc.GetNodeNames(labelnames[i].toString())
		println"Label NodeNames: "+NodeNames.toString()
	}
	print"All host list :"+hostnames.toString()
	hostnames = hostnames+NodeNames
	hostnames = hostnames.minus("")
	println"All Nodes List(Label + Hosts): "+hostnames
	def AvailNodes = []
	hostnames = hostnames.toArray();
	println "Hostname "+hostnames.toString()
	for (host in hostnames) {
		if(ReusableFunc.checkNodeStatus(host)){			 // Checks Nodes availability
			AvailNodes.add(host)
		}
		else if(ReusableFunc.getValueFromXML("EmailSettings", "NodeOfflineNotify") == "Yes"){
			println "Offline Node: "+host
			Email.EmailNotification(null,null,"NodeNotExist",host.toString())		// Notification send to email recipients, If node is off-line 
		}
	}
	hostnames = AvailNodes.toArray()		 //Collect all on-line nodes
	println "Available host names: "+AvailNodes.toString()
	for (host in hostnames) {
		remoteOperation.CheckAutoLogon(host)			//Reboot Machines if the machines are already logged in
	}
    for (host in hostnames) {
		def nodeName = host.toString()
		parallelExec [nodeName] = {
		node(nodeName) {
		def action,BuildDesc,BuildOutloc,RepoUrl,ExecuteCmd,TestExecmd,Seleniumcmd,XMLPath,TestExeProjPath,ExePath,PsexecArg,GitRepoUrl,GitBranchName = null
		if(labelnames.size() < 1){labelnames = labelnames+hostnames}
		for(int i=0;i<labelnames.size(); i++)
			{	
				print "nodeName :"+nodeName
				print "labelnames[i].toString() "+labelnames[i].toString()
				action = ReusableFunc.getXmlValue("ClientSetup",nodeName,"action",labelnames[i].toString())
				BuildDesc = ReusableFunc.getXmlValue("ClientSetup",nodeName,"BuildDesc",labelnames[i].toString())
				BuildOutloc = ReusableFunc.getXmlValue("ClientSetup",nodeName,"BuildOutloc",labelnames[i].toString())
				//ExecutionFile = ReusableFunc.getXmlValue("ClientSetup",nodeName,"ExecutionFile",labelnames[i].toString())
				RepoUrl = ReusableFunc.getXmlValue("ClientSetup",nodeName,"CheckoutUrl",labelnames[i].toString())
				ExecuteCmd = ReusableFunc.getXmlValue("ClientSetup",nodeName,"ExeCmd",labelnames[i].toString())
				TestExeProjPath= ReusableFunc.getXmlValue("ClientSetup",nodeName,"ProjectPath",labelnames[i].toString())
				TestExecmd = ReusableFunc.getXmlValue("ClientSetup",nodeName,"TestExeCmd",labelnames[i].toString())
				Seleniumcmd = ReusableFunc.getXmlValue("ClientSetup",nodeName,"SeleniumCmd",labelnames[i].toString())
				XMLPath = ReusableFunc.getXmlValue("ClientSetup",nodeName,"XMLPath",labelnames[i].toString())
				PsexecCmd = ReusableFunc.getXmlValue("ClientSetup",nodeName,"PsexecCmd",labelnames[i].toString())
				WaitForTerminate = ReusableFunc.getXmlValue("ClientSetup",nodeName,"WaitForTerminate",labelnames[i].toString())
				GitRepoUrl = ReusableFunc.getXmlValue("ClientSetup",nodeName,"GitCheckoutUrl",labelnames[i].toString())
				GitBranchName = ReusableFunc.getXmlValue("ClientSetup",nodeName,"BranchName",labelnames[i].toString())
				echo "Hostname: ${nodeName}; Actions: ${action}; BuildDesc: ${BuildDesc}; BuildOutloc: ${BuildOutloc} TestExeProjPath: ${TestExeProjPath}"
			}
			def ExeCount =0
			def RepoUrlCount =0
			def BuildDescCount = 0
			def BuildOutlocCount =0
			def TestExeCount =0
			def TestExeProjCount =0
			def selcmdcount =0
			def XMLPathCount = 0
			def PsexecCmdCount = 0
			def WaitForTerminateCount = 0
			def GitRepoUrlCount =0
			def GitBranchNameCount = 0
			for(int i=0;i<action.size(); i++)
			{
				def actions =action[i].toString()
				def buildoutloc,builddesc = []
				String repourl,execmd,testexecmd,gitrepoUrl,gitBranchname = null
				if(actions.contains(BuildDownload))
				{
				     builddesc = BuildDesc[BuildDescCount].join(",")
				     BuildDescCount = BuildDescCount+1
				     print"builddesc : "+builddesc
				     buildoutloc = BuildOutloc[BuildOutlocCount].join(",")
				     BuildOutlocCount = BuildOutlocCount+1
				     print"buildoutloc : "+buildoutloc
				     def BuildUrl= config.BuildAvailability.'**'.find { Server -> Server['@Desc'] == builddesc}['@URL'].toString()
				     def JobName = config.BuildAvailability.'**'.find { Server -> Server['@Desc'] == builddesc}['@Job'].toString()
					   def BuildChoice = config.BuildAvailability.'**'.find { Server -> Server['@Desc'] == builddesc}['@BuildChoice'].toString()
					if (BuildChoice == "LastSuccessful") 
					{
						BuildChoice = "lastSuccessfulBuild"
					}
					else if (BuildChoice == "Predefined") 
					{
						BuildChoice = config.BuildAvailability.'**'.find { Server -> Server['@Desc'] == builddesc}['@PredefinedBuildID'].toString()
					}
					else if (BuildChoice == "Today") 
					{
						BuildChoice = "lastBuild"
					}
					def downloadUrl = "${BuildUrl}/job/${JobName}/${BuildChoice}/artifact/*zip*/archive.zip"
					println "downloadUrl: "+downloadUrl
					def filePath = buildoutloc+"\\"+"archive.zip"
					if(isUnix()){ filePath=buildoutloc+"//"+"archive.zip" } // assigning build output location

					BuildUtils.DownloadByHttpReq(filePath,downloadUrl)         // download build by using HTTP request

					FileOperations.fileUnZipOperation(filePath,buildoutloc)    // Extract the Build
				}
				else if(actions.contains(BuildCopy))
				{
					builddesc = BuildDesc[BuildDescCount].join(",")
				    BuildDescCount = BuildDescCount+1
				    print"builddesc : "+builddesc
				    buildoutloc = BuildOutloc[BuildOutlocCount].join(",")
				    BuildOutlocCount = BuildOutlocCount+1
				    print"buildoutloc : "+buildoutloc
					
					def UserName= config.BuildAvailability.'**'.find { Server -> Server['@Desc'] == builddesc}['@UserName'].toString()
					def Password= config.BuildAvailability.'**'.find { Server -> Server['@Desc'] == builddesc}['@Password'].toString()
					Password = Matcher.quoteReplacement(ReusableFunc.decryptingPasswords("${EncryptionKey}", "${Password}").trim())
					Password = Password.replace("\\","")
					println "Password" +Password
					
					def CopyFromFolder= config.BuildAvailability.'**'.find { Server -> Server['@Desc'] == builddesc}['@NetworkFolderPath'].toString()
					println "CopyFromFolder" +CopyFromFolder
					println "UserName:" +UserName
					println "Password:"+Password
					//Copy a build from another network location
					FileOperations.CopyfromOtherNetwork(CopyFromFolder,buildoutloc,UserName,Password)
				}
				else if(actions.contains(SVNCheckOut)) // Checking out files from SVN
				{	
					//repourl = RepoUrl[RepoUrlCount].join(',')
					repourl = RepoUrl[RepoUrlCount]
					//print"repourl :"+repourl
					BuildUtils.SVNCheckOut(repourl)	
          				RepoUrlCount = RepoUrlCount+1				
				}
				else if(actions.contains(GITCheckOut)) // Checking out files from SVN
				{	
					//repourl = RepoUrl[RepoUrlCount].join(',')
					gitrepoUrl = GitRepoUrl[GitRepoUrlCount]
					gitBranchname = GitBranchName[GitBranchNameCount]
					print"gitrepoUrl :"+gitrepoUrl
					print"gitBranchname :"+gitBranchname
					BuildUtils.GITCheckOut(gitrepoUrl,gitBranchname)	
          			GitRepoUrlCount = GitRepoUrlCount+1	
					GitBranchNameCount = GitBranchNameCount+1
				}
				else if(actions.contains(TestExecuteCmd))
				{
					//Create a Backup for TestReport folder before start TestExecution
					boolean Bkstatus = ReusableFunc.CreateBk_ResultsFolder(nodeName)
					println"Report Backup status "+Bkstatus
					
					string testexecmd = TestExecmd[TestExeCount].toString()
					string finalProjectPath = TestExeProjPath[TestExeProjCount].toString()
					print "Execution Node :"+nodeName
					print"finalProjectPath: "+finalProjectPath
					echo "Executing TestExecute command through Ansible" 
					def TestExePath = ReusableFunc.FindTestExecuteExePath()
					print "TestExePath :"+TestExePath
					node(MasterNode) {
						def InventYML = "${MasterWorkspace}" +"//src//ConfigFiles//Inventoryfile.yml"
						print"InventYML:"+InventYML
						//ansibleVault action: 'encrypt', input: "${InventYML}", installation: 'ansible_2.8.0', vaultCredentialsId: 'AnsibleVaultPassword'
						TestExeCount = TestExeCount+1
						TestExeProjCount = TestExeProjCount+1
						try
						{
							ansiblePlaybook installation: 'ansible_2.8.0', inventory: "${InventYML}", playbook: "${TestExecutePlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars 'target=${nodeName} ProjectPath=\"${TestExeProjPath[0].toString()}\" TestExecutePath=\"${TestExePath}\" Arguments=\"${testexecmd}\"'"
						}
						catch(Exception ex)
						{
							echo" Test Execute code return with error "+ex
							env.continuePipeline = "false"
						}
					}
				}
				else if(actions.equals(SeleniumCmd))
				{
					//Create a Backup for TestReport folder before start TestExecution
					boolean Bkstatus = ReusableFunc.CreateBk_ResultsFolder(nodeName)
					println"Report Backup status "+Bkstatus
					
					echo "Execute Selenium command"
					string selcommand = Seleniumcmd[selcmdcount].toString()
					selcommand = selcommand.replace("\\","\\\\\\")
					string XMLcmdPath = XMLPath[XMLPathCount].toString()
					XMLcmdPath = XMLcmdPath.replace("\\","\\\\\\")
					string dirpath = selcommand.replace("\\\\Selenium.exe","").toString();
					println "dirpath:" +dirpath
					node(MasterNode) {
					def InventYML = "${MasterWorkspace}" +"//src//ConfigFiles//Inventoryfile.yml"
					try
					{
					 ansiblePlaybook installation: 'ansible_2.8.0', inventory: "${InventYML}", playbook: "${SeleniumPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars 'target=${nodeName} DirPath=\"${dirpath}\" SeleniumPath=\"${selcommand}\" Arguments=\"${XMLcmdPath}\"'"
					}
					catch(Exception e)
					{
					 echo "Selenium command failed with error" +e	
					  env.continuePipeline = "false"
					}
					}
				}
				else if(actions.equals(ExecuteCommand))  		// Execute Shell & Bat Command
				{
				    //string execmd = ExecuteCmd[ExeCount].join(",")
					string execmd = ExecuteCmd[ExeCount]
					//print"execmd :"+execmd
					if(isUnix())								// Check the given machine is Linux or windows
					{
						echo "Unix commnad"
						println"This is Unix machine"
						BuildUtils.RunShellCmd(execmd) 
						ExeCount = ExeCount+1 
					}
					else
					{
						BuildUtils.RunBatCmd(execmd)	
						ExeCount = ExeCount+1						
					}
				}
				else if(actions.equals("ExecuteCmdViaPsexec"))  		// Execute Shell & Bat Command
				{
				   //Create a Backup for TestReport folder before start TestExecution
					//boolean Bkstatus = ReusableFunc.CreateBk_ResultsFolder(nodeName)
					//println"Report Backup status "+Bkstatus
					
					echo "Execute Bat command through Psexec"
					string psexecCmd = PsexecCmd[PsexecCmdCount].toString()
					string psexecCmdNew = psexecCmd.replace("\\","\\\\\\\\")
					print"psexecCmd:"+psexecCmdNew
					def waitForTerminate = WaitForTerminate[WaitForTerminateCount]
					print "waitForTerminate:"+waitForTerminate
					def WorkingDirTemp = psexecCmd.split(Pattern.quote("\\"))
					def val =WorkingDirTemp.getAt(WorkingDirTemp.length-1)
					def newVal ="\\"+val
					def WorkingDir = psexecCmd.minus(newVal).toString()
					WorkingDir = WorkingDir.replaceAll("\$", "");
					
					println "Before WorkingDir:" +WorkingDir
					WorkingDir = WorkingDir.replace("\\","\\\\\\\\")
					println "WorkingDir:" +WorkingDir
					
					node(MasterNode) {
					def InventYML = "${MasterWorkspace}" +"//src//ConfigFiles//Inventoryfile.yml"
					try
					{
					 	ansiblePlaybook installation: 'ansible_2.8.0', inventory: "${InventYML}", playbook: "${PsexecPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars 'target=${nodeName} DirPath=\"${WorkingDir}\" Command=\"${psexecCmdNew}\" isWait=\"${waitForTerminate}\"'"
					}
					catch(Exception e)
					{
						 echo "Selenium command failed with error" +e	
					 	 env.continuePipeline = "false"
					}
					PsexecCmdCount = PsexecCmdCount+1
					WaitForTerminateCount = WaitForTerminateCount+1
				   }
				}
 			}
		  }
	   }
	}
	 parallel parallelExec
	}
	else{
		echo "ClientSetup stage inputs not available in input XML File"
	}
}
