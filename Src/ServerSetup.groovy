/**
 * Perform Server Operations - Download a build from Jenkins, copy a build from other network, Checkout SCM and Execute commands
 */
package ServerSetup
import groovy.json.JsonSlurperClassic
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.XmlParser
import groovy.util.slurpersupport.GPathResult
import java.util.regex.Matcher;
import java.util.regex.Pattern;

def ServerOperations()
{
	def FileOperations = new Utils.FileOperations()
	def ReusableFunc = new Utils.GeneralReusables()
	def BuildUtils = new Utils.BuildOperationsUtils()
	def Email = new Utils.EmailUtils()
	def remoteOperation = new Utils.RemoteOperations()
	//#InputFile Keywords and given variables should be matched
	def BuildDownload = "DownloadBuild"
	def BuildCopy = "CopyBuild"
	def ExecuteFile = "ExecuteFile"
	def SVNCheckout = "SvnCheckout"
	def ExecuteCommand = "ExecuteCommand"
	def TestExecuteCmd ="TestExecuteCommand"
	def xmlNode = "ServerSetup"
	def parallelExec = [:]         // Empty map for parallel execution
	def hostnames = []	 
	def labelnames = []
    	def readFileContents = ReusableFunc.ReadXml()
   	def config = new XmlSlurper().parseText(readFileContents)
	boolean stageExist = ReusableFunc.isXmlNodeExists(xmlNode)  // Checks ServerSetup Stage in input XMl file
	if(!stageExist)
	{
		config.ServerSetup.Machine.each {node -> hostnames.push(node['@host'].toString())}  // Collect all nodes from input file
		config.ServerSetup.Machine.each {node -> labelnames.push(node['@label'].toString())}
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
		println "Host name "+hostnames.toString()
		for (host in hostnames) {
			if(ReusableFunc.checkNodeStatus(host)){   //Checks Nodes availability
				AvailNodes.add(host)
			}
			else if(ReusableFunc.getValueFromXML("EmailSettings", "NodeOfflineNotify") == "Yes"){
				println "Off-line Node: "+host
				Email.EmailNotification(null,null,"NodeNotExist",host.toString())  // Notification send to email recipients, If node is off-line 
			}
		}
		hostnames = AvailNodes.toArray()         //Collect all on-line nodes
		println "Available host names: "+AvailNodes.toString()
		for (host in hostnames) {
			remoteOperation.CheckAutoLogon(host)			//Reboot Machines if the machines are already logged in
		}
		for (host in hostnames) {
			def nodeName = host.toString()
			parallelExec [nodeName] = {
			node(nodeName) 
			{  
				def action,BuildDesc,BuildOutloc,RepoUrl,ExecuteCmd,TestExecmd,TestExeProjPath = null
				if(labelnames.size() < 1){labelnames = labelnames+hostnames}
				for(int i=0;i<labelnames.size(); i++)
				{		                   // getting input details from xml file
					action = ReusableFunc.getXmlValue("ServerSetup",nodeName,"action",labelnames[i].toString())   
					BuildDesc = ReusableFunc.getXmlValue("ServerSetup",nodeName,"BuildDesc",labelnames[i].toString())
					BuildOutloc = ReusableFunc.getXmlValue("ServerSetup",nodeName,"BuildOutloc",labelnames[i].toString())
					//ExecutionFile = ReusableFunc.getXmlValue("ServerSetup",nodeName,"ExecutionFile",labelnames[i].toString()
					RepoUrl = ReusableFunc.getXmlValue("ServerSetup",nodeName,"CheckoutUrl",labelnames[i].toString())
					ExecuteCmd = ReusableFunc.getXmlValue("ServerSetup",nodeName,"ExeCmd",labelnames[i].toString())
					TestExeProjPath= ReusableFunc.getXmlValue("ServerSetup",nodeName,"ProjectPath",labelnames[i].toString())
					TestExecmd = ReusableFunc.getXmlValue("ServerSetup",nodeName,"TestExeCmd",labelnames[i].toString())
					echo "Hostname: ${nodeName}; Actions: ${action}; BuildDesc: ${BuildDesc}; BuildOutloc: ${BuildOutloc}; RepoUrl: ${RepoUrl}"
				}
				def ExeCount =0
				def RepoUrlCount =0
				def BuildDescCount = 0
				def BuildOutlocCount =0
				def TestExeCount =0
				def TestExeProjCount =0
				for(int i=0;i<action.size(); i++)
				{
					def actions =action[i].toString()
					def buildoutloc,builddesc = []
					String repourl,execmd,testexecmd = null
					if(actions.contains(BuildDownload))  
					{
						builddesc = BuildDesc[BuildDescCount].join(",")
						BuildDescCount = BuildDescCount+1
						print"builddesc :"+builddesc
						buildoutloc = BuildOutloc[BuildOutlocCount].join(",")
						BuildOutlocCount = BuildOutlocCount+1
						print"buildoutloc :"+buildoutloc
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
						println "downloadUrl:"+downloadUrl
						def filePath = buildoutloc+"\\"+"archive.zip"
						if(isUnix()){ filePath=buildoutloc+"//"+"archive.zip" } // assigning build output location
				
						BuildUtils.DownloadByHttpReq(filePath,downloadUrl)         // download build by using HTTP request
				
						FileOperations.fileUnZipOperation(filePath,buildoutloc)    // Extract the Build
					}
					else if(actions.contains(BuildCopy))
					{	
						builddesc = BuildDesc[BuildDescCount].join(",")
				     	BuildDescCount = BuildDescCount+1
				     	print"builddesc :"+builddesc
				     	buildoutloc = BuildOutloc[BuildOutlocCount].join(",")
				    	BuildOutlocCount = BuildOutlocCount+1
				    	print"buildoutloc :"+buildoutloc
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
					else if(actions.contains(SVNCheckout)) // Checking out files from SVN
					{			
						repourl = RepoUrl[RepoUrlCount].join(",")
						print"repourl :" +repourl
						BuildUtils.SVNCheckOut(repourl)	
						RepoUrlCount = RepoUrlCount+1				
					}
					else if(actions.equals(ExecuteCommand))  		// Execute Shell & Bat Command
					{
						string execmd = ExecuteCmd[ExeCount].join(",")
						print"execmd :"+execmd
						if(isUnix())								// Check the given machine is Linux or windows
						{
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
					else if(actions.equals(TestExecuteCmd))
					{
						/*	string execmd = ExecuteCmd[ExeCount].toString()
						testexecmd = TestExecmd[TestExecCount].toString()
						print"execmd :"+execmd
						print"TestExecCount: "+testexecmd
						def guestPwd = Matcher.quoteReplacement(config.MachineDetails.'**'.find { node -> node['@host'] == nodeName}['@password'].toString())
						guestPwd = Matcher.quoteReplacement(ReusableFunc.decryptingPasswords("${EncryptionKey}", "${guestPwd}").trim()) 	 			  
						def guestuser = config.MachineDetails.'**'.find { node -> node['@host'] == nodeName}['@userName'].toString()
						BuildUtils.RunTestExecute(execmd,testexecmd,guestuser,guestPwd)
						ExeCount = ExeCount+1
						TestExecCount = TestExecCount+1 */
						string testexecmd = TestExecmd[TestExeCount].toString()
						string testexepath = TestExeProjPath[TestExeProjCount].toString()
						print"Arguments :"+testexecmd
						print"TestExeProjPath: "+testexepath
						echo "Executing TestExecute command through Ansible" 
						def TestExePath = ReusableFunc.FindTestExecuteExePath()
						print "TestExePath :"+TestExePath
						node(MasterNode) {
							def InventYML = "${MasterWorkspace}" +"//src//ConfigFiles//Inventoryfile.yml"
							print"InventYML:"+InventYML
							//ansibleVault action: 'encrypt', input: "${InventYML}", installation: 'ansible_2.8.0', vaultCredentialsId: 'AnsibleVaultPassword'
							ansiblePlaybook installation: 'ansible_2.8.0', inventory: "${InventYML}", playbook: "${TestExecutePlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars 'target=${nodeName} ProjectPath=\"${testexepath}\" TestExecutePath=\"${TestExePath}\" Arguments=\"${testexecmd}\"'"
						}
					}
				}
			}
		}
	 }
	 parallel parallelExec
	}
	else{
		echo "ServerSetup stage inputs not available in input XML File"
	}
}
