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
        def readFileContents = ReusableFunc.ReadXml()
        def config = new XmlSlurper().parseText(readFileContents)
	config.MachineDetails.Machine.each {node -> hostnames.push(node['@host'].toString())}	                
	println "Hostname "+hostnames.toString()
	//Get curent month and Year 
	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
	def todayDate = format.format(new Date())
	print "todayDate: "+todayDate
	def TempDate = todayDate.toString().split("-");
	print"TempDate:"+TempDate
	def ReqDate = TempDate[0]+"-"+TempDate[1]
	print"Required Patch Month :"+ReqDate
	
       // Get computer ID from Computer Name          
		for(String host in hostnames)
			{
				remoteOpreation.CheckRestartRequired(host) // To check Machines, whether it requires restart after Windows Update
				def PatchKBSource = []
				def response = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', ignoreSslErrors: true, quiet: true, responseHandle: 'NONE', url: "https://bigfixdev.products.network.internal:9081/api/patch/computers?token="+IBMBigFixToken                     
          			def json = new JsonSlurper().parseText(response.content)["rows"] 
				json.each
          			{
					if(it["name"] == host) 
					{ 
						def computerID = it["id"] 
						def patchResponse = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', ignoreSslErrors: true, quiet: true, responseHandle: 'NONE', url: 'https://bigfixdev.products.network.internal:9081/api/patch/computers/' + computerID + '/patch_results?token='+IBMBigFixToken
						def patchResultJson = new JsonSlurper().parseText(patchResponse.content)["rows"]      
						if(patchResultJson.resource.size() > 0)
						{	
							for (int i =0; i< patchResultJson.resource.size(); i++)
							{
								def patchSeverity = patchResultJson[i]["patch"]["severity"]
								def patchCategory = patchResultJson[i]["patch"]["category"]
								def releasedDate = patchResultJson[i]["patch"]["source_release_date"]       //Filter current Month patches only
								def superseded = patchResultJson[i]["patch"]["superseded"]
								//print "relaesed Date : "+releasedDate
								if(patchSeverity == "Critical" && patchCategory == "Security Update" && releasedDate.contains(ReqDate) && superseded.toString() == "false")
								{
									PatchSource = patchResultJson[i]["patch"]["source_id"]
									PatchKBSource.add(PatchSource)
								}
							}
							if(!PatchKBSource.isEmpty())
							{
								print "Required Patch Update KBSource is : "+PatchKBSource
								node(host)
								{
									def afterSplit = []
									def returnCode = bat(encoding: 'UTF-8', label: 'getfixid', returnStdout: true, script: '@wmic qfe get HotfixID').trim()
									returnCode = returnCode.replaceFirst("^([\\W]+)<","<")       //To remove unwanted special char in first line of the content
									def returnCode1 = returnCode.split('\n')								
									for(int j = 0; j< returnCode1.size(); j++)
									{
										def tempvalue = returnCode1[j].trim()									
										afterSplit.add("${tempvalue}".toString())
									}
									print "Available Kb Update is "+afterSplit
								    for(int i=0; i< PatchKBSource.size(); i++){										
										if(afterSplit.stream().any {it.replaceAll("[^\\d.]", "").equals(PatchKBSource[i].replaceAll("[^\\d.]", ""))})
										{
											println "HotFixID - ${PatchKBSource[i]} is updated in ${host}"
										}
										else {
											echo" Error!! HotFixID - ${PatchKBSource[i]} is not updated in ${host}"
											isPatchUpdated = false
											print "Build Marked as Failed due to ${PatchKBSource[i]} is not updated in ${host} "
											currentBuild.result = 'Failure'
											}
									}
								}
							}
							else
							{
								isPatchUpdated = false
								print "Build Marked as Failed due to no patch update avilable for this Month on your Computer ${host} Computer ID " +  computerID
								currentBuild.result = 'Failure'
							}
						}
						else	
							{
								isPatchUpdated = false
								println "No Patch update for Computer ID " +  computerID
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
