package Utils

import hudson.FilePath
import hudson.model.ParametersAction
import hudson.model.FileParameterValue
import hudson.model.Executor
import hudson.*
import hudson.model.*
import java.lang.Object
import groovy.time.*
import jenkins.model.*
import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.*;
import groovy.stream.*
import hudson.FilePath
import hudson.model.ParametersAction
import hudson.model.FileParameterValue
import hudson.model.Executor

/*
 * Robo-Copy File within a Machine
 * Parameters :
 */
def CopyFiles(CopyFromFolder,CopytoFolder,Filename)
{
	try
	{
		def status = bat label: 'CopyFiles', returnStatus: true, script: "((robocopy \"${CopyFromFolder}\" \"${CopytoFolder}\" ${Filename} > C:\\log.txt) ^& IF %ERRORLEVEL% LEQ 4 exit /B 0)"
		if(status != 0)
		{
			echo "Failed command is "+command
			currentBuild.result = 'Failure'
		}
		return status
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}	
}

def CopyFolder(CopyFromFolder,CopytoFolder)
{
	try{
		def status = bat label: 'CopyFolder', returnStatus: true, script: "((robocopy \"${CopyFromFolder}\" \"${CopytoFolder}\" /S /MT:100 > C:\\log.txt) ^& IF %ERRORLEVEL% LEQ 4 exit /B 0)"
		if(status != 0)
		{
			echo "Failed command is "+command
			currentBuild.result = 'Failure'
		}
		return status
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
		currentBuild.result = 'Failure'
	}
}

/*
 * Robo-Copy Folder from other network with User name and Password
 * Parameters :
 */
def CopyfromOtherNetwork(CopyFromFolder,CopytoFolder,username,pswd)
{	
	//bat label: '', script:"net use "+CopyFromFolder+" "+ pswd +" /USER:whatEver\"+username +" /persistent:No"
	//CopyFromFolder = CopyFromFolder.replace("//","\\\\")
	//CopyFromFolder = CopyFromFolder.replace("/","\\")
	println "Before netuse CopyFromFolder: "+CopyFromFolder
	def status = bat label: 'Folderexist', returnStatus: true, script: "net use " + CopyFromFolder + " " + pswd + " " + "/USER:" + username + " /persistent:No"
	if(status == 0){
		def Copystatus = bat label: 'NetworkCopy',returnStatus: true, script: "((robocopy \"${CopyFromFolder}\" \"${CopytoFolder}\" /S /MT:100 > C:\\log.txt) ^& IF %ERRORLEVEL% LEQ 4 exit /B 0)"
		//Disconnect Network
		bat"net use * /delete /y"
		return Copystatus
	}
	else{
		echo 'Exception reason and status code: ' + status
		echo 'the folde not accessible - '+CopyFromFolder
	}
}
def CopyToOtherNetwork(CopyFromFolder,CopytoFolder,username,pswd)
{	
	//bat label: '', script:"net use "+CopyFromFolder+" "+ pswd +" /USER:whatEver\"+username +" /persistent:No"
	//CopyFromFolder = CopyFromFolder.replace("//","\\\\")
	//CopyFromFolder = CopyFromFolder.replace("/","\\")
	println "Before netuse CopyFromFolder: "+CopytoFolder
	def status = bat label: 'Folderexist', returnStatus: true, script: "net use " + CopytoFolder + " " + pswd + " " + "/USER:" + username + " /persistent:No"
	if(status == 0){
		def Copystatus = bat label: 'NetworkCopy',returnStatus: true, script: "((robocopy \"${CopyFromFolder}\" \"${CopytoFolder}\" /S /MT:100 > log.txt) ^& IF %ERRORLEVEL% LEQ 4 exit /B 0)"
		//Disconnect Network
		bat"net use * /delete /y"
		//echo 'CopyToOtherNetwork status code: ' + Copystatus
		return Copystatus
	}
	else{
		echo 'Exception reason and status code: ' + status
		echo 'the folde not accessible - '+CopyFromFolder
	}
}
/*
 * Robo-Copy file from other network with User name and Password
 * Parameters :
 */
def CopyFilefromOtherNetwork(CopyFromFolder,CopytoFolder,Filename,username,pswd)
{	
	//bat label: '', script:"net use "+CopyFromFolder+" "+ pswd +" /USER:whatEver\"+username +" /persistent:No"
	def status = bat label: 'Folderexist', returnStatus: true, script: "net use " + CopyFromFolder + " " + pswd + " " + "/USER:" + username + " /persistent:No"
	if(status == 0){
		def Copystatus = bat label: 'NetworkFileCopy', returnStatus: true, script: "((robocopy \"${CopyFromFolder}\" \"${CopytoFolder}\" ${Filename} > C:\\log.txt) ^& IF %ERRORLEVEL% LEQ 4 exit /B 0)"
		//Disconnect Network
		bat"net use * /delete /y"
		return Copystatus
	}
	else{
		echo 'Exception reason and status code: ' + status
		echo 'the folde not accessible - '+CopyFromFolder
	}
}
/*
 * Scope: unZip the zip Folder
 * Parameters :
 */
def fileUnZipOperation(path,BuildOutputLoc)
{
	try
	{
	fileOperations([fileUnZipOperation(filePath: path, targetLocation: BuildOutputLoc)])
	}
	catch(Exception e)
	{
		echo 'Exception reason: ' + e
		echo 'Stacktrace: ' + e.getStackTrace()
	}
}

