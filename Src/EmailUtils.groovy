package Utils

import hudson.model.*;
import jenkins.model.*;
import hudson.tools.*;
import hudson.util.Secret;
import javax.mail.*
import javax.mail.internet.*
import hudson.FilePath

def EmailNotification(Subject, msgtxt, predefined = null, passvalue = null, passvalue2=null, passvalue3=null, attachment = null)
{
 def ReusableFunc = new Utils.GeneralReusables()
 def receivers = ReusableFunc.getValueFromXML("EmailSettings", "RecipientList")
 def CCList = ReusableFunc.getValueFromXML("EmailSettings", "CCList")
if(receivers != null)
 {
 //Predefined Message Template 
 if(predefined != null)
 {
  if(Subject == null)
  {
   Subject = ""
	if (getEmailContentFromXML(predefined, "subject").contains("passarg")) {
		Subject = getEmailContentFromXML(predefined, "subject").replaceAll("passarg", passvalue)
	}
	else {
		Subject = getEmailContentFromXML(predefined, "subject")
	}
  }
	msgtxt = ""
  if (getEmailContentFromXML(predefined, "messageDetail").contains("passarg")) {
   msgtxt = getEmailContentFromXML(predefined, "messageDetail").replaceAll("passarg", passvalue)
   if(passvalue2 != null){ msgtxt = msgtxt.replaceAll("arg2", passvalue2)
   if(passvalue3 != null){ msgtxt = msgtxt.replaceAll("arg3", passvalue3)
   //print "msgtxt :"+msgtxt
   }
  }
  else {
   msgtxt = getEmailContentFromXML(predefined, "messageDetail")
  }
 }
 }
 def instance = Jenkins.getInstance()
 def mailServer = instance.getDescriptor("hudson.tasks.Mailer")
 def jenkinsLocationConfiguration = JenkinsLocationConfiguration.get()
 def SystemAdminMailAddress = jenkinsLocationConfiguration.getAdminAddress()
 //Getting E-mail Server
 def SMTPHost = mailServer.getSmtpHost()
 //print"SMTPHost:"+SMTPHost
 def SMTPPort = mailServer.getSmtpPort()
 instance.save()
 Properties props = new Properties();
 props.put("mail.smtp.host", SMTPHost);
 props.put("mail.smtp.port", SMTPPort);
 Session session = Session.getInstance(props, null);
 if (attachment == null)
 {
  Message message = new MimeMessage(session);
  message.setFrom(new InternetAddress(SystemAdminMailAddress));
  message.setRecipients(Message.RecipientType.TO, receivers);
  message.setRecipients(Message.RecipientType.CC, CCList);
  message.setSubject(Subject);
  message.setContent(msgtxt.toString(),"text/html")
  println 'Sending mail to ' + receivers + '.'
  Transport.send(message);
  println 'Mail sent.'
 }
 else
 {
  String[] receipents = attachment.split(',')
  Message msg = new MimeMessage(session);
  msg.setFrom(new InternetAddress(SystemAdminMailAddress))
  msg.setRecipients(Message.RecipientType.TO, receivers);
  msg.setRecipients(Message.RecipientType.CC, CCList);
  msg.setSubject(Subject)
  BodyPart messageBodyPart = new MimeBodyPart()
  messageBodyPart.setContent(msgtxt.toString(),"text/html")
  Multipart multipart = new MimeMultipart()
  multipart.addBodyPart(messageBodyPart)
  messageBodyPart = new MimeBodyPart()
  for(String toaddress in receipents)
  {
  messageBodyPart = new MimeBodyPart()
  messageBodyPart.attachFile(toaddress)  
  multipart.addBodyPart(messageBodyPart)
  println "--> Attachment Added:" +toaddress
  }
  // Send the complete message parts
  msg.setContent(multipart)
  println 'Sending mail to ' + receivers + '.'
  Transport.send(msg)   
  println "--> Mail sent with Attachment"
 }
 }
 else {
	println "Email Settings details are not present in input Xml File. Please fill out Email settings inputs for Email Notifications"
 }
}

@NonCPS
def getEmailContentFromXML(mailType, nodeValue) {
	def value = null;
 try
 {
	EmailTemplateXml = "${MasterWorkspace}"+"//src//ConfigFiles//EmailTemplate.xml"
	//def readeMailFileContents = new File("${EmailTemplateXml}").getText()
	if (! env.MasterNode.equals("master")) {			      
		channel = Jenkins.getInstance().getComputer("${env.MasterNode}").getChannel() 
		readeMailFileContents = new FilePath(channel, "${EmailTemplateXml}").readToString()
		readeMailFileContents = readeMailFileContents.trim().replaceFirst("^([\\W]+)<","<");
	}
	else{
		readeMailFileContents = new File("${EmailTemplateXml}").getText()
	}
	def emailconfig = new XmlSlurper().parseText(readeMailFileContents)
	value = emailconfig.Content."${mailType}"."${nodeValue}".text().toString()
 }
 catch(Exception e)
 {
	echo "Node ${nodeValue} and key value ${mailType} is not in input Xml File: " + e
 }
 return value
}
