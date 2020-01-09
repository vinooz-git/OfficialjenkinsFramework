import hudson.FilePath
import hudson.model.ParametersAction
import hudson.model.FileParameterValue
import hudson.model.Executor

def call(String name, String fname = null) {
    def paramsAction = currentBuild.rawBuild.getAction(ParametersAction.class);
	print "paramsAction"+paramsAction.toString()
	def paramValue = paramsAction.getParameters().toString()
	print "params Value: "+paramValue
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
                  workspace = new FilePath(null, env['WORKSPACE']+"@libs/ProcessToolsLib")
	          inventory = new FilePath(null, "${env['WORKSPACE']}"+"@libs/ProcessToolsLib/src/ansibleActions/inventory/")
                } 
				else {
                   channel = Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel()
                   workspace = new FilePath(channel, env['WORKSPACE'])
					inventory = new FilePath(channel, "${env['WORKSPACE']}"+"/ansibleActions/inventory/")		     
                } 
				print"workspace:"+workspace
				if (param.getName().equals("Invent_YML")) {
				file = inventory.child(param.getOriginalFileName().toString())
				}
				else {
                  file = workspace.child(param.getOriginalFileName().toString())	 
				}
                
				file.copyFrom(param.getFile()) 
                return file.toString();
            }
        }
    }
	print " User have not uploaded input Files. So we picked input files from workspace "
	}
    print " User have not uploaded input Files, So we picked input files from workspace "
}
