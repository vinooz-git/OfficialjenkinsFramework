/**

 * Checks build availabilities - checks Today's build,LastSuccessful,Predefined builds and verify the given folder path exists

 */

package BuildLibrary
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
import hudson.FilePath
import hudson.FilePath
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

def BuildAvailCall()
 {
	def continuePipeline = true
	def xmlNode = "BuildAvailability"
	def Email = new Utils.EmailUtils()
	def ReusableFunc = new Utils.GeneralReusables()
	def readFileContents = ReusableFunc.ReadXml() 
	//print "readFileContents:"+readFileContents
	def config = new XmlSlurper( false, false ).parseText(readFileContents)
   	def buildchoice = ""
	boolean stageExist = ReusableFunc.isXmlNodeExists(xmlNode) //Checks Build availability stage in input Xml file
	if(!stageExist){
    	config.BuildAvailability.Builds.each {
        node ->
            if (node['@BuildRepo'] == "Build Server") {
                if (node['@BuildChoice'] == "Today") {
                    buildchoice = "lastBuild"
                } else if (node['@BuildChoice'] == "LastSuccessful") {
                    buildchoice = "lastSuccessfulBuild"
                } else if (node['@BuildChoice'] == "Predefined") {
                    buildchoice = node['@PredefinedBuildID']
                } else {
                    echo "Please enter the correct buildchoice values to proceed further"
                    return
				}
				// Collecting Information from Jenkins using last build api call
                def url = node['@URL']
                def job = node['@Job']
                def build = httpRequest ignoreSslErrors: true, url:"${url}/job/${job}/${buildchoice}/api/json"
                def buildApi = new JsonSlurperClassic().parseText(build.getContent())
                echo "Build ID is: ${buildApi['id']}"

                // Converting Timestamp epoch/Unix time to human readable date format
                def buildAvailableDate = ReusableFunc.convertEpochtoReadable(buildApi['timestamp'])
                echo "Build Available Date is: ${buildAvailableDate}"

                if (buildchoice == "lastBuild") {
                    //Comparing today's date into last build date
                    if (buildAvailableDate == ReusableFunc.getTodaysDate()) {
                        echo "The last build ${buildApi['id']} is the today's build"
                        if (buildApi['result'] == 'SUCCESS') {
                            echo "Build Available Status: Success and its ready for Download"
                            if (ReusableFunc.getValueFromXML("EmailSettings", "BuildSuccess") == "Yes") {
								Email.EmailNotification("","","BuildSuccess",buildApi['id'])
                            }
                        } else {
                            echo "Build Available Status: Failure and its not good for Execution"
                            if (ReusableFunc.getValueFromXML("EmailSettings", "BuildFailure") == "Yes") {
								Email.EmailNotification("","","BuildFailure",buildApi['id'])
                            }
							continuePipeline = false
                            return continuePipeline
                        }
                    } else {
                        echo "No latest build available today"
                        if (ReusableFunc.getValueFromXML("EmailSettings", "NoLatestBuild") == "Yes") {
                            Email.EmailNotification("","","NoLatestBuild")
                        }
						continuePipeline = false
                        return continuePipeline
                    }
                } else if (node['@BuildChoice'] == "Predefined") {
                    if (buildApi['result'] == 'SUCCESS') {
                        echo "Build Available Status: Predefined build is available for Download"
                        if (ReusableFunc.getValueFromXML("EmailSettings", "BuildSuccess") == "Yes") {
                            Email.EmailNotification("","","BuildSuccess",buildApi['id'])
                        }
                    } else {
                        echo "Build Available Status: Failure and its not good for Execution"
                        if (ReusableFunc.getValueFromXML("EmailSettings", "BuildFailure") == "Yes") {
                            Email.EmailNotification("","","BuildFailure",buildApi['id'])
                        }
						continuePipeline = false
                        return continuePipeline
                    }
                }
            }
        else if (node['@BuildRepo'] == "Folder") {
            def Builddesc = node['@Desc']
            def usercredential = "/USER:"
            if (node['@UserName'] == null || node['@UserName'].isEmpty()) {
                usercredential = ""
            }
		def folderExist = null
		if(isUnix())
		{
			def GuestPwd = Matcher.quoteReplacement(node['@Password'].toString())
			GuestPwd = Matcher.quoteReplacement(ReusableFunc.decryptingPasswords("${EncryptionKey}", "${GuestPwd}").trim())
			try{
			sh label: '', script: "cd "+env.MasterWorkSpace + " && mkdir mountfolder2"
			}
			catch(Exception execp)
			{
				println "Unable to create a folder due to: " +execp
			}
			String nwpath = node['@NetworkFolderPath']
			//folderExist = sh label: 'Folderexist', returnStatus: true, script: "sudo -S mount -t cifs "+node['@NetworkFolderPath']+ " " +env.MasterWorkSpace + "/mountfolder -o username="+node['@UserName']+",password="+"${GuestPwd}"
			folderExist = sh label: 'Folderexist', returnStatus: true, script: "sudo -S mount -t cifs "+node['@NetworkFolderPath']+ " " +env.MasterWorkSpace + "/mountfolder2 -o username="+node['@UserName']+",password="+"${GuestPwd}"			
			println "folderExist:" +folderExist
			if (folderExist == 0) {
				echo "Folder exists in the network Path"
				//sh returnStatus: true, script: "lsof " +env.MasterWorkSpace + "/mountfolder2"	 
				sh returnStatus: true, script: "sudo -S umount " +env.MasterWorkSpace + "/mountfolder2"
				//sleep(10)
				//sh label: '', script: "cd "+env.MasterWorkSpace + " && rm -rf mountfolder2"
			}	
		}
		else
		{
			def GuestPass = Matcher.quoteReplacement(node['@Password'].toString())
			GuestPass = Matcher.quoteReplacement(ReusableFunc.decryptingPasswords("${EncryptionKey}", "${GuestPass}").trim())
			GuestPass = GuestPass.replace("\\","")
			println "GuestPass1:"+GuestPass
			echo"commands:"+"net use " + node['@NetworkFolderPath'] + " " + GuestPass + " " + usercredential + node['@UserName'] + " /persistent:No"
			println "GuestPass2:"+GuestPass
			folderExist = bat label: 'Folderexist', returnStatus: true, script: "net use " + node['@NetworkFolderPath'] + " " + GuestPass + " " + usercredential + node['@UserName'] + " /persistent:No"
			println "folderExist:" +folderExist
		}
            if (folderExist == 0) {
                echo "Folder exists in the network Path"
			} 
			else {
                echo "Folder doesn't exist in the network Path or credentials are invalid."
                if (ReusableFunc.getValueFromXML("EmailSettings", "FolderNotExist") == "Yes") {
                    Email.EmailNotification("","","FolderNotExist",node['@NetworkFolderPath'].toString())
                }
				continuePipeline = false
                return continuePipeline
            }
        } 
		else {
            echo "Please enter the correct Build repository value to proceed further"
			continuePipeline = false
                return continuePipeline
        }
	}
    }
	else
	{
		echo "BuildAvailability stage inputs not available in input XML File"
	}
	return continuePipeline
}
