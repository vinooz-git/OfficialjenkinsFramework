#!/usr/bin/env groovy

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

pipeline  {
 agent any 
  stages {
    stage('Build Availability'){
      steps {
       script {            
        
            def readFileContents = new File("${env.WORKSPACE}/Config/Configuration.xml").getText()
            def config = new XmlSlurper().parseText(readFileContents)
            def buildchoice = ""
        
            config.BuildAvailability.Builds.each { node ->
                  if(node['@BuildRepo'] == "Jenkins") {
                                       
                      if (node['@BuildChoice'] == "Today") {
                        buildchoice = "lastBuild"            
                      }
                      else if (node['@BuildChoice'] == "LastSuccessful") {
                        buildchoice = "lastSuccessfulBuild"
                      }
                      else if (node['@BuildChoice'] == "Predefined") {
                        buildchoice = node['@PredefinedBuildID']
                      }
                      else {
                        echo "Please enter the correct buildchoice values to proceed further"
                        return
                      }    

                   // Collecting Information from Jenkins using last build api call
                   def url = node['@URL']
                   def job = node['@Job']
                   def build = httpRequest "${url}/job/${job}/${buildchoice}/api/json" 
                   def buildApi = new JsonSlurperClassic().parseText(build.getContent())
                   echo "Build ID is: ${buildApi['id']}"          

                    // Converting Timestamp epoch/Unix time to human readable date format
                    def buildAvailableDate = convertEpochtoReadable (buildApi['timestamp'])
                    echo "Build Availble Date is: ${buildAvailableDate}"
        
                     if (buildchoice == "lastBuild") {
                        //Comparing today's date into last build date
                        if (buildAvailableDate == getTodaysDate()) {
                          echo "The last build ${buildApi['id']} is the today's build"
                          if (buildApi['result'] == 'SUCCESS') {
                              echo "Build Status: Success and its ready for Execution"
                           if (getValueFromXML("EmailSettings", "BuildSuccess") == "Yes"){
                              sendingEmailNotification (buildApi['id'], "BuildSuccess")
                           }
                          }
                          else {
                             echo "Build Status: Failure and its not good for Execution"
                           if (getValueFromXML("EmailSettings", "BuildFailure") == "Yes"){
                             sendingEmailNotification (buildApi['id'], "BuildFailure")
                           }
                             return
                          }                  
                        }
                        else {
                           echo "No latest build today"
                         if (getValueFromXML("EmailSettings", "NoLatestBuild") == "Yes"){
                           sendingEmailNotification ("", "NoLatestBuild")
                         }
                           return
                         } 
                      }
                     else if (node['@BuildChoice'] == "Predefined") {
                        if (buildApi['result'] == 'SUCCESS') {
                         echo "Build Status: Predefined build is ready for Execution"
                         if (getValueFromXML("EmailSettings", "BuildSuccess") == "Yes"){
                         sendingEmailNotification (buildApi['id'], "BuildSuccess")
                         }
                         }
                        else {
                           echo "Build Status: Failure and its not good for Execution"
                         if (getValueFromXML("EmailSettings", "BuildFailure") == "Yes"){
                           sendingEmailNotification (buildApi['id'], "BuildFailure")
                         }
                           return
                          }               
                      }
                    }
                    else if (node['@BuildRepo'] == "Folder") {
                     
                     def Builddesc = node['@Desc']
                     def usercredential = "/USER:"
                     if (node['@UserName'] == null || node['@UserName'].isEmpty())
                     {
                       usercredential = ""
                     }
                     
                     //def folderExist = bat label: 'Folderexist', returnStatus: true, script: "net use "+getValueFromXML("BuildAvailability", "BuildNetworkPFolderPath")+" "+getValueFromXML("BuildAvailability", "Password")+" /USER:"+getValueFromXML("BuildAvailability", "UserName")+" /persistent:No"
                     def folderExist = bat label: 'Folderexist', returnStatus: true, script: "net use "+node['@NetworkFolderPath']+" "+node['@Password']+" "+usercredential+node['@UserName']+" /persistent:No"
                     if (folderExist == 0) {
                      echo "Folder exists in the network Path"
                      bat returnStatus: true, script: "net use /delete "+node['@NetworkFolderPath']
                     }
                     else {
                      echo "Folder doesn't exist in the network Path or credentials are invalid."
                      if (getValueFromXML("EmailSettings", "FolderNotExist") == "Yes"){
                      sendingEmailNotification (Builddesc.toString(), "FolderNotExist")
                      }
                      return
                     }
                   }
                   else {
                     echo "Please enter the correct Build repository value to proceed further"
                   }
                  
                 }
       }
     }
   }    
   stage('Vm Setup'){
    steps {
       script {
          def parallelExec = [:]
          def hostnames = []
          def readFileContents = new File("${env.WORKSPACE}/Config/Configuration.xml").getText()
          def config = new XmlSlurper().parseText(readFileContents)
          //def ServerEntry = config.VMSetup.'**'.findAll{node -> node.name() == 'Server'}
        
          config.VMSetup.Server.each {node -> 
          hostnames.push(node['@host'].toString())}
        
          for (int i=0; i<hostnames.size(); ++i) {
             def nodeName = hostnames[i].toString();
             parallelExec [nodeName] = {
             def nw= config.VMSetup.'**'.find { Server -> Server['@host'] == nodeName}['@network'].toString()
             def action= config.VMSetup.'**'.find { Server -> Server['@host'] == nodeName}['@actions'].toString()
             def sn= config.VMSetup.'**'.find { Server -> Server['@host'] == nodeName}['@snap'].toString()
             echo "Hostname: ${nodeName}; NetworkName: ${nw}; Actions; ${action}; Snap: ${sn}"        
            }
           }
        
        parallel parallelExec
        
        config = null
        readFileContents = null 
        hostnames = null
        
        
        /*config.VMSetup.Server.each {node ->                   
        def host = node['@host']
        def network =  node['@network']
        def actions = node['@actions']
        def snap = node['@snap'] 
         echo "Hostname: ${host}; NetworkName: ${network}; Actions; ${actions}; Snap: ${snap}"       
        }*/
        }
       }
     }   
 }
}

