import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.GPathResult
import groovy.util.XmlParser
import groovy.json.JsonSlurperClassic
import groovy.json.JsonSlurper
import groovy.lang.GroovyObjectSupport
import java.util.Map
import jenkins.*
import hudson.* 
import java.lang.Object
import groovy.time.*
import jenkins.model.*
import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.*;
import groovy.stream.*

def call()
{

def problematichosts = []
	
pipeline  {
  agent any
	 environment {
		 def workspace = "${env.MasterWorkSpace}"
		 def jenURL = "${env.JENKINS_URL}"
	 }
     stages {
         stage('AddSlaveNodes') {
	    steps {
		script {

		      echo ">> This stage will add all the nodes in Jenkins from the generated XML and confirm the nodes running status"		   		
		      
			  // Collecting hostnames from the configuration XML file. 	
	          def hostnames = []
		      def parallelExec = [:]
	          def readFileContents = new File("${env.xmlPath}").getText()
              def config = new XmlSlurper().parseText(readFileContents)
		      config.MachineDetails.Machine.each {node -> hostnames.push(node['@host'].toString())}
              println ">> Available Machines are: "+hostnames.toString()
		    
		      // Deleting the existing secretID file for the added nodes if exist.
		      def dataToBeWritten = ""
		      def secretIDfile = new File("${workspace}/src/ansibleActions/playbooks/vars/secretID.yml")		    
     	      secretIDfile.delete()
			  
		     //Copying inventory file to Workspace and take the copied path for further actions. 
		     def invtTempPath = env.ymlPath.substring(0,env.ymlPath.lastIndexOf(File.separator));
	         dir(invtTempPath) {
                fileOperations([fileCopyOperation(excludes: '', flattenFiles: true, includes: '*.yml', targetLocation: "${WORKSPACE}/src/ansibleActions/inventory")])
             }
		     echo ">> Inventory file is copied to Workspace"	    
		     def invTempFile = new File(env.ymlPath)	    
		     def invFileName = invTempFile.getName()
		     def invFilePath = "${WORKSPACE}/src/ansibleActions/inventory/${invFileName}"	      
		     
			 // Adding nodes one by one based on configuration file. 	
		     for(String host in hostnames) {
			  def workingRepo = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@wrkingRepo'].toString() 
			  def label = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@label'].toString()
			
              		DumbSlave dumb = new DumbSlave(host,  // Agent name, usually matches the host computer's machine name
              		host + "_" + "desc",           // Agent description
              		workingRepo,         // Workspace on the agent's computer
              		"2",                        // Number of executors
              		Mode.EXCLUSIVE,             // "Usage" field, EXCLUSIVE is "only tied to node", NORMAL is "any"
              		label,                      // Labels
              		new JNLPLauncher(),         // Launch strategy, JNLP is the Java Web Start setting services use
              		RetentionStrategy.INSTANCE) // Is the "Availability" field and INSTANCE means "Always"

              		Jenkins.instance.addNode(dumb)
              		println ">> Agent '$host' created with home directory as '$workingRepo'"
         	        String secretID = jenkins.model.Jenkins.getInstance().getComputer(host).getJnlpMac()

			        if(dataToBeWritten == "") {  
				      dataToBeWritten = host + "_secret: " + secretID
			        }
			        else {
				      dataToBeWritten = dataToBeWritten + "\n" + host + "_secret: " + secretID		
                    } 
		      }
			
	         //SecretID for each node is written on secretid.yml file. 	
		     writingsecretIDinfile(dataToBeWritten)     
                    
		     // Checking the given slave running Status before connecting from the Master
	         echo ">> Checking the given slave running Status before connecting from the Master"  
		    for (host in hostnames) {
			   def nodeName = host.toString();
			   def vcenter = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@vcenter'].toString() 			
			   parallelExec [nodeName] = {      
			     try { 
				   //if(OfflineNodeList.stream().any {it.equalsIgnoreCase(host)})
				   def hostIP = vSphere buildStep: [$class: 'ExposeGuestInfo', envVariablePrefix: 'VSPHERE', vm: nodeName.trim(), waitForIp4: true], serverName: vcenter.trim()
				   echo ">> ${nodeName} - ${hostIP} are in running state."
				   // Collecting the IP address for each nodes and replace it on Inventory files.  
				   includingIPaddress(invFilePath, nodeName, hostIP)
		          }
		        catch(Exception ex){
				 // Add all the problematic nodes in array for further display.    
				 echo '## Exception reason: ' + ex
				 if (ex.getMessage() != null) {   
                    if (ex.getMessage().contains("${nodeName} not found") || ex.getMessage().contains("Could not find vSphere Cloud")) {
				        problematichosts.push(nodeName)
				    }
				 }
				 else {
				   // Power on the Nodes when its down. 	
                   def IP = VMPowerON(vcenter, nodeName)
				   // Collecting the IP address for each nodes and replace the same on Inventory files.
				   includingIPaddress(invFilePath, nodeName, IP)	
				    }
			    }   
			  }
		    }
		      parallel parallelExec
		      if ( problematichosts.size() > 0) {
			     echo "*****************************************************************************"    
			     echo "The following nodes are problematic. Unable to use those nodes. Do manual configuration or correct the given values"		  
			     println problematichosts.toString()
			     echo "The reason may be \n 1. The given host name is not valid. \n 2. The mapped VM center to the host is not correct one. \n 3. The given credential for the VM center is not correct one."        
			     echo "*****************************************************************************"    
		      }	
		 
		      echo ">> This stage is added all the given nodes in jenkins from the generated XML file and confirmed the Slave running status"			
              }
	        } 
         }
  
  stage('SlavePreSetup')  {
	  environment {
            Vspherecre = credentials('VSPHERE')
		    EncryptionKey = credentials('EncryptionAESKey')

       }
	  steps {
		  script {
		      echo ">> This stage will make all the nodes to Online for Jenkins Communication"
			  //Variable Initialize
			  def linuxPlaybook = "${workspace}/src/ansibleActions/playbooks/slaveconfig-lnx.yml"
			  def wdwPlaybook = "${workspace}/src/ansibleActions/playbooks/slaveconfig-wnd.yml"
			  def agentFilePath = "${workspace}/src/ansibleActions/support/agent.jar"
			  def linuxJavaPath = "${workspace}/src/ansibleActions/support/jdk11_L.tar.gz"
			  def windowsJavaPath = "${workspace}/src/ansibleActions/support/jdk11_W.zip"
			  def invTempFile = new File(env.ymlPath)	    
		      def invFileName = invTempFile.getName()
		      def invFilePath = "${WORKSPACE}/src/ansibleActions/inventory/${invFileName}"
			  
			  // Collecting all Online nodes and Problematic hosts to exclude from Ansible Slave Pre-setup
			  List<String> excludeNodeList = getNodeStatus("online")
			  excludeNodeList.addAll(problematichosts)
			  def excludeHosts = excludeNodeList.toString().replace("[","").replace("]", "").replace(",", ":!").replace(" ", "")
			  echo ">> Exclude Hosts from Ansible Playbook are ${excludeHosts}"
			  
			  // Setting up linux OS connections between Master and Slave. 
			  echo ">> Setting up linux OS connections between Master and Slave."
			  decryptingPasswordsinInventory("${invFilePath}", "${EncryptionKey}")
			  // Vault the Inventory file to secure sensitive data's
			  ansibleVault action: 'encrypt', input: "${invFilePath}", installation: 'ansible_2.8.0', vaultCredentialsId: 'AnsibleVaultPassword'
			  //Trigger Playbook for linux OS setup
			  try {				  
			     ansiblePlaybook installation: 'ansible_2.8.0', limit: "all:!${excludeHosts}", disableHostKeyChecking: true, inventory: "${invFilePath}", playbook: "${linuxPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars  'jenkinsURLPath=${jenURL} agentFilePath=${agentFilePath} linuxJavaPath=${linuxJavaPath}'"
			  }
			  catch (Exception ex){
			      echo '## Linux Playbook is failed because of ' + ex 
			  }
			  
			  // Enabling/Upgrading Powershell, .netframework and Winrm settings in Windows Machine for Ansible actions. 
			  def hostnames = []
			  def guestOPScript = "${workspace}/src/Support/guestOpsManagement.pl"
			  def WinrmWrkDir = "C:\\\\Winrm"			  
	          def readFileContents = new File("${env.xmlPath}").getText()
              def config = new XmlSlurper().parseText(readFileContents)
			  config.MachineDetails.Machine.each { node -> if(node['@os'] == "Windows"){hostnames.push(node['@host'].toString())}}
	          println ">> Windows Machines are: "+hostnames.toString()
			  for (host in hostnames) {
		             if (!excludeNodeList.contains(host))
			     {
				  def guestPwd = Matcher.quoteReplacement(config.MachineDetails.'**'.find { node -> node['@host'] == host}['@password'].toString())
				  guestPwd = Matcher.quoteReplacement(decryptingPasswords("${EncryptionKey}", "${guestPwd}").trim()) 	 			  
				  def guestuser = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@userName'].toString()				  
				  def vcenter = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@vcenter'].toString()
				  def domain = "${host}\\\\${guestuser}"
				     
				  makeDirectoryonAgent(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir) 
				  makeDirectoryonAgent(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "C:\\\\Windows\\\\SysWOW64\\\\config\\\\systemprofile\\\\Desktop")    
				     
				  CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${workspace}/src/Support/Upgrade-PowerShell.ps1", "${WinrmWrkDir}\\\\Upgrade-PowerShell.ps1")
				  CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${workspace}/src/Support/Install-WMF3Hotfix.ps1", "${WinrmWrkDir}\\\\Install-WMF3Hotfix.ps1")   
				  CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${workspace}/src//Support/winrm_enable.ps1", "${WinrmWrkDir}\\\\winrm_enable.ps1")   
	              CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${workspace}/src/Support/WinrmEnable.bat", "${WinrmWrkDir}\\\\WinrmEnable.bat")    			    

				  StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, "Upgrade-PowerShell.ps1")
  				  StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, "Install-WMF3Hotfix.ps1")   
				  StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, "winrm_enable.ps1")      
				  
				  def hostIP = getHostIP(vcenter, host)   
				  
				  StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, "Upgrade-PowerShell.ps1")
  				  StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, "Install-WMF3Hotfix.ps1")   
				  StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, "winrm_enable.ps1")      
				
				  }
			  }
                          
			  try {
		          // Setting up Windows OS connections between Master and Slave.
                  ansiblePlaybook installation: 'ansible_2.8.0', limit: "all:!${excludeHosts}", disableHostKeyChecking: true, inventory: "${invFilePath}", playbook: "${wdwPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars  'jenkinsURLPath=${jenURL} agentFilePath=${agentFilePath} windowsJavaPath=${windowsJavaPath}'"
			  }
			  catch (Exception ex){
			      echo '## Windows Playbook is failed because of ' + ex 
			  }
              echo ">> Pre-requisites done in slaves for execution."
			  sleep(300)
			  List<String> OfflineNodeList = getNodeStatus("offline")
			  echo "Following agents are offline and do the needful"			 
  	          echo "**************************************************************************************************"
  	          echo "${OfflineNodeList}"				 
  	          echo "**************************************************************************************************"
          }
       } 
     }	     
   } 
}
}
@NonCPS
def writingsecretIDinfile(dataToBeWritten)
{
	new File("${workspace}/src/ansibleActions/playbooks/vars/",'secretID.yml').withWriter('utf-8'){ 
	writer -> writer.writeLine dataToBeWritten
	echo "Secret ID's are written to secretID.yml file"	
        }  
}

