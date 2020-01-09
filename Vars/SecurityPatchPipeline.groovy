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

def call(arg)
{
	def isPatchUpdated = false
	def BuildUtils = new Utils.BuildOperationsUtils()
	if(arg == "CheckPatchUpdate")
	{
		stage("CheckPatchUpdate")
		{
			def hostnames = []
			def computerIDs = []
			def patchUpdatedMachines = []
        		def readFileContents = new File("${env.xmlPath}").getText()
        		def config = new XmlSlurper().parseText(readFileContents)
			config.MachineDetails.Machine.each {node -> hostnames.push(node['@host'].toString())}	                
			println "Hostname "+hostnames.toString()
          
        		// Get computer ID from Computer Name          
			for(String host in hostnames)
			{
				def response = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', ignoreSslErrors: true, quiet: true, responseHandle: 'NONE', url: 'https://bigfixdev.products.network.internal:9081/api/patch/computers?token=2b47329db830853a112ff077a3e8ddb4bbb253d0'                     
          			def json = new JsonSlurper().parseText(response.content)["rows"]                    
          			json.each
          			{
					if(it["name"] == host)            		         
					computerIDs.add(it["id"])
          			}       
	 		}
         		println computerIDs .toString()   
			
			//Iterate through each machine and find out patch is applied or not
	 		for(def computerID in computerIDs)
	 		{
		 		def patchResponse = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', ignoreSslErrors: true, quiet: true, responseHandle: 'NONE', url: 'https://bigfixdev.products.network.internal:9081/api/patch/computers/' + computerID + '/patch_results?token=2b47329db830853a112ff077a3e8ddb4bbb253d0'
		 		def patchResultJson = new JsonSlurper().parseText(patchResponse.content)["rows"]      
		 		if(patchResultJson.resource.size() > 0)
		 		{		 
			 		def lastPatchUpdatedDate = patchResultJson[patchResultJson.resource.size()-1]["patch"]["updated_at"]
			 		println lastPatchUpdatedDate 

			 		// Ensure Patch update done in last 15 days
			 		def (value1, value2) = lastPatchUpdatedDate.tokenize( 'T' ) 
			 		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			 		String todayDate = dateFormat.format(new Date())
			 		String startDate = dateFormat.format(new Date().minus(15))
			 		Date updateStartdate = dateFormat.parse(startDate);
			 		Date updateEnddate = dateFormat.parse(todayDate);
			 		Date actualUpdatedDate = dateFormat.parse(value1);       
			 		if(actualUpdatedDate >= updateStartdate && actualUpdatedDate <= updateEnddate)
			 		{
				 		patchUpdatedMachines.add(computerID)
				 		println "Patch update done for Computer ID " +  computerID
			 		}
			 		else			 
			 			println "No Patch update for Computer ID " +  computerID			
	 			}
				else			 
					println "No Patch update for Computer ID " +  computerID
	 		}
	 		if(patchUpdatedMachines.size() > 0)
	 		{
		 		println "Sanity execution needs to be triggered"
		 		isPatchUpdated = true
	 		}
			else
				println "Sanity execution not required since no update since last 15 days."	
			}		
		}
	else
	{	
		
		stage("Reportgeneration")
		{ 
			sleep 30
			def hostnames = []
			def reportHost = []
			
			// Read Host details
			def readFileContents = new File("${env.xmlPath}").getText()
        		def config = new XmlSlurper().parseText(readFileContents)
			config.MachineDetails.Machine.each {node -> hostnames.push(node['@host'].toString())}			
			config.MachineDetails.Machine.each {node -> if(node['@label'] == "REPORT"){reportHost.push(node['@host'].toString())}}
			
			// Copy the Report to Jenkins from client machines
			File resultDir = new File("/tmp/Results")			
			if(resultDir.exists())
			{
				FileUtils.cleanDirectory(resultDir)
				FileUtils.forceDelete(resultDir)				
			}			
			resultDir.mkdir()
			
			println "Hostname "+hostnames.toString()
			for (host in hostnames) 
			{
				// Create a temp directory
				File directory = new File("/tmp/Results/" + host + "/temp")
				directory.mkdir()
				masterPath = new FilePath(null, "/tmp/Results/" + host + "/temp")
				println "Master Path : " + masterPath
				def resultFolderName
				
				node(host)
				{
					println 'Inside Node ' + host
					nodeChannel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel() 	
					nodePath = new FilePath(nodeChannel, "${TestResultPath}")				
					if(nodePath.exists())
					{		
						// Find out the latest Report
						List<FilePath> dirList = nodePath.listDirectories()
						def latestDate = new Date(dirList[0].lastModified())
						def latestFilePath = dirList[0]
						for(int i = 1; i < dirList.size(); i++)
						{
						//	println dirList[i]
						 	println new Date(dirList[i].lastModified())
							 if(latestDate.before(new Date(dirList[i].lastModified())) && dirList[i].getBaseName().startsWith("TestResult"))
							{
								latestDate = new Date(dirList[i].lastModified())
								latestFilePath = dirList[i]
							}
						}						
						SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
						def todayDate = format.format(new Date())
						latestDate = format.format(latestDate)						
						if(latestDate.compareTo(todayDate) == 0)
						{
							latestFilePath.copyRecursiveTo(masterPath)
						}
						resultFolderName = latestFilePath.getBaseName()
					}
			 	}
				
				// Rename the temp folder				
				File newDirectory = new File("/tmp/Results/" + host + "/" + resultFolderName)
				directory.renameTo(newDirectory)
			}
			
			// Copy the report to destination machine
			finalSourcePath = new FilePath(null, "/tmp/Results")
			println finalSourcePath
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_hhmmss")
			String todayDate = dateFormat.format(new Date())			
			node(reportHost[0])			
			{
				println "Inside Results Node "
				Channel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel() 
				finalDestPath = new FilePath(Channel, "C:\\PatchUpdateResults\\PACS\\" + todayDate + "\\ExecutionResults")
				finalDestPath.mkdirs()
				finalSourcePath.copyRecursiveTo(finalDestPath)
				finalreportpath = new FilePath(Channel, "C:\\PatchUpdateResults\\PACS\\" + todayDate + "\\FinalResults")
				finalreportpath.mkdirs()
				BuildUtils.RunBatCmd("cd /d c:\\PDFGenerator && PdfGenerator.exe -project \"PACS\" -filepath \"${finalDestPath}\" -patchDate \"July 29, 2019\" -output \"${finalreportpath}\"")
			}			
				
			FileUtils.cleanDirectory(resultDir)
			FileUtils.forceDelete(resultDir) 	
		}
	}
	return isPatchUpdated
}
