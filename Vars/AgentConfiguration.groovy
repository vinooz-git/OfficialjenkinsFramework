import jenkins.*
import hudson.*
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.GPathResult
import groovy.util.XmlParser
import hudson.FilePath
import jenkins.model.*
import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.cloudbees.plugins.credentials.impl.*;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*;
import hudson.plugins.sshslaves.verifiers.*
import com.cloudbees.jenkins.plugins.sshslaves.verification.*
import hudson.model.*
import hudson.plugins.sshslaves.*
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.model.Node.Mode

def call () {

	//vSphere buildStep: [$class: 'ExposeGuestInfo', envVariablePrefix: 'VSPHERE', vm: "PA-TST4-W10", waitForIp4: true], serverName: 'neptune.products.network.internal'
    echo ">> This stage will add all the nodes in Jenkins from the generated XML and confirm the nodes running status"
    def continuePipeline = true
    def hostnames = []
    def windowHostnames = []
    def parallelExec = [:]
    def dataToBeWritten = ""
    def problematichosts = []
    def unreachableAgentInfo = "The following nodes are problematic. Please recheck the given values"
    // If the agent is not Master, then the node file path sets through Channel
    channel= getChannel(env['NODE_NAME'])
    def ConfigXML = "${MasterWorkspace}" +"//Xml_Configuration.xml"
    def ymlMain = "${MasterWorkspace}"
    def InventYML = "${MasterWorkspace}" +"//src//ConfigFiles"
   // def InventYML = "${MasterWorkspace}"
    InventoryYmlMain = new FilePath(channel, "${ymlMain}")
    DestPath = new FilePath(channel, "${InventYML}")
    InventoryYmlMain.copyRecursiveTo("Inventoryfile.yml",DestPath)
   InventYML = "${MasterWorkspace}" +"//src//ConfigFiles//Inventoryfile.yml"
 //   InventYML = "${MasterWorkspace}" +"//Inventoryfile.yml"
    readFileContents = new FilePath(channel, "${ConfigXML}").readToString()
    readFileContents = readFileContents.trim().replaceFirst("^([\\W]+)<","<");	    	
    InventoryFile = new FilePath(channel, "${InventYML}")
    def common = new Utils.GeneralReusables()
    def vmOperations = new Utils.VmOperationsUtils()
    def file = new Utils.FileOperations()

    // Collecting hostnames from the configuration XML file.	    
    def config = new XmlSlurper().parseText(readFileContents)
    config.MachineDetails.Machine.each {node -> hostnames.push(node['@host'].toString())}
    println ">> Available Machines are: "+hostnames.toString()
	
    // replacing the Encrypted password in Inventory file for Ansible. 	
    try {
       file.replaceContentinfile(InventoryFile, "ansible_password: ", "ansible_password: ", "DECRYPTPWD", "${EncryptionKey}")
       file.replaceContentinfile(InventoryFile, "ansible_become_password: ", "ansible_become_password: ", "DECRYPTPWD", "${EncryptionKey}")
    }
    catch(Exception ex){
	  echo '## Exception reason: ' + ex  
    }	

                      
    // Deleting the secret ID files if exist When the launcher is Java web start.
    if (nodeLaunchMethod == "viaJavaWebStart") {
	secretIDFile = new FilePath(channel, "${secretIDPath}")
	secretIDFile.delete()
     }
	
     // Exclude the existing Online nodes based on XML hosts. 
     List<String> excludeNodeList = common.getNodeStatus(hostnames, "online")		
	
     // Adding nodes one by one based on configuration file after confirming the node status.
     echo ">> Confirm the given slave running Status before connecting from the Master"	
     for(String host in hostnames) {	     
	 def vcenter = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@vcenter'].toString()
          
	 // Confirm all the given nodes are valid and accessable from VSPhere.
	 // Also writing the Host IP's in Inventory File.
	 def hostIP=null;
	 if (vcenter == "Physical Machine") {
	    // This line collects entered Physical Machine IP details. 	 
            hostIP = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@hostIP'].toString()		     
	 }    
	 else {
	    try {				 						   			   
		 hostIP = vSphere buildStep: [$class: 'ExposeGuestInfo', envVariablePrefix: 'VSPHERE', vm: host, waitForIp4: true], serverName: vcenter			 						   			   
		 echo ">> ${host} - ${hostIP} are in running state."
		 // Collecting the IP address for each nodes and replace it on Inventory files. 		    	 
		 file.replaceContentinfile(InventoryFile, "ansible_host: ${host.trim()}", "ansible_host: ", hostIP)
	    }
            catch(Exception ex){
	         // Add all the problematic nodes in array for further display.    
                 echo '## Exception reason: ' + ex
	         if (ex.getMessage() != null) {   
                   if (ex.getMessage().contains("${host} not found") || ex.getMessage().contains("Could not find vSphere Cloud")) {
		      problematichosts.push(host)
		      unreachableAgentInfo = unreachableAgentInfo+ "\n" + host +": "+ex.getMessage()
		   }
	        }
	        else {
		    // Power on the Nodes when its down. 	
                    hostIP = vmOperations.VmPowerOn(vcenter, host)
		    if (hostIP != null) {  
		       // Collecting the IP address for each nodes and replace the same on Inventory files.
		       file.replaceContentinfile(InventoryFile, "ansible_host: ${host}", "ansible_host: ", hostIP)	
		    }
	        }
	     }
	 }	 
                 
	 if (!excludeNodeList.contains(host)) {	
	     def workingRepo = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@wrkingRepo'].toString() 
	     def label = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@label'].toString()
	     def machinePwd = Matcher.quoteReplacement(config.MachineDetails.'**'.find { node -> node['@host'] == host}['@password'].toString())
	     machinePwd = Matcher.quoteReplacement(common.decryptingPasswords("${EncryptionKey}", "${machinePwd}").trim())
	     def rawPwd = common.decryptingPasswords("${EncryptionKey}", config.MachineDetails.'**'.find { node -> node['@host'] == host}['@password'].toString().trim())	 
             		 
	     def machineUser = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@userName'].toString()
	     def machineOS = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@os'].toString()   
	     def workingDir =null
	     if (hostIP == null || hostIP == "false") { hostIP = host }
	     if (machineOS == "Windows") {
		   def driveName   = workingRepo.split(':')
		   workingDir = driveName[0] +": && "
		   javaPath = "${workingRepo}\\jdk11\\jdk11_W\\bin\\java" 
     		     agentCommand = "sshpass -p '${rawPwd}' ssh -o StrictHostKeyChecking=no ${machineUser}@${hostIP} \"${javaPath} -Xmx1024m -jar ${workingRepo}//agent.jar -text\""		
		     
	     }
	     else {
		   workingDir=null
		   javaPath = "${workingRepo}/jdk-11.0.4/bin/java"  
	           agentCommand = "sshpass -p '${rawPwd}' ssh -o StrictHostKeyChecking=no ${machineUser}@${hostIP} \"${javaPath} -Xmx1024m -jar ${workingRepo}/agent.jar -text\""		

	      }	 
		
	     // Jenkins Launch Method switch	 
	     ComputerLauncher launcher = null
	     switch(nodeLaunchMethod) {
		  case "viaJavaWebStart":
		        launcher = new JNLPLauncher()   //JNLP is the Java Web Start setting services use
		        break;
		  case "viaCommand":
		        launcher = new CommandLauncher(agentCommand)	 // Command Launch method from Master
		        break;
		  case "viaSSH":
		     if (machineOS == "Windows") {
		        //delete the exisiting credential before create it starts here 
		        def credentialsStore = jenkins.model.Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
		        allCreds = credentialsStore.getCredentials(Domain.global())
		        //here we cycle through all the credentials until we find the intended id
		        allCreds.each{
			//if we find the intended ID, delete it
			  if (it.id == host){
			    credentialsStore.removeCredentials(Domain.global(), it)
			   // sleep(30)
			  }
		        }		       
		      //remove the \ from password
		      //def replacemeachinepwd=machinePwd.replaceAll("[\\\\]","")
		      Credentials machineCredential = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, host, "${host}Credential", machineUser, rawPwd)
                      SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(), machineCredential)
		      //launcher = new SSHLauncher(hostIP, 22, host, null, javaPath, null, null)
		      launcher = new SSHLauncher(hostIP, 22, host, null, javaPath, workingDir, null,
						210, // Connection Timeout in Seconds
						10, // Maximum Number of Retries
						15,
						new hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy()
					       )
		     }
		     else {
			   launcher = new SSHLauncher(hostIP, 22, '745f56e1-0e68-44af-a8e9-c0d38d854688', null, javaPath, workingDir, null,
						210, // Connection Timeout in Seconds
						10, // Maximum Number of Retries
						15,
						new hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy()
					       )
		     }
		     break;
	         }
		    //create the nodes process 
            DumbSlave dumb = new DumbSlave(host,  // Agent name, usually matches the host computer's machine name
               host + "_" + "desc",           // Agent description
               workingRepo,         // Workspace on the agent's computer
               "3",                        // Number of executors
               Mode.EXCLUSIVE,             // "Usage" field, EXCLUSIVE is "only tied to node", NORMAL is "any"
               label,                      // Labels
               launcher,         // Launch strategy
               RetentionStrategy.INSTANCE) // Is the "Availability" field and INSTANCE means "Always"

            Jenkins.instance.addNode(dumb)
            println ">> Agent '$host' created with home directory as '$workingRepo'"
		   
	    if (nodeLaunchMethod == "viaJavaWebStart") {   
                String secretID = jenkins.model.Jenkins.getInstance().getComputer(host).getJnlpMac()
		if(dataToBeWritten == "") {		
	               dataToBeWritten = host + "_secret: " + secretID
		}
		else {		   
		       dataToBeWritten = dataToBeWritten + "\n" + host + "_secret: " + secretID		
                }
	      }	
		 
	    }
	  }
	
	 if (nodeLaunchMethod == "viaJavaWebStart") {   
	     //SecretID for each node is written on secretid.yml file. 				     
             file.writingContentinfile(secretIDFile, dataToBeWritten)
	     echo "Secret ID's are written to secretID.yml file"
	 }
	  
	if ( problematichosts.size() > 0) {
	     echo "*****************************************************************************"    	  
             echo "${unreachableAgentInfo}"		       
	     echo "*****************************************************************************"    
        }
	
	// Vault the Inventory file to secure sensitive data's
	try {
	ansibleVault action: 'encrypt', input: "${InventYML}", installation: 'ansible_2.8.0', vaultCredentialsId: 'AnsibleVaultPassword'
	}
	catch (Exception ex) {
	InventoryFile.delete()
	} 
	
	// Collecting all Online nodes and Problematic hosts to exclude from Ansible Slave Pre-setup
	excludeNodeList.addAll(problematichosts)
	def excludeHosts = excludeNodeList.toString().replace("[","").replace("]", "").replace(",", ":!").replace(" ", "")
	echo ">> Exclude Hosts from Ansible Playbook are ${excludeHosts}"
	
	// Setting up linux OS connections between Master and Slave. 
	echo ">> Setting up linux OS connections between Master and Slave."
	//Trigger Playbook for linux OS setup
	try {	
            if (common.isAllHostExcluded(hostnames, excludeNodeList) == false) {
	       if (nodeLaunchMethod == "viaJavaWebStart") {	    
	             ansiblePlaybook installation: 'ansible_2.8.0', limit: "all:!${excludeHosts}", disableHostKeyChecking: true, inventory: "${InventYML}", playbook: "${linuxPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars  'jenkinsURLPath=${jenURL} agentFilePath=${agentFilePath} linuxJavaPath=${linuxJavaPath}'"
	       }
	       else {	    
		    ansiblePlaybook installation: 'ansible_2.8.0', limit: "all:!${excludeHosts}", disableHostKeyChecking: true, inventory: "${InventYML}", playbook: "${linuxOpenSSHPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars  'agentFilePath=${agentFilePath} linuxJavaPath=${linuxJavaPath}'"
	       }	       
	    }
	    else { 
		echo ">> Linux Playbook execution is skipped since all the hosts are in excluded list."
	    }
	   }
	catch (Exception ex){
	    echo '## Linux Playbook is failed because of ' + ex 
	}
	
	// Enabling/Upgrading Powershell, .netframework and Winrm settings in Windows Machine for Ansible actions. 			  
	config.MachineDetails.Machine.each { node -> if(node['@os'] == "Windows"){windowHostnames.push(node['@host'].toString())}}
	println ">> Windows Machines are: "+windowHostnames.toString()
	println ">> excludeNodeList Machines are: "+excludeNodeList.toString()
	println ">> Copying files to slave start here"
	for (host in windowHostnames) {
		if (!excludeNodeList.contains(host)) {
		    echo "Files copying to host "+ host 
		    def guestPwd = Matcher.quoteReplacement(config.MachineDetails.'**'.find { node -> node['@host'] == host}['@password'].toString())
		    guestPwd = Matcher.quoteReplacement(common.decryptingPasswords("${EncryptionKey}", "${guestPwd}").trim()) 	 			  	
		    def guestuser = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@userName'].toString()				  		
		    def vcenter = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@vcenter'].toString()
		    def domain = "${host}\\\\${guestuser}"
		    def remoteCmds = new Utils.RemoteOperations()
		    Vspherecre_PSW = Matcher.quoteReplacement(Vspherecre_PSW)
		    	
		   if (vcenter != "Physical Machine") { 
		    remoteCmds.makeDirectoryonAgent(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir) 
		    remoteCmds.makeDirectoryonAgent(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "C:\\\\Windows\\\\SysWOW64\\\\config\\\\systemprofile\\\\Desktop")    
			
		    remoteCmds.CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${upgradePSPath}", "${WinrmWrkDir}\\\\Upgrade-PowerShell.ps1")
		    remoteCmds.CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${wmf3HotfixPSPath}", "${WinrmWrkDir}\\\\Install-WMF3Hotfix.ps1")   
		    remoteCmds.CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${winrmPSPath}", "${WinrmWrkDir}\\\\winrm_enable.ps1")   
	            remoteCmds.CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${winrmBatPath}", "${WinrmWrkDir}\\\\WinrmEnable.bat")
                    remoteCmds.CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${winRemoteCmdPath}", "${WinrmWrkDir}\\\\EnableRemoteCommands.bat")
	            remoteCmds.CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${openSSHPath}", "${WinrmWrkDir}\\\\OpenSSH-Win32.zip")
		    remoteCmds.CopyFilestoWindows(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, "${psexecPath}", "${WinrmWrkDir}\\\\PsExec.exe")	

		    remoteCmds.StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, "Upgrade-PowerShell.ps1")
  		    remoteCmds.StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, "Install-WMF3Hotfix.ps1")   
		    remoteCmds.StartPrgmonWindowsthruVghetto(guestOPScript, vcenter, Vspherecre_USR, Vspherecre_PSW, host, domain, guestPwd, WinrmWrkDir, "winrm_enable.ps1")      
			/*		  
		    def hostIP = vmOperations.getHostIP(vcenter, host)   
			  
		    remoteCmds.StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, "Upgrade-PowerShell.ps1")
  		    remoteCmds.StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, "Install-WMF3Hotfix.ps1")   
		    remoteCmds.StartPrgmonWindowsthruWinexe(hostIP, guestuser, guestPwd, WinrmWrkDir, "winrm_enable.ps1") */
		}
	     }
	}
	
	try {
		if (common.isAllHostExcluded(hostnames, excludeNodeList) == false) {
		//Setting up Windows OS connections between Master and Slave.
		   if (nodeLaunchMethod == "viaJavaWebStart") {	
		      ansiblePlaybook installation: 'ansible_2.8.0', limit: "all:!${excludeHosts}", disableHostKeyChecking: true, inventory: "${InventYML}", playbook: "${wdwPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars  'jenkinsURLPath=${jenURL} agentFilePath=${agentFilePath} windowsJavaPath=${windowsJavaPath} stagechoice=${stageChoice}'"
		   }
		   else {
		      ansiblePlaybook installation: 'ansible_2.8.0', limit: "all:!${excludeHosts}", disableHostKeyChecking: true, inventory: "${InventYML}", playbook: "${wdwOpenSSHPlaybook}", vaultCredentialsId: 'AnsibleVaultPassword', extras: "--extra-vars  'agentFilePath=${agentFilePath} windowsJavaPath=${windowsJavaPath} stagechoice=${stageChoice}'"	 
		   }
		   //sleep(30)
		}
		else { 
		   echo ">> Windows Playbook execution is skipped since all the hosts are in excluded list."
		}
		}
		catch (Exception ex){
			//sleep(30)
			echo '## Windows Playbook is failed because of ' + ex 
		}
              	echo ">> Pre-requisites done in slaves for execution."
	
	        if(nodeLaunchMethod == "viaSSH") {
			for(String host in hostnames) {
				if (!excludeNodeList.contains(host)) {
					def ExistSSHServersList = new FilePath(channel, "/tmp/ExistSSHServers")
					if (file.ContentExistinFile(ExistSSHServersList, host)){
						echo ">> Updating the SSH port number to 24"
						vcenter = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@vcenter'].toString()
						workingRepo = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@wrkingRepo'].toString()
						label = config.MachineDetails.'**'.find { node -> node['@host'] == host}['@label'].toString()
						hostIP = vmOperations.getHostIP(vcenter, host) 
						javaPath = "${workingRepo}\\jdk11\\jdk11_W\\bin\\java" 
						driveName   = workingRepo.split(':')
						workingDir = driveName[0] +": && "

						if (hostIP == null ) { hostIP = host }

						launcher = new SSHLauncher(hostIP, 24, host, null, javaPath, workingDir, null,
							210, // Connection Timeout in Seconds
							10, // Maximum Number of Retries
							15,
							new hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy())

						DumbSlave dumb = new DumbSlave(host,  // Agent name, usually matches the host computer's machine name
              						host + "_" + "desc",           // Agent description
               						workingRepo,         // Workspace on the agent's computer
               						"3",                        // Number of executors
               						Mode.EXCLUSIVE,             // "Usage" field, EXCLUSIVE is "only tied to node", NORMAL is "any"
               						label,                      // Labels
              					        launcher,         // Launch strategy
              					        RetentionStrategy.INSTANCE) // Is the "Availability" field and INSTANCE means "Always"

            					Jenkins.instance.addNode(dumb)
					}
				}
			}
	        }	
		
	        common.relaunchNode(hostnames)
		OfflineNodeList = common.getNodeStatus(hostnames, "offline")
		if ( OfflineNodeList.size() > 0) {
		    echo "Following agents are offline and do the needful"			 
  	            echo "**************************************************************************************************"
  	            echo "${OfflineNodeList}"				 
  	            echo "**************************************************************************************************"
		 }
	      echo ">> This stage is added all the given nodes in jenkins from the generated XML file and confirmed the Slave running status"
	      if ( OfflineNodeList.size() > 0) {
		    currentBuild.result = 'Failure'
		    continuePipeline = false
	      }
             return continuePipeline
}

@NonCPS
def getChannel(nodename)
{
if (!nodename.equals("master")) {
            channel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel() 
	}
	else {channel = null}	
	return channel
}
