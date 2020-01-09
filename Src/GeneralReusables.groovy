package Utils

import jenkins.*
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;	
import groovy.json.JsonSlurperClassic
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.XmlParser
import groovy.lang.GroovyObjectSupport
import groovy.util.slurpersupport.GPathResult
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.TimeZone
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import java.lang.Object
import java.time.format.DateTimeFormatter
import java.nio.file.FileSystems
import java.lang.Integer
import java.net.InetAddress;
import java.net.UnknownHostException;
import hudson.model.Computer.ListPossibleNames
import java.util.Map
import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.*  
import hudson.slaves.*
import jenkins.model.Jenkins
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import hudson.FilePath
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * Available Methods
 * convertEpochtoReadable - 
 * getTodaysDate - 
 * getValueFromXML - 
 * getXmlValue
 * getXmlHostValues
 * getIpAddressOfNode
 * isXmlNodeExists
 */

/*
 * Converting epoch/Unix timestamp to human readable and the output format is dd/MM/yyyy
 */
def convertEpochtoReadable (timestamp) {
	Date buildTS = new Date(timestamp)
	DateFormat TSformat = new SimpleDateFormat("dd/MM/yyyy")
	def Date = TSformat.format(buildTS).toString();
 return Date
}

/*
 * Collecting Today's date and convert into the format dd/MM/yyyy
 */
def getTodaysDate () {
	LocalDate today = LocalDate.now();
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	def todayDate = today.format(formatter).toString();
	echo "Today's Date is: ${todayDate}"
  return todayDate
}
@NonCPS
def ReadXml()
{
	def XmlFileLoc= "${MasterWorkspace}" +"//Xml_Configuration.xml"
	def readFileContents = "";
	if (! env.MasterNode.equals("master")) {			      
			channel = Jenkins.getInstance().getComputer(env.MasterNode).getChannel() 
			Contents = new FilePath(channel, "${XmlFileLoc}").read()
			readFileContents = Contents.getText("UTF-8") 
			//readFileContents = readFileContents.trim().replaceFirst("^([\\W]+)<","<");
		}
		else{
			readFileContents = new File("${XmlFileLoc}").getText()
		} 	
	return readFileContents
}

def ReadFile(nodeName, fileLoc)
{
	def readFileContents = "";
	if (! nodeName.equals("master")) {			      
			channel = Jenkins.getInstance().getComputer(nodeName).getChannel() 
			readFileContents = new FilePath(channel, "${fileLoc}").readToString()
			//def Contents = readFileContents.getText("UTF-8") 
			readFileContents = readFileContents.trim().replaceFirst("^([\\W]+)<","<");
		}
		else{
			readFileContents = new File("${fileLoc}").getText()
		} 	
	return readFileContents
}

/*
 * Finding the value for Key Paired using the node. 
 */
def getValueFromXML(nodeValue, keyValue) {
	def value = null;
 try
 {
	readFileContents = ReadXml()
	def config = new XmlSlurper().parseText(readFileContents)
	value = Matcher.quoteReplacement(config."${nodeValue}".'**'.find { add -> add['@key'] == keyValue}['@value'].toString())
	if(keyValue == "Password")
	{
	 if(value == "")
		println "Password Empty"
	 else
		value = Matcher.quoteReplacement(decryptingPasswords("${EncryptionKey}", "${value}").trim())
	}
 }
 catch(Exception e)
 {
	echo "Node ${nodeValue} and key value ${keyValue} is not in input Xml File: " + e
 }
 return value
}

/*
 * To get the array of values from XML based on node Head and operation's key value
 */
/*def getXmlValue(nodeHead,hostName,nodeKey,labelName)
{
	def NodeNames = GetNodeNames(labelName)
	readFileContents = ReadXml()
	def config = new XmlSlurper().parseText(readFileContents)
	def returnValue= []
	if(NodeNames.contains(hostName)){
	config."${nodeHead}".'**'.find { node -> 
		  if(node['@label'] == labelName){node.Operation.each {node1 -> returnValue.push(node1["@${nodeKey}"].toString())}}}
		//print "Return value label :"+returnValue
		}
	if(returnValue[0] != null || returnValue[0] == null )
	{
	config."${nodeHead}".'**'.find { node -> 
		if(node['@host'] == hostName){node.Operation.each {node1 -> returnValue.push(node1["@${nodeKey}"].toString())}}}
	}
	returnValue = returnValue.minus("");
	//print "Return value Host :"+returnValue
 return returnValue
}*/
def getXmlValue(nodeHead,hostName,nodeKey,labelName)
{
	
	def NodeNames = GetNodeNames(labelName)
	readFileContents = ReadXml()
	def config = new XmlSlurper().parseText(readFileContents)
	def returnValue= []
	if(NodeNames.contains(hostName)){
	config."${nodeHead}".'**'.find { node -> 
		  if(node['@label'] == labelName){node.Operation.each {node1 -> returnValue.push(node1["@${nodeKey}"].toString())}}}
		//print "Return value label :"+returnValue
		}
	if(returnValue[0] != null || returnValue[0] == null )
	{
	config."${nodeHead}".'**'.find { node -> 
		if(node['@host'] == hostName){node.Operation.each {node1 -> returnValue.push(node1["@${nodeKey}"].toString())}}}
	}
	returnValue = returnValue.minus("");
	//echo " returnvalues : " + returnValue
 return returnValue
}
/*
 * To get the 'Machine' node values from XML based on node Head and Machine's key value
 */
