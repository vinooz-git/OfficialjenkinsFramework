/**
 * Jenkins security patch stages
 */ 
def call() 
{	
	def continuePipeline = true
	
	if(continuePipeline){
	stage('ScriptExecution')
	{
		def methodcall = new ClientSetup.ClientSetup()
		methodcall.ClientOperations()
		echo "ClientSetup stage"
    }  
	}
	
	else
	{
		echo "There are failure builds while checking your given build status. Check your RecipientList's mail for more details"
		currentBuild.result = 'Failure'
	}
}
