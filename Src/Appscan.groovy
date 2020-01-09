/**
 * Checking Web Application security vulnerability - 
 */
package ScanLibrary
import hudson.FilePath
def appscanner()
{
	echo "AppScan started"
	def ReusableFunc = new Utils.GeneralReusables()
	def Email = new Utils.EmailUtils()
	def xmlNode = "AppScan"
	boolean stageExist = ReusableFunc.isXmlNodeExists(xmlNode)
	if(!stageExist)
	{
	def AppScan_Node = "DAST_SCAN"
	def URL = ReusableFunc.getValueFromXML("AppScan", "URL")
	def UserName = ReusableFunc.getValueFromXML("AppScan", "UserName")
	def Password = ReusableFunc.getValueFromXML("AppScan", "Password")
	Password= Password.replace("\\","")
	def RecordedLoginSeq = ReusableFunc.getValueFromXML("AppScan", "RecordedLoginSeq")
	def EmailNotification = ReusableFunc.getValueFromXML("EmailSettings", "AppScanResult")
	if(UserName != "" && Password != "")
	{
		RecordedLoginSeq = ""
		println "Form Based Authentication will effect. So pathRecordedLoginSequence is set to null"
	}
	else
	{
		UserName = ""
		Password = ""
		println "Path Recorded Login Sequence Authentication will effect" +RecordedLoginSeq
	}
	String NodeJobLoc = null
	Date date = new Date()
	String report = date.format("yyyy-MM-dd") + "-" + "-appscanreport.html"
	if(Jenkins.instance.getNode(AppScan_Node.toString()).toComputer().isOnline()){
	node(AppScan_Node)
	{	
		step([$class: 'AppScanStandardBuilder', additionalCommands: '', authScan: true, authScanPw: Password, authScanRadio: true, authScanUser: UserName, generateReport: true, includeURLS: '', installation: 'AppScan Standard', pathRecordedLoginSequence: RecordedLoginSeq, policyFile: '', reportName: report, startingURL: URL, verbose: false])
		NodeJobLoc = env.WORKSPACE 
		println "NodeJobLoc:" +NodeJobLoc
	}
	if(EmailNotification == "Yes"){
		node(MasterNode)
		{
			println "env.masternode:" +env.masternode
			println "MasterNode:" +MasterNode
			def workspace = env.MasterWorkSpace
			println "workspace:" +workspace
			CopyFromFolder = NodeJobLoc
			copytoFolder = env.MasterWorkSpace
			username = AppScan_Node+"\\Administrator"
			println "FileName:"  +report
			println "CopyFromFolder:" +CopyFromFolder
			println "copytoFolder:" +copytoFolder
			println "Channel copy enter"
			def masterPath = new FilePath(null, copytoFolder)
			println "masterPath:" +masterPath
			
			node(AppScan_Node)
			{
				println 'Inside Node + "${AppScan_Node}"'
					nodeChannel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel() 				   
				nodePath = new FilePath(nodeChannel, "${CopyFromFolder}")
				println "nodePath:" +nodePath
					nodePath.copyRecursiveTo(report,masterPath)				
				println "file copied to: "+masterPath							
			}

			copytoFolder = copytoFolder + "//" + report
			Email.EmailNotification("Appscan Results","Please Find the attached Results of Appscan",null,null,copytoFolder)
		} 
	}
	}
	else if(ReusableFunc.getValueFromXML("EmailSettings", "NodeOfflineNotify") == "Yes")
	{
		Email.EmailNotification(null,null,"NodeNotExist",AppScan_Node.toString())
	}
	}
	else
	{
		echo "Appscan Stage Inputs Not Available In Input XML File"
	}
}