def getXmlHostValues(nodeHead,hostName,nodeKey,labelName)
{
    def NodeNames = GetNodeNames(labelName) 
	readFileContents = ReadXml()
	def config = new XmlSlurper().parseText(readFileContents)
	def returnValue = []
	if(NodeNames.contains(hostName)){
	config."${nodeHead}".'**'.find { node -> 
		if(node.@'label' == labelName){returnValue = node.@"${nodeKey}"}}  
		}
	if(returnValue[0] != null || returnValue[0] == null )
	{
	config."${nodeHead}".'**'.find { node -> 
		if(node.@'host' == hostName){returnValue = node.@"${nodeKey}"}}
	}
 return returnValue
 
}
def getXmlHostValues_labels(nodeHead,hostName,nodeKey)
{
	readFileContents =ReadXml()
	def config = new XmlSlurper().parseText(readFileContents)
	def returnValue = null
	config."${nodeHead}".'**'.find { node -> 
		if(node.@'label' == hostName){returnValue = node.@"${nodeKey}"}}
 return returnValue
 
 
}

/*
 * get a ip address of the jenkins node
 */
@NonCPS
def getIpAddressOfNode(NodeName)
 {
	def NodeIpAddress = null;
 	def node = jenkins.model.Jenkins.instance.getNode(NodeName)
	NodeIpAddress = node.computer.getChannel().call(new ListPossibleNames())
	def ReplaceString = NodeIpAddress.toString().replaceAll("\\[", "").replaceAll("\\]","");
  return ReplaceString;
 }
 
/*
 * To check particular node availability in xml file
 */
def isXmlNodeExists(xmlNodeName)
{
	readFileContents = ReadXml()
	def config = new XmlSlurper().parseText(readFileContents)
	boolean result = config."${xmlNodeName}".isEmpty()
	return result
}

/*
 * Checks whether the node is off-line or on-line
 */
def checkNodeStatus(nodeName)
{
 boolean result = false
 try{
		result =  Jenkins.instance.getNode(nodeName.toString()).toComputer().isOnline()
    }
	catch(Exception e)
	{
		echo "Node ${nodeName} is not added in Master Jenkins: " +e
	}
	return result
}

/*
 *This method collects a list of Node names from the current Jenkins instance
 */
def GetNodeNames(label) {
  def nodes = []
  jenkins.model.Jenkins.instance.computers.each { c ->
    if (c.node.labelString.equals(label)) {
      nodes.add(c.node.selfLabel.name)
      //print "Node found under ${label}- ${c.node.selfLabel.name}"
    }
  }
  return nodes
}

//@NonCPS
decryptingPasswords(text)
{
  String Key = credentials('EncryptionAESKey')
  //String Key = "devopsIBM97531qa"
  println "KeyDecry:" +Key
  // Create key and cipher
  String initVector = "QMPRWESJUYERJF=!"
  SecretKeySpec aesKey = new SecretKeySpec(Key.getBytes("UTF-8"), "AES");
  IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
  Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
  
  // Decrypt the text 
  byte[] sdata = Base64.getDecoder().decode(text)
  cipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
  String decrypted = new String(cipher.doFinal(sdata));
  println decrypted
  return decrypted	
}

@NonCPS
def getNodeStatus(hostnames, status)
{
	List<String> NodeList = new ArrayList<>()
	def jenkinsNodes = Jenkins.instance.nodes			
	for (Node node in jenkinsNodes) 
	{
          if (hostnames.contains(node.nodeName)) {	
	    if (status == "online") {				
		  if (node.getComputer().isOnline()) {						

			NodeList.add(node.nodeName)			
		}
	    }
	    else if (status == "offline") {
	      if (node.getComputer().isOffline()) {
		NodeList.add(node.nodeName)	
	      }
	    }
	  }
	}
	return NodeList
}

