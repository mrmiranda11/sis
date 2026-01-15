package com.experian

import com.experian.eda.decisionagent.interfaces.nt.NTJSEMJSONInterface
import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import com.experian.jmx.JMXHelper
import com.experian.util.*
import groovy.json.JsonSlurper
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils


/**
 *   DoCallDA
 *   This class performs the call to the DA. The steps it does are the following
 *   <ol>
 *   <li>The message must be valid. So it checks if the validator ended with error. 
 *   In this case it ends.</li>
 *   <li>Based on the XSD desdcription of the strategy, it parses the XML message and
 *   builds the DataAreas for each data block.</li>
 *   <li>Calls the DA usign the Java Object Interface</li>
 *   <li>If there's no error, retreives the response data areas and ends.
 *   </ol>   
 * 
 *  @version 1.0
 *  @author David Teruel 
 */
public class DoCallDAJSON implements GroovyComponent<Message>
{

    protected static final ExpLogger  logger     = new ExpLogger(this);
    protected static final String     TR_SUCCESS = "success";
    protected static boolean DAInitialized = false;
	protected static boolean  jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
    //protected static boolean validateJSON = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('json.validate'));
    protected static       String     keyfile = MyInterfaceBrokerProperties.getPropertyValue('authentication.keyfile');
    protected static       String     encoding = MyInterfaceBrokerProperties.getPropertyValue('json.encoding');
    protected static       Properties prop = new Properties();
    protected static       Hashtable  aliasTranslation = new Hashtable();
    protected static       Hashtable  authMethods = new Hashtable();
    protected static       Properties authUsersProp = new Properties();
    protected static       Hashtable  authUsers = new Hashtable();
    protected static       Hashtable  authPass = new Hashtable();
    protected              int        errorCode = 0;
    protected              String     errorMessage = "";

    protected static String version = "2.11";
    protected static int daDefLogLevel = Integer.parseInt(MyInterfaceBrokerProperties.getPropertyValue('da.log.level'))

