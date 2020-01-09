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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import hudson.FilePath
import jenkins.model.*
import hudson.model.Node.Mode
import jenkins.model.Jenkins

def call()
{
	def IBMBigFixToken = "45d2bce70f2520af7e5f88b5b687fb6e910b6cb6" //Access Token for IBM BIG FIX Api
	def isPatchUpdated = true
	def BuildUtils = new Utils.BuildOperationsUtils()
	def ReusableFunc = new Utils.GeneralReusables()
	def remoteOpreation = new Utils.RemoteOperations()
	def hostnames = []
	def computerIDs = []
	def patchUpdatedMachines = []
	def ReqKBUpdates = []
	def reportHost = []
	def readFileContents = ReusableFunc.ReadXml()
        def config = new XmlSlurper().parseText(readFileContents)
	config.MachineDetails.Machine.each {node -> hostnames.push(node['@host'].toString())}	
	config.MachineDetails.Machine.each {node -> if(node['@label'] == "REPORT"){reportHost.push(node['@host'].toString())}}
	if(reportHost == null || reportHost.isEmpty())
	{  
		print "Error!! Add REPORT node details in XML File. If you are running this Job for Security Patch Testing"
		currentBuild.result = 'Failure'
		return false
        }
	else	{ hostnames = hostnames.minus(reportHost[0].toString())	}
	println "Hostname "+hostnames.toString()
	//Get curent month and Year 
	SimpleDateFormat format = new SimpleDateFormat("MMMM-yyyy")
	def todayDate = format.format(new Date())
	//print "todayDate: "+todayDate
	def TempDate = todayDate.toString().split("-");
	//print"TempDate:"+TempDate
	def ReqDate = TempDate[0]+" "+TempDate[1]
	print"Required Patch Month :"+ReqDate
	boolean releasedDateCheck = false
       // Get computer ID from Computer Name          
		for(String host in hostnames)
			{
				def ComputerFullName = null;
				node(host)
				{
					def pattern ="\\d+"
					//remoteOpreation.CheckRestartRequired(host) // To check Machines, whether it requires restart after Windows Update
					def returnData,returnVersion = null;				
					//returnData = bat(encoding: 'UTF-8', label: 'getComputerDetails', returnStdout: true, script: '@systeminfo | findstr /B /C:"OS Name" /C:"System Type"').trim()
					Map<NodeProperty<?>,NodePropertyDescriptor> props = Jenkins.getInstance().getComputer(env['NODE_NAME']).getSystemProperties()
					def releaseId = null
					def type = props['os.arch']
					def osName = props['os.name']
					returnData= osName +" "+type
					//print "Computer Name with Os type: "+returnData
					if((returnData.contains("Windows Server 2012 R2"))||(returnData.contains("Windows 7"))||(returnData.contains("Windows 8.1"))||(returnData.contains("Windows Server 2008 R2"))||(returnData.contains("Windows Server 2016"))){					}
					else{
						returnVersion = bat label: '', returnStdout: true, script: '@REG QUERY \"HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\" /v ReleaseId'
						def returnCode1 = returnVersion.split(' ')								
						for(int j = 0; j< returnCode1.size(); j++){
							def tempvalue = returnCode1[j].replaceAll("\\s","")	
							//print"tempvalue:"+tempvalue
							boolean match = tempvalue ==~ pattern
							if(match){
								releaseId = tempvalue
								print "returnVersion:"+releaseId
							}
						}					
					}
					if(releaseId != null){	ComputerFullName = returnData+" "+releaseId }
					else {ComputerFullName = returnData}
					print"Computer Full Name: "+ComputerFullName
					node(reportHost[0])			
					{
						ReqKBUpdates = []
						def Query = "If Not Exists(select * from MachineDetails where HostName='"+host+"')Begin INSERT INTO MachineDetails (ProductId,HostName,ProductName) VALUES ((SELECT ProductId fROM MSProducts where OSName='"+ComputerFullName+"'),'"+host+"',(SELECT ProductName from  MSProducts where OSName='"+ComputerFullName+"')); End Select ProductName from MachineDetails where HostName='"+host+"'"
						//Update Machine Details Table 
						def returnComputerName = bat label: 'DBUpdate', returnStdout: true, script:"@\"C:\\Program Files\\Microsoft SQL Server\\Client SDK\\ODBC\\170\\Tools\\Binn\\SQLCMD.exe\" -U merge -P merge -S DB-A1-WS8 -d TestKBGenerator -Q \"If Not Exists(select * from MachineDetails where HostName='"+host+"')Begin INSERT INTO MachineDetails (ProductId,HostName,ProductName) VALUES ((SELECT ProductId fROM MSProducts where OSName='"+ComputerFullName+"'),'"+host+"',(SELECT ProductName from  MSProducts where OSName='"+ComputerFullName+"')); End Select ProductName from MachineDetails where HostName='"+host+"'\""
						//print"CoputerNameFromDB:"+returnComputerName
						ComputerFullNameFromDB = GetModifiedContent(returnComputerName,'[a-zA-Z0-9\\s-]*')
						ComputerFullNameFromDB = ComputerFullNameFromDB[1].toString()
						print "CoputerNameFromDB:"+ComputerFullNameFromDB
						//Get Required Kb Updates from Database
						def returnKbdatas = bat label: 'DB', returnStdout: true, script: "@\"C:\\Program Files\\Microsoft SQL Server\\Client SDK\\ODBC\\170\\Tools\\Binn\\SQLCMD.exe\" -U merge -P merge -S DB-A1-WS8 -d TestKBGenerator -Q \"Select distinct(su.KBUpdate) from SecurityUpdates su, MSProducts msp, CriticalUpdates cu where msp.OSName = '"+ComputerFullName+"' and msp.ProductID like cu.ProductID and msp.ProductID like su.productId  and cu.CVENo = su.CVENo and cu.Description = 'Critical' and (su.Subtype='Security Update' or su.SubType= 'Servicing Stack Update') and su.ReleaseMonth = '"+ReqDate+"' and cu.ReleaseMonth ='"+ReqDate+"'\""
						//print"returnKbdatas :"+returnKbdatas
						returnKbdatas = returnKbdatas.replaceAll("-","")
						def returnCode1 = returnKbdatas.split('\n')								
						for(int j = 0; j< returnCode1.size(); j++)
						{
							def tempvalue = returnCode1[j].replaceAll("\\s","")									
							boolean match = tempvalue ==~ pattern
							if(match)
							{
							    ReqKBUpdates.add("${tempvalue}".toString())
							}
							 
						}
						print"ReqKBUpdates for the ${host} is :"+ReqKBUpdates.toString()
					}
					def afterSplit = []
					def returnCode = bat(encoding: 'UTF-8', label: 'getfixid', returnStdout: true, script: '@wmic qfe get HotfixID').trim()
					returnCode = returnCode.replaceFirst("^([\\W]+)<","<")       //To remove unwanted special char in first line of the content
					def returnCode1 = returnCode.split('\n')								
					for(int j = 1; j< returnCode1.size(); j++)
					{
						def tempvalue = returnCode1[j].replaceAll("\\s","")
						afterSplit.add("${tempvalue}".toString())
						
					}
					//afterSplit=afterSplit.remove(0)
					print "Available HotFixID for the ${host}: "+afterSplit
					for(int i=0; i< ReqKBUpdates.size(); i++){										
					if(afterSplit.stream().any {it.replaceAll("[^\\d.]", "").equals(ReqKBUpdates[i].replaceAll("[^\\d.]", ""))})
					{
						println "HotFixID - ${ReqKBUpdates[i]} is updated in ${host}"
					}
					else {
						echo" Error!! HotFixID - ${ReqKBUpdates[i]} is not updated in ${host}"
						isPatchUpdated = false
						print "Build Marked as Failed due to ${ReqKBUpdates[i]} is not updated in ${host} "
						currentBuild.result = 'Failure'
						}
					}
				}
						
			}
			
         		if(isPatchUpdated)
	 		{
		 		println "Sanity execution needs to be triggered"
		 		isPatchUpdated = true
	 		}
			
	return isPatchUpdated
  }		

  def GetComputerName(ComputerName)
  {
   def ComputerFullName = null;
	if(ComputerName.contains("Windows 10") && ComputerName.contains("1903") && ComputerName.contains("x64"))
    	{
		ComputerFullName = "Windows 10 Version 1903 for x64-based Systems"
	}
	else if(ComputerName.contains("Windows 10") && ComputerName.contains("1607") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 10 Version 1607 for x64-based Systems"
	}
  	else if(ComputerName.contains("Windows 10") && ComputerName.contains("1703") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 10 Version 1703 for x64-based Systems"
	}
	else if(ComputerName.contains("Windows 10") && ComputerName.contains("1709") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 10 Version 1709 for x64-based Systems"
	}
	else if(ComputerName.contains("Windows 10") && ComputerName.contains("1803") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 10 Version 1803 for x64-based Systems"
	}
	else if(ComputerName.contains("Windows 10") && ComputerName.contains("1809") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 10 Version 1809 for x64-based Systems"
	}
	else if(ComputerName.contains("Windows 10") && ComputerName.contains("1903") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 10 Version 1903 for x64-based Systems"
	}
	else if(ComputerName.contains("Windows 8.1") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 8.1 for x64-based systems"
	}
	else if(ComputerName.contains("Windows 8.1") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 8.1 for x64-based systems"
	}
	else if(ComputerName.contains("Windows 7") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows 7 for x64-based Systems Service Pack 1"
	}
	else if(ComputerName.contains("Microsoft Windows Server 2012 R2"))
	{
		ComputerFullName = "Windows Server 2012 R2"
	}
	 else if(ComputerName.contains("Microsoft Windows Server 2008 R2") && ComputerName.contains("x64"))
	{
		ComputerFullName = "Windows Server 2008 R2 for x64-based Systems Service Pack 1"
	}
	 else if(ComputerName.contains("Microsoft Windows Server 2016"))
	{
		ComputerFullName = "Windows Server 2016"
	}
  return ComputerFullName
  }

def GetModifiedContent(data,pattern)
{
	def ModifiedContents = []
	if(data.contains("-")){ data = data.replaceAll("-","") }
	def returnCode1 = data.split('\n')								
	for(int j = 0; j< returnCode1.size(); j++)
	{
		def tempvalue = returnCode1[j].trim()							
		boolean match = tempvalue ==~ pattern
		if(match)
		 {
			if((tempvalue != "")||(tempvalue != null)){ ModifiedContents.add("${tempvalue}".toString()) }
		 }
	}
	//print "Avilable Content :"+ModifiedContents.toString()
	def clean = ModifiedContents.findAll { item -> !item.isEmpty() }
	//print"clean:"+clean.toString()
	return clean
}