def isAllHostExcluded(hostnameList, excludedHostList) {
    def pendingHost = [];
    for (host in hostnameList) {
        if(! excludedHostList.contains(host)) {
             pendingHost.add (host)
         }		
      }
      if (pendingHost.isEmpty()) { return true}
      else {return false }        
}

def relaunchNode(hostnames) {
	def jenkinsNodes = Jenkins.instance.nodes
	for (Node node in jenkinsNodes) {
		if (hostnames.contains(node.nodeName)) {
			if (node.getComputer().isOffline()) {
				node.computer.connect(true)
				sleep(30)
			}
		}
	}
}

def decryptingPasswords(Key, text)
{	
  String initVector = "QMPRWESJUYERJF=!"	
  SecretKeySpec aesKey = new SecretKeySpec(Key.getBytes("UTF-8"), "AES");
  IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));

  Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
  // encrypt the text with Symmetric encryption
  /*	
  cipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
  byte[] encrypted = cipher.doFinal(text.getBytes());
  println encrypted 
  String ecnstr = Base64.getEncoder().encodeToString(encrypted);	
  println ecnstr 
*/
  // Decrypt the text 
 byte[] sdata = Base64.getDecoder().decode(text)
 cipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
 String decrypted = new String(cipher.doFinal(sdata));
 return decrypted	
}
/*
 * To get the available smartbear executor(TestExecute/TestComplete) path
 */
 
def FindTestExecuteExePath()
{	def TestExePath = null
	def basePath = "C:\\Program Files (x86)\\SmartBear" 
	nodeChannel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel()
	nodePath = new FilePath(nodeChannel, basePath)				
	if(nodePath.exists())
		{		
		List<FilePath> dirList = nodePath.listDirectories()
			for (list in dirList) 
			{
				if(list.getBaseName().startsWith("TestExecute"))
				{
					TestExePath = basePath+"\\"+list.getBaseName()+"\\Bin\\TestExecute.exe"
					//print "TestExePath :"+TestExePath
				}
			}
			if(TestExePath == null)
			{
				print "TestExecute exe Not Found in Base folder ${basePath}"
				for (list in dirList) 
					{
					if(list.getBaseName().startsWith("TestComplete"))
					{
						TestExePath = basePath+"\\"+list.getBaseName()+"\\Bin\\TestComplete.exe"
						print "TestComplete exe selected as Executor"
					}
				}
			}
			if(TestExePath == null)
			{
				print "TestExecute And TestComplete exe Not Found in Base folder ${basePath}"	
			}
		}
		else{
			print "C:\\Program Files (x86)\\SmartBear Folder is not exists"
		}
	return TestExePath
}

/*
Create a backup for Test Results folder and deletes the contents of the folder
*/
def CreateBk_ResultsFolder(host)
{
	boolean status = false
	def ReportPath = getXmlValue("ClientSetup",host,"TestReportPath","") 
	print"ReportPath from XML: "+ReportPath
	nodeChannel = Jenkins.getInstance().getComputer(host).getChannel()				
	 //Create a Backup for old Test Report 
	def TempPath = ReportPath[0].toString().split("\\\\");
	TempPath = TempPath[TempPath.length-1]
	def ReportTempPath = ReportPath[0].toString().minus(TempPath.toString())
	ReportBkPath = ReportTempPath+"\\TestResultsBackup"
	print"ReportBkPath:"+ReportBkPath
	reportBackupPath = new FilePath(nodeChannel, "${ReportBkPath}")
		if(!reportBackupPath.exists())
		{
			reportBackupPath.mkdirs()
		}
		reportPath = new FilePath(nodeChannel, "${ReportPath[0].toString().trim()}")
					
		//Move old test reports from TestReports path to TestResults_Backup folder
		int copyStatus = reportPath.copyRecursiveTo(reportBackupPath)
		print "Report copyStatus :"+copyStatus
		if(copyStatus != 0)
		{
			status = true
			reportPath.deleteContents()
			print"Report files are deleted"
		}
		else
		{
			println "Old TestReports not copied to TestResultsBackup"
		}

return status
}

def getCurrentMonth()
{
  Calendar cal = Calendar.getInstance();
  def monthName = new SimpleDateFormat("MMM").format(cal.getTime())
  int year = Calendar.getInstance().get(Calendar.YEAR);
  //print "monthName :"+monthName
  def CurentMonthAndYear = monthName +" "+year
  return CurentMonthAndYear
}
