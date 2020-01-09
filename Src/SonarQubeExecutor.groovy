package ScanLibrary

import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.* 
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.XmlParser
import java.net.InetAddress;
import java.net.UnknownHostException;
import hudson.model.Computer.ListPossibleNames
import groovy.io.FileType
import java.io.File
import java.nio.file.FileSystems

def SonarOperations()
{
	def ReusableFunc = new Utils.GeneralReusables()
	def FileOperations = new Utils.FileOperations()
	def SonarUtils = new Utils.SonarQubeUtils()
	def BuildUtils = new Utils.BuildOperationsUtils()
	def Email = new Utils.EmailUtils()
	def xmlNode = "SonarQube"
	boolean stageExist = ReusableFunc.isXmlNodeExists(xmlNode)
	if(!stageExist)
	{
		def SonarProjectName = null
		def EmailNotification = ReusableFunc.getValueFromXML("EmailSettings", "SonarQubeResult")
		def PrgmLanguage = ReusableFunc.getValueFromXML("SonarQube", "PrgmLanguage").trim()
		def svnpath = ReusableFunc.getValueFromXML("SonarQube", "svnpath")
		def Projectlocation = ReusableFunc.getValueFromXML("SonarQube", "Solutionfilelocation")
		def jarlocation = "E:\\Jenkins_WorkSpace\\cnesreport_jar_file"
		def nodeName = "ICA-TST5-WS12"
		def sonarhomelocation = "Sonar_MSBuild"
		def sonarurl = "Sonar_Qube"
		def MsBuildLocation = "MSBuild_Home"
		String docfilename = null
		String xlfilename = null
		def token = null
		String NodeJobLoc = null
		if(Jenkins.instance.getNode(nodeName.toString()).toComputer().isOnline())
		{
			node(nodeName)
			{
				echo "Workspace used : " + workspace
				def username = "admin"
				def password = "admin"
				sleep(2)
				def status  = null
				BuildUtils.SVNCheckOut(svnpath)
				if(PrgmLanguage.equalsIgnoreCase("javamaven"))
				{
					SonarProjectName = "JavaMavenProject"
					def Pomlocation = ReusableFunc.getValueFromXML("SonarQube", "PomLocation")
					echo "Pom file location is : " + Pomlocation
					def Mavenlocation = ReusableFunc.getValueFromXML("SonarQube", "Mavenlocation")
					def mvnloc = tool Mavenlocation
					echo "maven home location : " + mvnloc
					SonarUtils.ScanProject_JavaMaven(SonarProjectName, sonarhomelocation, SonarProjectName, Mavenlocation, sonarurl)
				}
				else if(PrgmLanguage.equalsIgnoreCase("javaant"))
				{
					SonarProjectName = "JavaAntProject"
					def Buildxmllocation = ReusableFunc.getValueFromXML("SonarQube", "buildxmllocation")
					echo "build.xml file location is : " + Buildxmllocation
					def sonarantjarlocation = ReusableFunc.getValueFromXML("SonarQube", "SonarAntJar")
					echo "Ant jar for sonar location is : " + sonarantjarlocation
					SonarUtils.ScanProject_JavaAnt(SonarProjectName, Buildxmllocation, sonarantjarlocation, sonarhomelocation, sonarurl)
				}
				else if(PrgmLanguage.equalsIgnoreCase("C#")||PrgmLanguage.equalsIgnoreCase("Csharp"))
				{
					SonarProjectName = "CSharpProject"
					def projfileloc = SonarUtils.getFilefromextension(workspace, "csproj")
					println projfileloc.toString()
					def projloc = SonarUtils.getParentfolder(projfileloc)
					println "projloc:" +projloc
					def restorecmd = projloc + "\\nuget.exe restore " + projloc + "\\packages.config -PackagesDirectory " + projloc + "\\packages"
					echo ('restore nuget package command to be executed is : ' + restorecmd)
					bat label: '', script: restorecmd
					SonarUtils.ScanProject_Csharp(SonarProjectName, sonarhomelocation, sonarurl, MsBuildLocation)
				}
				else
				{
					SonarProjectName = "OtherProject"
					echo "Project file location is : " + Projectlocation
					def machineOS = ReusableFunc.getValueFromXML("SonarQube", "MachineOS")
					echo "OS in which scanning occurs is : " + machineOS
					SonarUtils.ScanProject_Others(SonarProjectName, sonarhomelocation, sonarurl, Projectlocation)
				}

				sleep(120)
				def tokengen = SonarUtils.generatedtoken
				def SonarIp = SonarUtils.sonarip 
				echo "Token generated from SonarUtils is : " + tokengen
				SonarUtils.GenerateReport(SonarProjectName, jarlocation, SonarIp, tokengen)
				Date date = new Date()
				docfilename = date.format("yyyy-MM-dd") + "-" + SonarProjectName + "-analysis-report.docx"
				println "docfilename is : " + docfilename
				xlfilename = date.format("yyyy-MM-dd") + "-" + SonarProjectName + "-issues-report.xlsx"
				println "xlsfilename is : " + xlfilename
				NodeJobLoc = jarlocation
				println "NodeJobLoc:" +NodeJobLoc
				}
			//Sending SonarQube Results to Email
			if(EmailNotification == "Yes")
			{
				node(env.masternode)
				{	
					def SonarIp = SonarUtils.sonarip 
					def workspace = env.MasterWorkSpace
					println "Emil Notification Part"
					//To Copy a result from sonar machine to Jenkins Master node
					CopyFromFolder = NodeJobLoc
					println "CopyFromFolder:" +CopyFromFolder
					copytoFolder = env.MasterWorkSpace
					println "copytoFolder" +copytoFolder
					username = nodeName+"\\Administrator"
					//Channel copytoFolder
					println "Channel copy enter"
					def masterPath = new FilePath(null, copytoFolder)
					println "masterPath:" +masterPath
					
					node(nodeName)
					{
						println 'Inside Node + "${nodeName}"'
							nodeChannel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel() 				   
						nodePath = new FilePath(nodeChannel, "${CopyFromFolder}")
						println "nodePath:" +nodePath
							nodePath.copyRecursiveTo(docfilename,masterPath)				
							//nodePath.copyRecursiveTo(xlfilename,masterPath)				
						println "file copied to: "+masterPath							
					}
					//
					String resultPath = "Sonar Results URL: " + SonarIp + "/dashboard?id=" + SonarProjectName
					//Email.EmailNotification("Sonar Qube Results","http://10.9.39.194:9000/dashboard?id=CICD",null,null,copytoFolder + "/" + docfilename + "," + copytoFolder + "/" + xlfilename)					
					Email.EmailNotification("Sonar Qube Results",resultPath,null,null,copytoFolder + "/" + docfilename)	
				} 
			}
		}
	}
	else
	{
		echo "SonarQube Stage Inputs Not Available In Input XML File"
	}
}