//Copy files between linux machines
def FileCopybetweenLinux(Sourcefile,DestinationIP,password,DestinationUser,DestinationPath)
{
	
	String command = "sshpass -p " + "\'" + password + "\'" + " scp -r " + Sourcefile + " " +DestinationUser + "@" + DestinationIP + ":/" + DestinationPath + "/"
	println "Command:" + command
	sh script: command
}

//Folder creation to local machine
def FolderCreationtoLocal(FolderPath)
{
	fileOperations([folderCreateOperation(FolderPath)])
	println "Folder Created to path "+FolderPath
}

//Folder Rename to local machine
def FolderRenametoLocal(OldName,NewName)
{
	fileOperations([folderRenameOperation(destination: NewName, source: OldName)])
	println "Folder name renamed to "+NewName
}

//Folder copy to local machine
def FolderCopytoLocal(Source,Destination)
{
	fileOperations([folderCopyOperation(destinationFolderPath: Destination, sourceFolderPath: Source)])
	println "Folder Copied to "+Destination
}

//Folder deletion from local machine
def FolderDeletefromLocal(FolderPath)
{
	new File(FolderPath).delete()
	println "Folder deleted from "+FolderPath
}

//File creation to local machine
def FileCreationtoLocal(FilePath,FileContent='')
{
	fileOperations([fileCreateOperation(fileContent: FileContent, fileName: FilePath)])
	println "File created to path "+FilePath
}

//File Rename to local machine
def FileRenametoLocal(OldName,NewName)
{
	fileOperations([fileRenameOperation(destination: NewName, source: OldName)])
	println "file Rename to "+NewName
}

//File deletion from local machine
def FileDeletefromLocal(FilePath)
{
	fileOperations([fileDeleteOperation(excludes: FilePath, includes: '')])
	println "file deleted from "+FilePath
}

//File copy to local machine
def FileCopytoLocal(Source,Destination)
{
	fileOperations([fileCopyOperation(excludes: Source, flattenFiles: false, includes: '', targetLocation: Destination)])
	println "File copied to "+Destination
}

//Extract the TAR file to local machine
def FileUnTARtoLocal(Source,Destination)
{
	fileOperations([fileUnTarOperation(filePath: Source, isGZIP: false, targetLocation: Destination)])
	println "Tar file extracted to "+Destination
}

@NonCPS
def FileUpload(String name, String fname = null) {
    def paramsAction = currentBuild.rawBuild.getAction(ParametersAction.class);
	//print "paramsAction"+paramsAction.toString()
	def paramValue = paramsAction.getParameters().toString()
	//print "params Value: "+paramValue
    if (paramsAction != null) {
		if(paramValue.contains("xml") || paramValue.contains("yml")){
        for (param in paramsAction.getParameters()) {
			
		 if (param.getName().equals(name)) {
                if (! param instanceof FileParameterValue) {
                    error "unstashParam: not a file parameter: ${name}"
                }
                if (env['NODE_NAME'] == null) {
                    error "unstashParam: no node in current context"
                }
				//print"env['NODE_NAME']:"+env['NODE_NAME']
                if (env['WORKSPACE'] == null) {
                    error "unstashParam: no workspace in current context"
                }
				//print"env['WORKSPACE']:"+env['WORKSPACE']
				if (env['NODE_NAME'].equals("master")) {
                  workspace = new FilePath(null, env['WORKSPACE']+"@libs//ProcessToolsLib")
                } else {
                  channel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel()
                  workspace = new FilePath(channel, env['WORKSPACE'])
                }
				print"workspace:"+workspace
                filename = fname == null ? param.getOriginalFileName() : fname
                file = workspace.child(filename.toString())
                file.copyFrom(param.getFile())
                return filename;
            }
        }
    }
	//print " User have not uploaded input Files. So we picked input files from workspace "
	}
    print "User have not uploaded input Files, So we picked input files from workspace"
}

def writingContentinfile(filePath, dataToBeWritten)
{
      filePath.write(dataToBeWritten, "utf-8")		    
}

def replaceContentinfile(invtFile, findLine, minusContent, newContent, credential = null)
{
	//print (findLine + " Find line")
 try { 
    def fileContent = "";
    BufferedReader reader = new BufferedReader(new StringReader(invtFile.readToString()));	 
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
//	 print    (line)
	    
	if (line.contains (findLine)) {
		//echo "Inner loop: ${findLine}"
	     def replaceContent = line.minus(minusContent).trim()
	     if (newContent == "DECRYPTPWD") {
		  def jenkins = new Utils.GeneralReusables()
		  def decrypted = jenkins.decryptingPasswords(credential, replaceContent) 
		  line = line.replace(replaceContent, decrypted)
	      }

	      else {	
			line = line.replace(replaceContent, newContent)
	      }
	  }
	  fileContent = fileContent + line + System.lineSeparator();
			//       println (fileContent   + " fileContent ")
       }
	reader.close();
	writingContentinfile(invtFile, fileContent)   
   }
  catch(Exception ex) {
   echo '## Exception reason: ' + ex
  }
}

def ContentExistinFile(ipFile, findLine) {
    BufferedReader reader = new BufferedReader(new StringReader(ipFile.readToString()));	 
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
	    if (line.contains (findLine)) {
		    return true;
	    }
    }
    return false;	
}
