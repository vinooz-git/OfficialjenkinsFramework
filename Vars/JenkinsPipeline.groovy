/**
 * Jenkins CI/CD Pipeline stages
 */ 
def call() 
{	
	def continuePipeline = true
	
	stage('BuildAvailability')
	{
		def methodcall = new BuildLibrary.BuildAvailability()
		continuePipeline = methodcall.BuildAvailCall()
		echo "continuePipeline status :"+continuePipeline
		echo "BuildAvailability completed"
    } 
	if(continuePipeline){
	
	stage('VmSetup') 
	{
		def methodcall = new VMLibrary.VmOperation()
		methodcall.VMOperationCall()
		echo "Vm operations completed"
    }
	
	stage('ServerSetup')
	{
	 	def methodcall = new ServerSetup.ServerSetup()
		methodcall.ServerOperations()
		echo "ServerSetup Stage Completed" 
    } 
	
	def parallelExec = [:]
	parallelExec["ClientSetup"] = {
	stage('Script Execution')
	{
		def methodcall = new ClientSetup.ClientSetup()
		methodcall.ClientOperations()
		echo "ClientSetup stage"
    }  
	}
	parallelExec["SonarQube"] = {
	
	stage('SonarQube')
	{
		def methodcall = new ScanLibrary.SonarQubeExecutor()
		methodcall.SonarOperations()
		echo "SonarQube stage completed"
    } 
	}
	parallelExec["AppScan"] = {
	
	stage('AppScan')
	{
		def methodcall = new ScanLibrary.Appscan()
		methodcall.appscanner()
		echo "AppScanner stage completed"
	}
	}
	parallel parallelExec
	}
	else
	{
		echo "There are failure builds while checking your given build status. Check your RecipientList's mail for more details"
		currentBuild.result = 'Failure'
	}
}