@NonCPS
def VMPowerON(vcenter, hostname)
{
	try {
		echo "Attempting to power ON the machine ${hostname}"	
		def IP = vSphere buildStep: [$class: 'PowerOn', timeoutInSeconds: 400, vm: hostname.trim()], serverName: vcenter.trim()
	}
	catch(Exception ex) {
	        echo 'Exception reason: ' + ex
                problematichosts.push(hostname)
	}
}

@NonCPS
def getHostIP(vcenter, hostname)
{
	try {
	    def hostIP = vSphere buildStep: [$class: 'ExposeGuestInfo', envVariablePrefix: 'VSPHERE', vm: hostname.trim(), waitForIp4: true], serverName: vcenter.trim()
	    retutrn hostIP	
	}
	catch(Exception ex) {
	        echo '## Exception reason: ' + ex
		return null
	}
}

@NonCPS
def decryptingPasswordsinInventory(invtFile, Key) 
{
File ifile = new File(invtFile)
  def fileContent = "";	
  BufferedReader reader = new BufferedReader(new FileReader(ifile))	
  def line = reader.readLine();	
  while (line != null) {
	  if (line.contains ("ansible_password: ")) {
	         def pwds = line.minus("ansible_password: ").trim()
		 string decrypted = decryptingPasswords(Key, pwds) 
		 line = line.replace(pwds, decrypted)		  
	  }
	  if (line.contains ("ansible_become_password: ")) {
	         def pwds = line.minus("ansible_become_password: ").trim()
		 string decrypted = decryptingPasswords(Key, pwds) 
		 line = line.replace(pwds, decrypted)		  
	  }
	  fileContent = fileContent + line + System.lineSeparator();	 
	  line = reader.readLine();				
  }
	FileWriter writer = new FileWriter(invtFile);
	writer.write(fileContent);
	
	reader.close();
    writer.close();
}

