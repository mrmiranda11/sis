package com.experian

import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import com.experian.jmx.JMXHelper;

// Logger
import com.experian.util.DAAgnostic
import com.experian.util.JSONCodeGen
import com.experian.util.JSONFaker
import com.experian.util.JSONCodeGenV2
import com.experian.util.JSONFakerV2
import com.experian.util.MD5
import com.experian.util.MyInterfaceBrokerProperties
import com.experian.util.SISSwaggerGen
import groovy.json.JsonOutput
import org.apache.commons.codec.binary.Base64;
import com.experian.util.*;

/**
 *
 *
 *
 *
 */
public class GenerateJSONSchema implements GroovyComponent<Message> {

    protected static final ExpLogger  logger     = new ExpLogger(this);
    protected static final String     TR_SUCCESS = "success";
    protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
    protected static       boolean    generateJSON = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('json.generate'));
    protected              int        errorCode = 0;
    protected              String     errorMessage = "";
    protected              Properties prop = new Properties();
    protected static String version = "2.11";
	protected static       Hashtable  authUsers = new Hashtable();
    protected static       Hashtable  authPass = new Hashtable();	
	protected static       Hashtable  authMethods = new Hashtable();
    protected static       Properties authUsersProp = new Properties();
    protected static       String     keyfile = MyInterfaceBrokerProperties.getPropertyValue('authentication.keyfile');

    public GenerateJSONSchema(){
        File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
        String checksum = MD5.getMD5Checksum(groovyFile);

        logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);

        InputStream inS = DoBatchCallDA.class.getClassLoader().getResourceAsStream("decisionagent.properties");
        prop.load(inS);
        inS.close();

    }

    public String getVersion()
    {
        return this.version;
    }
	
	static {
		reloadProperties();
	}
	public static reloadProperties(){
	try{
          InputStream inputStream = GenerateJSONSchema.class.getClassLoader().getResourceAsStream("users");
          if (inputStream == null) {
            throw new FileNotFoundException("property file 'users' not found in the classpath");
          }
          authUsersProp.load(inputStream);
          inputStream.close();
          authUsers = new Hashtable();
          authPass = new Hashtable();
          logger.debug "authUsersProp loaded ${authUsersProp}"
        }catch(Exception e){
          logger.error "Error configuring authentication users: ${e.getMessage()}; ${e.getStackTrace()}"
        }
		
	 try{
          String aux = MyInterfaceBrokerProperties.getPropertyValue('authentication.schema.method');
          //logger.debug "authentication.method = ${aux}"		  
          if(aux != null || aux != ''){
            String[] parts = aux.split("\\|",-1);
            parts?.each{
              def auth = it.split(":",-1);			  
			  if(auth.length == 2)
              	authMethods[auth[0]] = auth[1];				 
            }
          }
        }catch(Exception e){
          logger.error "Error configuring authentication methods: ${e.getMessage()}; ${e.getStackTrace()}"
        }
	}
	
    public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception {
	
		if(authenticateUser(message)){
		
        String encoding = "ISO-8859-1"; //Def encoding
        String strategy 	= (String)message.get("strategy");
        String generateSample = (String)message.get("generateSample");
        String format = (String)message.get("format");
        Hashtable auxMap = new Hashtable();
        def json = ""
        if(strategy == null || strategy.trim() == ""){
            errorCode = 1;
            errorMessage = "Strategy name should be provided";
        }else{
            json = getJSONSchema(strategy, auxMap, message,generateSample=='true', format=='swagger')
            reloadStrategies(strategy)
        }

        if(errorCode > 0){
            //message.put("data","<init><strategy>${message.get('strategy')}</strategy><result>ERR_${errorCode}: ${errorMessage}</result></init>".toString())
            //message.put("""{"error":"errorCode","errorMessage:"${errorMessage}"}""") => Bug
			message.put("data",'{\"error\":'+errorCode+',\"errorMessage\":\"'+errorMessage+'\"}')
        }else{
            message.put("data",json.toString());

        }
        HashMap headers = new HashMap();
        headers['Content-Type']= "application/json; charset=${encoding}".toString();
        headers['Cache-Control']= "no-cache";

        message.put("http.response.headers",headers);

        message.put("contentType","application/json; charset=${encoding}".toString())
        errorCode = 0;
        errorMessage = "";
        return TR_SUCCESS;
		}
		else{
			message.put("error.code",4)
			message.put("error.message","You are not authorized to call this service.")
		}
    }
	
	private boolean authenticateUser(final Message message){
			
		String user="";
		String pass="";
		String alias=message['strategy'];				
		
		if(authMethods[alias] == null || authMethods[alias] == '' || authMethods[alias].toUpperCase()  == "NONE"){
          return true;
        }
		else if(authMethods[alias].toUpperCase() == "ENABLED"){
					
		  def authentication=message['authentication']!=null?message['authentication']:null;
		  
		   if(authentication==null || authentication == ""){			
             return false;
			}
			 byte[] encoded = Base64.decodeBase64(authentication.getBytes());     
			 def userPass = new String(encoded);			
			 user = userPass.split(":",-1)[0]
			 pass = userPass.split(":",-1)[1]                 
       }
       
       if(authUsers[alias] == null){
          //if(authUsersProp.getProperty(alias+'.user')!=null){
             //authUsers[alias] = CryptoUtils.decrypt(authUsersProp.getProperty(alias+'.user'),keyfile);
             authUsers[alias] = CryptoUtils.decrypt(getOrDefault(message, alias,"user"),keyfile);
          //}
       }
       if(authPass[alias] == null){
          //if(authUsersProp.getProperty(alias+'.pass')!=null){
             authPass[alias] = CryptoUtils.decrypt(getOrDefault(message, alias,"pass"),keyfile);
          //}
       }
       
	   if(user == authUsers[alias] &&
          pass == authPass[alias]){
		  return true;
       }else{
	      return false;
       }
		
	}

    //GIB-52: Created to get the wildcard user and pass
    private String getOrDefault(Message message, String alias, String prop){
        logger.debug message.get("callID") + "getOrDefault: ${alias} ${prop}"
        if(authUsersProp.getProperty(alias+'.'+prop) != null && authUsersProp.getProperty(alias+'.'+prop) != ""){
          logger.debug message.get("callID") + "Exists - returning value ${authUsersProp.getProperty(alias+'.'+prop)}"
          return authUsersProp.getProperty(alias+'.'+prop)
        }else{
          logger.debug message.get("callID") + "Does not exists - returning wildcard ${authUsersProp.getProperty('*.'+prop)}"
          return authUsersProp.getProperty('*.'+prop)
        }
    }

    private String getAuthMethod(String alias){
        String type = "NONE"
        try{
            String aux = MyInterfaceBrokerProperties.getPropertyValue('authentication.method');

            if(aux != null || aux != ''){
                String[] parts = aux.split("\\|",-1);
                parts?.each{
                    def auth = it.split(":",-1);
                    if(auth[0]==alias){
                        type = auth[1];
                    }
                }
            }
        }catch(Exception e){}
        return type;
    }


    private String getJSONSchema(String alias,Hashtable ret, Message message, boolean generateSample, boolean swagger){
        int i = 1;
        String dirName;
        File fIn = null;
        //logger.debug(prop.stringPropertyNames().toString())  ;
        while(true){
            dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i);
            //logger.debug("Dirname = ${dirName}");
            logger.debug("Looking for  " +  dirName+"/"+alias+".ser") ;
            if(dirName == null){
                break;
            }
            fIn = new File(dirName+"/"+alias+".ser");
            //logger.debug("File opened:" +fIn.getAbsolutePath());
            if(fIn.exists()){
                break;
            }
            fIn = null;
            i++;
        }

        if(fIn!=null){
            def jsonSchema = ""
            def swaggerContent = ""
            def faker = null;
            def gen = null
            def swaggerGen = null;
            if(generateJSON){
                //Generar JSONSChema
                def jsonVersion = MyInterfaceBrokerProperties.getPropertyValue("json."+alias+".format")
                logger.debug "Version of the JSON generator for $alias = $jsonVersion"
                if(jsonVersion == "2"){
                    faker = new JSONFakerV2()
                    gen = new JSONCodeGenV2(fIn, alias)
                }else {
                    faker = new JSONFaker()
                    gen = new JSONCodeGen(fIn, alias)
                }
                def jsonContent = JsonOutput.prettyPrint(gen.generate("UTF-8"))
                logger.debug "jsonContent > $jsonContent"
                def jsonSchemaFile = new File(dirName + "/" + alias + ".json");
                jsonSchemaFile.write(jsonContent)
                if (swagger) {
                    try {
                        if(jsonVersion == "2"){
                            swaggerGen = new SISSwaggerGenV2(jsonContent, alias, MyInterfaceBrokerProperties.getPropertyValue("swagger.host"))
                        }else{
                            swaggerGen = new SISSwaggerGen(jsonContent, alias, MyInterfaceBrokerProperties.getPropertyValue("swagger.host"))
                        }

                        swaggerContent = JsonOutput.prettyPrint(swaggerGen.generateSwagger())
                        new File(dirName + "/" + alias + ".swagger").write(swaggerContent)
                    } catch (Exception e) {
                        logger.error "Error generating Swagger specification: $e"
                        logger.error "${e.getStackTrace()}"
                    }
                }

                def fakeMessage = ""
                try {
                    logger.debug "Generating sample message with ${faker.class}"
                    fakeMessage = JsonOutput.prettyPrint(faker.getFakeMessage(jsonContent, alias))
                    new File(dirName + "/" + alias + "_sample.json").write(fakeMessage)
                } catch (Exception e) {
                    logger.error "Error generating the fake message: $e: ${e.stackTrace}"

                }
                if (generateSample) {
                    return fakeMessage
                } else {
                    if (swagger) {
                        return swaggerContent
                    } else {
                        return jsonContent
                    }
                }
            }else{
                def jsonSchemaFile = new File(dirName+"/"+alias+".json");

                if(jsonSchemaFile.exists()) {
                    def jsonContent = jsonSchemaFile.text
                    if(swagger){
                        try {
                            if(jsonVersion == "2"){
                                swaggerGen = new SISSwaggerGenV2(jsonContent, alias, MyInterfaceBrokerProperties.getPropertyValue("swagger.host"))
                            }else{
                                swaggerGen = new SISSwaggerGen(jsonContent, alias, MyInterfaceBrokerProperties.getPropertyValue("swagger.host"))
                            }
                            swaggerContent = JsonOutput.prettyPrint(swaggerGen.generateSwagger())
                            new File(dirName + "/" + alias + ".swagger").write(swaggerContent)
                        }catch(Exception e){
                            logger.error "Error generating Swagger specification: $e"
                            logger.error "${e.getStackTrace()}"
                        }
                    }
                    def fakeMessage = ""
                    try {
                        fakeMessage = JsonOutput.prettyPrint(faker.getFakeMessage(jsonContent, alias))
                        new File(dirName + "/" + alias + "_sample.json").write(fakeMessage)
                    }catch(Exception e){
                        logger.error "Error generating the fake message: $e"
                    }
                    if(generateSample){
                        return fakeMessage
                    }else {
                        if(swagger){
                            return swaggerContent
                        }else {
                            return jsonContent
                        }
                    }
                }else{
                    logger.error "No JSON SCHEMA file found in paths";
                    errorCode = 5;
                    errorMessage = "No JSON SCHEMA file found in paths";
                    return null;
                }
            }


        }else{
            logger.error "No SER file found in paths";
            errorCode = 5;
            errorMessage = "No SER file found in paths";
            return null;
        }

    }



    private void reloadStrategies(String strategy){
        //Check for translated alias
        List  aliasTranslation = new ArrayList();
        String auxTrans = MyInterfaceBrokerProperties.getPropertyValue('alias.translation');
        //logger.debug "auxTrans = #${auxTrans}#"
        if(auxTrans != null && auxTrans.trim() != '' && auxTrans!='${alias.translation}'){
            String[] parts = auxTrans.split(";",-1);
            for(int i=0;i<parts.length;i++){
                if(parts[i] != null && parts[i].split() != ''){
                    String[] trans = parts[i].split(":",-1)
                    if(trans[1] == strategy){
                        aliasTranslation.add(trans[0])
                    }
                }
            }
        }
        if(aliasTranslation.size()==0){
            aliasTranslation.add(strategy)
        }
        //logger.debug "ALIAS TRANS: ${aliasTranslation}";
        aliasTranslation.each{ alias ->
            try{
                logger.debug "Reseting cache for ${alias}"
                if(jmx) JMXHelper.resetCache(alias);
                if(DAAgnostic.managementInterface.isStrategyLoaded(alias)){
                    DAAgnostic.managementInterface.unloadStrategy(alias);
                }
                def ret = DAAgnostic.managementInterface.loadStrategy(alias)
                logger.debug "Strategy reloaded with code ${ret}"
            }catch(Exception e){
                logger.error "Error reloading strategies ${e}"
            }
        }
    }
}     