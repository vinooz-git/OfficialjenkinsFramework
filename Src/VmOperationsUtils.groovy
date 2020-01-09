package Utils
import com.vmware.vim25.mo.VirtualMachineSnapshot;

/* Available Method :
 * VmRevert
 * VmPowerOn
 * VmPowerOff
 * VmCreation
 */
 
/**
 * Revert Machine to given Snapshot
 * Parameters :
 */
 def VmRevert(String VmName,String Network,String Snapshot){
	try
	{
	    vSphere buildStep: [$class: 'RevertToSnapshot', snapshotName: Snapshot.trim(), vm: VmName.trim()], serverName: Network.trim()
		echo "${VmName} is Reverted to ${Snapshot} - Snapshot"
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
	}
 }
	
/**
 * PowerOn the given Vm Machine
 *Parameters :
 */
//@NonCPS
 def VmPowerOn(String VmName,String Network){
	def VMPowerOnFlag = "false"
 	try{
		vSphere buildStep: [$class: 'PowerOn', timeoutInSeconds: 260, vm: VmName.trim()], serverName: Network.trim()
		sleep 30;
		echo "${VmName} is Switched ON"
		VMPowerOnFlag = "true"
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		VMPowerOnFlag = "false"
	}
	//echo "VMPowerOnFlag :" +VMPowerOnFlag
	 return VMPowerOnFlag
}
	
/**
 * PowerOff the given Vm Machine
 *Parameters :
 */
 def VmPowerOff(String VmName,String Network){
	def VMPowerOffFlag = "false"
	try
	{
	    vSphere buildStep: [$class: 'PowerOff', evenIfSuspended: false, ignoreIfNotExists: false, shutdownGracefully: true, vm: VmName.trim()], serverName: Network.trim()
		echo "${VmName} is Switched Off"
		VMPowerOffFlag = "true"
	}		
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		VMPowerOffFlag = "false"
	}
	//echo "VMPowerOffFlag: " +VMPowerOffFlag
	return VMPowerOffFlag
}
	
/**
 * Create a VM machine based on Inputs
 */
def VmCreation(String VmName,String Network,String Template,String Cluster,String Datastore,String Folder)
{
	try
	{
		vSphere buildStep: [$class: 'Deploy', clone: VmName, cluster: Cluster, datastore: Datastore, folder: Folder, linkedClone: false, powerOn: true, resourcePool: '', template: Template, timeoutInSeconds: 60], serverName: Network.trim()
		//vSphere buildStep: [$class: 'Deploy', clone: 'ica-tst30-ws12', cluster: 'sdg-vctr', datastore: 'sdg-pqa-12', folder: 'User Resources/PQA/Automation/VMCheck/', linkedClone: false, powerOn: true, resourcePool: '', template: 'w2012r2-std', timeoutInSeconds: 60], serverName: 'SDG'
		echo "VM Created Successfully"
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
	}
}

@NonCPS
def getHostIP(vcenter, hostname)
{
	try {
		echo "Attempting to expose the machine ${hostname}"
		echo "${hostname},${vcenter}"
		def hostIP = vSphere buildStep: [$class: 'ExposeGuestInfo', envVariablePrefix: 'VSPHERE', vm: hostname.trim(), waitForIp4: true], serverName: vcenter.trim()		
		return hostIP
	}
	catch(Exception ex) {
	        echo '## Exception reason: ' + ex
	}
}
