/**
 * Perform VM operations - PowerOFF/ON, Revert based on Property file details 
 */
package VMLibrary

import groovy.json.JsonSlurperClassic
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.NodeChild
import groovy.util.XmlParser
import groovy.util.slurpersupport.GPathResult
import hudson.FilePath

def VMOperationCall()
{	
	def ReusableFunc = new Utils.GeneralReusables()
	def VMReusables = new Utils.VmOperationsUtils()
    //InputFile Keywords and given variables should be matched
	def poweroff = "VmPowerOff"
	def poweron = "VmPowerOn"    
	def revert = "VmRevert"
	def vmcreation = "VmCreation"
	def xmlNode = "VMSetup"
	def parallelExec = [:]         //Empty map for parallel execution
	def hostnames = []
	def failedHostNames = []
	def labelnames = []
	def failureNode = []
	def powerOffFailureNode = []
	def powerOnFailureNode = []
    	def readFileContents = ReusableFunc.ReadXml()
    	def config = new XmlSlurper().parseText(readFileContents)	
	boolean stageExist = ReusableFunc.isXmlNodeExists(xmlNode)
	
	if(!stageExist){
		config.'VMSetup'.'Machine'.each {node -> hostnames.push(node.@'host')}
    		hostnames = hostnames.minus("");
		println "Hostname "+hostnames.toString()
		config.'VMSetup'.'Machine'.each {node -> labelnames.push(node.@'label')}
		labelnames = labelnames.minus("");
		def NodeNames = []
		for(int i=0;i<labelnames.size; i++)
		{
			NodeNames = ReusableFunc.GetNodeNames(labelnames[i].toString())
			println"Label NodeNames: "+NodeNames.toString()
		}
		hostnames = hostnames+NodeNames;
		hostnames=hostnames.minus("");
		println "label + Hostname "+hostnames.toString()
		for (host in hostnames) 
		{
			def nodeName = host.toString();
			parallelExec [nodeName] = {
			def action,snapshot,network,template,cluster,datastore,folder = null
			if(labelnames.size < 1){labelnames = labelnames+hostnames}
			for(int i=0;i<labelnames.size; i++)
			{
				action = ReusableFunc.getXmlValue("VMSetup",nodeName,"action",labelnames[i].toString())
				println"action :"+action
				snapshot = ReusableFunc.getXmlValue("VMSetup",nodeName,"snap",labelnames[i])
				template = ReusableFunc.getXmlValue("VMSetup",nodeName,"Template",labelnames[i])
				cluster = ReusableFunc.getXmlValue("VMSetup",nodeName,"Cluster",labelnames[i])
				datastore = ReusableFunc.getXmlValue("VMSetup",nodeName,"Datastore",labelnames[i])
				folder = ReusableFunc.getXmlValue("VMSetup",nodeName,"Folder",labelnames[i])
				network = ReusableFunc.getXmlHostValues("MachineDetails",nodeName,"vcenter",labelnames[i])
				echo "Hostname: ${nodeName}; NetworkName: ${network}; Actions: ${action}; Snap: ${snapshot}"
			}
			for(int i=0;i<action.size; i++)
			{
				def powerOnFlag = false; def powerOffFlag = false; def revertFlag = false;
				def actions = action[i].toString();
				
				if(actions.contains(poweroff)){
					powerOffFlag = VMReusables.VmPowerOff(nodeName,network.toString())
					if (powerOffFlag == "false")
					{
						echo "Retrying to power off the node: ${nodeName}"
						powerOffFlag = VMReusables.VmPowerOff(nodeName,network.toString())
						if (powerOffFlag == "false")
						{
							powerOffFailureNode.add(nodeName.toString());
							//break;
						}
					}
				}
				else if(actions.contains(revert)){	
					powerOffFlag = VMReusables.VmPowerOff(nodeName,network.toString())
					sleep 15;
					revertFlag = VMReusables.VmRevert(nodeName,network.toString(),snapshot.join(","))
					sleep 35;
					powerOnFlag = VMReusables.VmPowerOn(nodeName,network.toString())
					sleep 60;
					if (powerOffFlag == "false" || revertFlag == "false" || powerOnFlag == "false")
					{
						failureNode.add(host.toString());
						break;
					}
				}
				else if(actions.contains(poweron)){
					powerOnFlag = VMReusables.VmPowerOn(nodeName.toString(),network.toString())
					sleep 60;
					if (powerOnFlag == "false" )
					{
						echo "Retrying to power on the node: ${nodeName}"
						powerOnFlag = VMReusables.VmPowerOn(nodeName.toString(),network.toString())
						sleep 60;
						if (powerOnFlag == "false" )
						{
							powerOnFailureNode.add(nodeName.toString());
							//break;
						}
					}
				}
				else if(actions.contains(vmcreation)){
					VMReusables.VmCreation(nodeName.toString(), network.toString(), template.toString(), cluster[i].toString(), datastore[i].toString(), folder[i].toString())
					sleep 15;
				}	
			}
		}
	}
	parallel parallelExec
	}
	else{
		echo "VMSetup stage inputs not available in input XML File"
	}
	if ((powerOffFailureNode.size() > 0) || (powerOnFailureNode.size() > 0))
	{
		echo "Power off failed for these nodes: ${powerOffFailureNode}"
		echo "Power on failed for these nodes: ${powerOnFailureNode}" 
	}
	return powerOffFailureNode + powerOnFailureNode
	
}