//Converting epoch/Unix timestamp to human readable and the output format is dd/MM/yyyy
@NonCPS
def convertEpochtoReadable (timestamp) {
    Date buildTS = new Date(timestamp)
    DateFormat TSformat = new SimpleDateFormat("dd/MM/yyyy")
    def Date = TSformat.format(buildTS).toString();
    return Date
}

// Collecting Today's date and convert into the format dd/MM/yyyy
@NonCPS
def getTodaysDate () {
  LocalDate today = LocalDate.now();
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  def todayDate = today.format(formatter).toString();
  echo "Today's Date is: ${todayDate}"
  return todayDate
}

//Finding the value for Key Paired using the node. 
@NonCPS
def getValueFromXML(nodeValue, keyValue) {
 def readFileContents = new File("${env.WORKSPACE}/Config/Configuration.xml").getText()
 def config = new XmlSlurper().parseText(readFileContents)
 //def JenkinsBuildURL = config.appSettings.add[0]['@key']
 def value = config."${nodeValue}".'**'.find { add -> add['@key'] == keyValue}['@value'].toString()
 
 config = null
 readFileContents = null
 return value
}

//Finding the Email subject and Body using the Mail type and node values. 
@NonCPS
def getEmailContentFromXML(mailType, nodeValue) {
 def readeMailFileContents = new File("${env.WORKSPACE}/Config/EmailNotificationConfig.xml").getText()
 def emailconfig = new XmlSlurper().parseText(readeMailFileContents)
 def value = emailconfig.Content."${mailType}"."${nodeValue}".text().toString()
 
 emailconfig = null
 readeMailFileContents = null
 return value
}

//Sending Email notification with correct templates. 
@NonCPS
def sendingEmailNotification(passValues, mailType){
 def subj = ""
 def msg = ""
 def recipient = getValueFromXML("EmailSettings", "RecipientList")
 
 if ("${getEmailContentFromXML(mailType, "subject")}".contains("{passarg}")) {
  subj = "${getEmailContentFromXML(mailType, "subject")}".replaceAll("passarg", passValues)
 }
 else {
  subj = "${getEmailContentFromXML(mailType, "subject")}"
 }
 
 if ("${getEmailContentFromXML(mailType, "messageDetail")}".contains("{passarg}")) {
  msg = "${getEmailContentFromXML(mailType, "messageDetail")}".replaceAll("passarg", passValues)
 } 
 else {
   msg = "${getEmailContentFromXML(mailType, "messageDetail")}"
 }
 
 emailext body: "${msg}", subject: "${subj}", to: "${recipient}"
 
}