@NonCPS
decryptingPasswords(Key, text)
{
  // Create key and cipher
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

@NonCPS
def includingIPaddress(invtFile, hostname, ipaddr)
{
 try {
  File ifile = new File(invtFile)
  def fileContent = "";	
  BufferedReader reader = new BufferedReader(new FileReader(ifile))	
  def line = reader.readLine();	
  while (line != null) {
	  if (line.contains ("ansible_host: ${hostname}")) {
	         def host = line.minus("ansible_host: ").trim()
		  echo "IPaddress for ${host}: ${ipaddr}"  

		 line = line.replace(host, ipaddr)		  
	  }
	  fileContent = fileContent + line + System.lineSeparator();	 
	  line = reader.readLine();				
  }
	FileWriter writer = new FileWriter(invtFile);
	writer.write(fileContent);
	
	reader.close();
    writer.close();
    echo ">> IP addresses are added to all hosts in Inventory file" 	
 }
 catch(Exception ex) {
   echo 'Exception reason: ' + ex
 }
}

@NonCPS
def makeDirectoryonAgent(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, directory)
{      
   def mkdir = sh label: 'mkdir', returnStatus: true, script: """#!/bin/bash +x
   perl ${guestOPScript} -server ${vcenter} -username ${Vspherecre_USR} -password ${Vspherecre_PSW} -vm ${host} -operation mkdir -guestusername ${domain} -guestpassword ${guestPwd} -filepath_src ${directory}"""
   if (mkdir != 0) {
      echo "Credentials might be invalid or Folder already exists in the Target machine"
    }	
}


@NonCPS
def CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, srcPath, destPath)
{	
   def cpyFiles = sh label: 'cpyFiles', returnStatus: true, script: """#!/bin/bash +x
   perl ${guestOPScript} --server ${vcenter} --username ${Vspherecre_USR} --password ${Vspherecre_PSW} --vm ${host} --operation copytoguest --guestusername ${domain} --guestpassword ${guestPwd} --filepath_src ${srcPath} --filepath_dst ${destPath}"""	
   if (cpyFiles != 0) {	   
      echo "Credentials might be invalid or File already exists in the Target machine"
   }	
}

