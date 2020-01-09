import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.GPathResult
import groovy.util.XmlParser
import jenkins.model.*
import hudson.slaves.*
import java.util.*;
import groovy.stream.*
import hudson.FilePath
import java.io.IOException
import org.apache.commons.io.FileUtils
import jenkins.model.Jenkins
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Calendar;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter
import static java.time.temporal.TemporalAdjusters.firstInMonth;

def call()
{
	def IBMBigFixToken = "45d2bce70f2520af7e5f88b5b687fb6e910b6cb6"  //Access Token for IBMBIGFIX Tool
	def BuildUtils = new Utils.BuildOperationsUtils()
	def ReusableFunc = new Utils.GeneralReusables()
	def Fileoperations = new Utils.FileOperations()
	Vspherecre = credentials('VSPHERE')
	//sleep 30
	def hostnames = []
	def reportHost = []
	def ConReportGen = true
	def Executionnode, ExecutionDate
	// Read Host details
	def readFileContents = ReusableFunc.ReadXml()
        def config = new XmlSlurper().parseText(readFileContents)
	//config.MachineDetails.Machine.each {node -> hostnames.push(node['@host'].toString())}	
	config.ClientSetup.Machine.each {node -> hostnames.push(node['@host'].toString())}	
	config.MachineDetails.Machine.each {node -> if(node['@label'] == "REPORT"){reportHost.push(node['@host'].toString())}}
			
	// Copy the Report to Jenkins from client machines
	File resultDir = new File("/tmp/Results")			
	if(resultDir.exists())
		{
			FileUtils.cleanDirectory(resultDir)
			FileUtils.forceDelete(resultDir)				
		}			
		resultDir.mkdir()
		SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd_hhmmss")
		String todayDate1 = dateFormat1.format(new Date())
		ExecutionDate = todayDate1  //Reference date for Collecting all host results
		print"Execution Results Folder Date:" + ExecutionDate
		println "Hostname "+hostnames.toString()
		 for (host in hostnames) 
			{
				def resultFolderName
				node(host)
				{	
					Executionnode = host
					nodeChannel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel()
					def ReportPath = ReusableFunc.getXmlValue("ClientSetup",host,"TestReportPath","") 
					print"ReportPath from XML: "+ReportPath
					def TestResultPath = ReportPath[0].toString()
					nodePath = new FilePath(nodeChannel, "${TestResultPath}")				
					if(nodePath.exists())
					{		
						// Find out the latest Report
						List<FilePath> dirList = nodePath.listDirectories()
						//def latestDate = new Date(dirList[0].lastModified())
						def latestFilePath = dirList[0]
						for(int i = 0; i < dirList.size(); i++)
						{
							//println new Date(dirList[i].lastModified())
							// if(latestDate.before(new Date(dirList[i].lastModified())) && dirList[i].getBaseName().startsWith("TestResult"))
							
							def latestDate = new Date(dirList[i].lastModified())
							latestFilePath = dirList[i]
							SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
							def todayDate = format.format(new Date())
							latestDate = format.format(latestDate)	
							print"latest Report Date :"+latestDate
							print "Today date: "+todayDate	
							print"latestDate.compareTo(todayDate):"+latestDate.compareTo(todayDate)
							print "latestFilePath.getBaseName():"+latestFilePath.getBaseName()
							if(latestDate.compareTo(todayDate) == 0 && latestFilePath.getBaseName().startsWith("TestResult"))
							{	
								print"latestFilePath :"+latestFilePath
								node(reportHost[0])
								{	
									Channel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel() 
									Executionnode = host.toString()
									println 'Executionnode Node ' + Executionnode
									 				
									finalDestPath = new FilePath(Channel, "c:\\PatchUpdateResults\\${ProjectName}\\" + todayDate1 + "\\ExecutionResults\\"+Executionnode+"\\"+latestFilePath.getBaseName())
									finalDestPath.mkdirs()
									finalDestCopyPath = "\\PatchUpdateResults\\${ProjectName}\\" + todayDate1 + "\\ExecutionResults\\"+Executionnode+"\\"+latestFilePath.getBaseName()
								
								}
								CopyFromFolder = latestFilePath
								CopytoFolder = "\\\\"+reportHost[0]+finalDestCopyPath
								username = config.MachineDetails.'**'.find { node -> node['@label'] == "REPORT"}['@userName'].toString()
								def guestPwd = Matcher.quoteReplacement(config.MachineDetails.'**'.find { node -> node['@label'] == "REPORT"}['@password'].toString())
								pswd = ReusableFunc.decryptingPasswords("${EncryptionKey}", "${guestPwd}").trim() 	 			  
							
								def status = Fileoperations.CopyToOtherNetwork(CopyFromFolder,CopytoFolder,username,pswd)
								if(status != 0)
								{
									ConReportGen = false
									print "Error!! Copy Reports operation failed. Copy files from ${CopyFromFolder} location to  ${CopytoFolder} with username ${username} and password {pswd} Failed"
									currentBuild.result = 'Failure'
								}
								
							}
							else
							{
							print "No Results found  with today Date"
							}
						}						
					}
					else
					{
						println "${host} TestResult path ${nodePath} is not present"
					}
			 	}
				
			} 
			if(ConReportGen)
			{
				node(reportHost[0])			
				{
					println "Inside Results Node "+reportHost[0]
					
					//Getting Second Week of Tuesday date 
					LocalDate now = LocalDate.now(); //2019-10-21
					LocalDate firstTuesDay = now.with(firstInMonth(DayOfWeek.TUESDAY));
					LocalDate SecondTuesDay = firstTuesDay.plusDays(7);
					print("SecondTuesDay :"+SecondTuesDay);
					def patchDate = SecondTuesDay.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")); //October 8, 2019
					print"Current Month Patch Date:"+patchDate
					
					def projectName = "${env.ProjectName}"
					Channel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel() 
					finalDestPath = new FilePath(Channel, "C:\\PatchUpdateResults\\${ProjectName}\\" + ExecutionDate + "\\ExecutionResults")
					if(!finalDestPath.exists()){ finalDestPath.mkdirs()}
					finalreportpath = new FilePath(Channel, "C:\\PatchUpdateResults\\${projectName}\\" + ExecutionDate + "\\FinalResults")
					if(!finalreportpath.exists()){ finalreportpath.mkdirs()}
					def ConsolidateReportPath = new FilePath(Channel, "C:\\PatchUpdateResults\\${ProjectName}\\" + ExecutionDate + "\\Consolidated")
					if(!ConsolidateReportPath.exists()){ ConsolidateReportPath.mkdirs()}
					def DestRepoPath = finalreportpath.toString()
					print "DestRepoPath:"+DestRepoPath
					List<FilePath> dirList = finalDestPath.listDirectories()
															
					//Running Consolidate Report Generator Exe
					String Sourcepath = dirList.join(",")
					print "Available folders in reports path:"+Sourcepath
					def ConsolidateStatus = BuildUtils.RunBatCmd("cd /d c:\\ConsolidateReportGenerator && MergeReport.exe -Project \"${projectName}\" -InputFilePath \"${Sourcepath}\" -OutputFilePath \"${ConsolidateReportPath}\" -VsphereUserName \"${Vspherecre_USR}\" VsphereuserPassword \"${Vspherecre_PSW}\" -SecurityReport \"security\" > consolidateReport.txt")
					
					node(MasterNode) {
						def InventYMLNew = "${MasterWorkspace}" +"//src//ConfigFiles//Inventoryfile.yml"
						print"InventYML:"+InventYMLNew
						try
						{
							ansiblePlaybook installation: 'ansible_2.8.0', inventory: "${InventYMLNew}", playbook: "${RunReportGenPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars 'target=${reportHost[0].toString()} projectName=\"${projectName}\" sourceReport=\"${finalDestPath}\" patchDate=\"${patchDate}\" DestReportPath=\"${DestRepoPath}\" JobId=\"${env.BUILD_NUMBER}\" bigfixToken=\"${IBMBigFixToken}\" UpdateSecurityDB=\"no\"'"
						}
						catch(Exception ex)
						{
							echo" Report Generator code return with error "+ex
						}
					}
					//Check whether Pdf File is generated 
					List<FilePath> ReportDirList = finalreportpath.list()
						boolean PdfStatus = false
						boolean consolidateRepo = false
						for(int i = 0; i < ReportDirList.size(); i++)
						{
							def filename = ReportDirList[i].getName()
							if(filename.contains("pdf"))
							{
								PdfStatus = true
								print "PDF File is Generated"
							}
							if(filename.contains("Consolidated"))
							{
								consolidateRepo = true
							}
						}
						if(!PdfStatus)
						{
							println"Error!! Security Patch PDF files are not generated. Due to some failure cases in the report"
							currentBuild.result = 'Failure'
						}
						if(!consolidateRepo)
						{
							println"Error!! Consolidated PDF Reports are not generated."
							currentBuild.result = 'Failure'
						}
						else
						{
							print "Consolidated PDF Report Generated"
						}
				}	 	
			}
			else
			{
				println " Some of the Execution Machines results copy has failed. Please check job log for more details "
			}
}