	public DoCallDAJSON()
	{
		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);
		
		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);
        String sDaLogLevel = InterfaceBrokerProperties.getProperty("da.log.level");
        if(sDaLogLevel == null || sDaLogLevel == "null" || sDaLogLevel.trim() == ""){
            sDaLogLevel = 0
        }
        daDefLogLevel = Integer.parseInt(sDaLogLevel)

        InputStream inS = DoBatchCallDA.class.getClassLoader().getResourceAsStream("decisionagent.properties");
        prop.load(inS);
        inS.close();

        reloadProperties()
	}
	
	public String getVersion()
	{
		return this.version;
	}
    
  /**
   *  Main method of the script
   *  
   *  @param message  
   *  @param dataMap
   *  @exception Exception
   *  @returns success String                         
   *
   */              
  public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception {
      errorCode = 0
      errorMessage = ""
      if(jmx && JMXHelper.getReloadPropertiesVal())
      {
          reloadProperties();
          JMXHelper.setReloadPropertiesVal(false);
      }
      if(encoding == null || encoding?.trim() == "")
          encoding = "UTF-8"


      /*message.put("callID","" + (new java.util.Date()).getTime() + ": ");
      TimeAgent tagent = new TimeAgent(message.get("callID"));
      tagent.init()
      message.put('timeAgent',tagent)*/
      message['timeAgent'].addPoint("IN")
      def tagent = message['timeAgent']
      def reqMsg = message['data'];

        //Validate and check request message
        // 1- Is not empty
        String retMessage = ""
        String alias =""
        if(reqMsg == null || reqMsg == "null" || reqMsg.trim() == ""){
            retMessage = """{"errorCode":"JS01","errorMessage":"Input JSON can't be empty"}"""
        }else {
            try {
                InputStream inputJSON;
                inputJSON = IOUtils.toInputStream(reqMsg, encoding);
                OutputStream outputJSON = new ByteArrayOutputStream();
                long init = System.currentTimeMillis()
                def content = new JsonSlurper().parseText(reqMsg)
                def jsonVersion = "1";
                logger.debug "version [${content.DAJSONDocument}]"
                if(content.DAJSONDocument == null){
                    jsonVersion = "2"
                }
                logger.debug "jsonVersion = $jsonVersion"
                if(jsonVersion == null || jsonVersion.trim() == "" || jsonVersion.trim() == "1")
                    alias = content.DAJSONDocument.OCONTROL.ALIAS.value.toString()
                else
                    alias = content.DAJSONDocumentV2.OCONTROL.ALIAS.value.toString()

                logger.debug "Time to get alias: ${System.currentTimeMillis() - init}: [$alias]"
                if (alias == null || alias == "null" || alias.trim() == "") {
                    errorCode = 3
                    errorMessage = "Input message is not valid. Error returned: No ALIAS element can be found."
                } else {
                    if (!validateUser(message, alias)) {
                        errorCode = 4
                        errorMessage = "You are not authorized to call this service."
                    }
                }
                if (errorCode == 0) {
                    message['timeAgent'].addPoint("PREDA")
                    def retCode = NTJSEMJSONInterface.instance().execute(inputJSON, outputJSON, encoding, false, daDefLogLevel);
                    message['timeAgent'].addPoint("POSTDA")
                    logger.debug "DA call with ret code $retCode"
                    if (retCode != 0) {
                        errorCode = retCode
                        errorMessage = "DA returned error"
                    }
                    retMessage = outputJSON.toString(encoding)
                } else {
                    retMessage = """{"errorCode":"JS0$errorCode","errorMessage":"$errorMessage"}"""
                }
            } catch (Exception e) {
                logger.error message.get("callID") + "Exception calling JSON: $e"
                logger.error message.get("callID") + "${e.getStackTrace()}"
                retMessage = """{"errorCode":"JS99","errorMessage":"Unexpected error"}"""
            }
        }
        logger.debug "Response message > $retMessage"

      HashMap headers = new HashMap();

      headers['Cache-Control']= "no-cache";

      message.put("http.response.headers",headers);
      message.put("contentType", "application/json")
      message.put('data',retMessage)

      tagent.stop("JS"+errorCode);
      tagent.trace();

      if(jmx)
      {
          JMXHelper.addexecution(alias,JMXHelper.JSON,"JS0"+errorCode,new Long(tagent.getTotalTime()));
      }
      return TR_SUCCESS;
  }




    private boolean validateUser(Message message, String alias ){
        logger.debug message.get("callID") + "Auth method ${ authMethods[alias]}"
        String user = "";
        String pass = "";
        if(authMethods[alias] == null || authMethods[alias] == '' || authMethods[alias].toUpperCase()  == "NONE"){
            return true;
        }else if(authMethods[alias].toUpperCase() == "BASIC"){
            def headers = message.get('http.request.headers')
            //logger.debug message.get("callID") + " headers ${headers}"
            def auth = headers.containsKey('Authorization')?headers['Authorization']:headers['authorization'];
            if(auth==null || auth == ""){
                return false;
            }
            auth = auth.substring("Basic ".length());
            //logger.debug message.get("callID") + " Auth string ${auth}"
            byte[] encoded = Base64.decodeBase64(auth.getBytes());
            def userPass = new String(encoded);
            logger.debug message.get("callID") + " After decoded ${userPass}"
            user = userPass.split(":",-1)[0]
            pass = userPass.split(":",-1)[1]
            logger.debug message.get("callID") + " Basic auth: ${user}/${pass}"

        }

        if(authUsers[alias] == null){
            if(authUsersProp.getProperty(alias+'.user')!=null){
                authUsers[alias] = CryptoUtils.decrypt(authUsersProp.getProperty(alias+'.user'),keyfile);
            }
        }
        if(authPass[alias] == null){
            if(authUsersProp.getProperty(alias+'.pass')!=null){
                authPass[alias] = CryptoUtils.decrypt(authUsersProp.getProperty(alias+'.pass'),keyfile);
            }
        }
        if(user == authUsers[alias] &&
                pass == authPass[alias]){
            return true;
        }else{
            return false;
        }

    }

    private static void reloadProperties(){

        InputStream inS = ValidateInputMessage.class.getClassLoader().getResourceAsStream("decisionagent.properties");
        prop.load(inS);
        inS.close();
        try{
            String auxTrans = MyInterfaceBrokerProperties.getPropertyValue('alias.translation');
            //logger.debug "auxTrans = #${auxTrans}#"
            if(auxTrans != null && auxTrans.trim() != '' && auxTrans!='${alias.translation}'){
                String[] parts = auxTrans.split(";",-1);
                for(int i=0;i<parts.length;i++){
                    if(parts[i] != null && parts[i].split() != ''){
                        String[] trans = parts[i].split(":",-1)
                        aliasTranslation[trans[0]] = trans[1];
                    }
                }
            }
            //logger.info "ALIAS TRANS: ${aliasTranslation}";
        }catch(Exception e){
            logger.error "Error creating translation table ${e.getMessage()} + ${e.getStackTrace()}";
        }


        try{
            String aux = MyInterfaceBrokerProperties.getPropertyValue('authentication.method');
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


        try{
            InputStream inputStream = ValidateInputMessage.class.getClassLoader().getResourceAsStream("users");
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
    }
    

	
}