@NonCPS
def StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, script)
{
   def strtprgthruVghetto = sh label: 'strtprgthruVghetto', returnStatus: true, script: """#!/bin/bash +x
   perl ${guestOPScript} --server ${vcenter} --username ${Vspherecre_USR} --password ${Vspherecre_PSW} --vm ${host} --operation startprog --guestusername ${domain} --guestpassword ${guestPwd} --working_dir ${WinrmWrkDir} --program_path '${WinrmWrkDir}\\WinrmEnable.bat' --program_args '${script}'"""
   if (strtprgthruVghetto != 0) {
     echo "## Credentials might be invalid or File already exists in the Target machine"
   }		   
}

@NonCPS
def StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, script)
{
   def strtprgthruWinexe = sh label: 'strtprgthruWinexe', returnStatus: true, script: """#!/bin/bash +x
   winexe -U ${hostIP}/\'${guestuser}'%${guestPwd} //${hostIP} 'PowerShell.exe ${WinrmWrkDir}\\${script}'"""		
   if (strtprgthruWinexe != 0) {
     echo "## Credentials might be invalid or File already exists in the Target machine"
   }		
}


@NonCPS
def getNodeStatus(status)
{
	List<String> NodeList = new ArrayList<>()
	def jenkinsNodes = Jenkins.instance.nodes			
	for (Node node in jenkinsNodes) 
	{  
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
	echo ">> The following Nodes are currently ${status}"
	echo "${NodeList}"
	return NodeList
}
