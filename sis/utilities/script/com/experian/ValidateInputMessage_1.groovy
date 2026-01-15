package com.experian;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;
// Logger
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import java.io.*;
import java.util.*;
import com.experian.util.*;
import com.experian.jmx.*;

import javax.xml.transform.Source;
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Schema
import javax.xml.validation.Validator

import groovy.util.slurpersupport.GPathResult;
import org.apache.commons.codec.binary.Base64;

import groovy.jmx.builder.JmxBuilder;

/**
 *   ValidateInputMessage
 *   This class receives the SOAP input message, and perfoms formal 
 *   and specific validations based on the alias of the strategy.
 *   <p>
 *   Based on the alias, once the XML is well-formed, it looks for
 *   the alias_st.xsd on the paths configured in the decisionagent.properties
 *   file.     
 *
 *   @version 2.2
 *   @author David Teruel 
 *   @date 25/08/2014
 *
 */        
public class ValidateInputMessage implements GroovyComponent<Message> 
{    
    protected static final ExpLogger  logger       = new ExpLogger(this); 
    protected static final String     TR_SUCCESS   = "success";
    protected static       Hashtable  XSD_cache    = new Hashtable();
    protected static       Hashtable  File_cache   = new Hashtable();
    protected static       Properties prop = new Properties();
    protected static       Hashtable  aliasTranslation = new Hashtable();
    protected static       NBSMManager manager = new NBSMManager();
    protected static       JmxBuilder jmxBuilder = new JmxBuilder();
    protected static       Hashtable  authMethods = new Hashtable();
    protected static       Properties authUsersProp = new Properties();
    protected static       Hashtable  authUsers = new Hashtable();
    protected static       Hashtable  authPass = new Hashtable();
    protected static       String     keyfile = MyInterfaceBrokerProperties.getPropertyValue('authentication.keyfile');
    protected static       boolean    validate = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('validation'));
	protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
    protected static String version = "2.11";

	public ValidateInputMessage()
	{
		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);
		
		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);

		if(jmx)
		{
			jmxBuilder.export
			{
	            bean(target:manager, name:"jmx.builder:type=NBSMManager");
	        }
        }		
        reloadProperties();
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
    public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception 
	{
        if(jmx && JMXHelper.getReloadPropertiesVal())
		{
           reloadProperties();
           JMXHelper.setReloadPropertiesVal(false);
        } 
        //logger.debug(message.toString());
        message.put("error.code",0)
        message.put("error.message","");
        
        try
		{
          message.put("callID","" + (new java.util.Date()).getTime() + ": ");
          message.put('initMilis',System.currentTimeMillis());
          
          
          /*Init TimeAgent*/
          TimeAgent tagent = new TimeAgent(message.get("callID"));
          tagent.init()
          message.put('timeAgent',tagent)
          
          String xml = (String)message.get("data");
          //message.get('timeAgent').addPoint("GETDATA")         
          if(xml == null || xml.trim() == ''){
            message.put("error.code",1)
            message.put("error.message","Input XML can't be empty")
          }else if (xml.trim() == 'LB') {
            message.put("error.code",1000)     // Load banlancer call
          }else{
             
             //logger.info "Incoming message: ${xml}"
             
             XmlSlurper sl = XmlSlurperCache.getInstance().getXmlSlurper();

             // Validation of soap format and retreival of ALIAS
             String alias = null
             
             try{
                logger.debug "Type ${message.get('REST')=='Y'?'REST':'SOAP'}"
                if("Y" == message.get("REST")){
             
                   if(xml.indexOf("xmlns:xsi") == -1){
                       //logger.debug "There's no xmlns:xsi ---> Replacing"
                       xml = xml.replaceAll('<DAXMLDocument','<DAXMLDocument xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"')
                       //logger.debug "${xmlQueryDA}"
                    }
                    //logger.debug "Setting xmlDocument in message";
                    message.put('xmlDocument',xml);
                    
                    def  DAXMLDocument =  sl.parseText(xml)
                    // Get alias and validate
                    alias = DAXMLDocument.OCONTROL.ALIAS.text()
                    
                    logger.info "Alias = ${alias}"
                    message.put("alias",alias);
                    
                    logger.info "Translated alias = ${aliasTranslation[alias]}"
                    if(aliasTranslation[alias] != null){
                        message.put("transAlias",aliasTranslation[alias]);
                    }
                    if(alias==null || alias==''){
                      message.put("error.code",3)
                      message.put("error.message","Input message is not valid. Error returned: No ALIAS element can be found.")
                    }else if(!validateUser(message, DAXMLDocument, alias )){
                       message.put("error.code",4)
                       message.put("error.message","You are not authorized to call this service.")
                    }else{
                      if(validate){  
                        if(message['transAlias'] != null && message['transAlias'].trim() != ''){
                          validateQueryDA(xml,message,message['transAlias']);
                        }else{
                          validateQueryDA(xml,message,alias);
                        }
                      }
                    }
                }else{
                  def soap = sl.parseText(xml).declareNamespace(soap: 'http://www.w3.org/2003/05/soap-envelope');
                  if(soap.name().toLowerCase()!= "envelope"){
                    throw new Exception("SOAP format is invalid. envelope element missed")
                  }
                  //logger.info "@${soap.children()[0]?.name()}@"
                  //logger.info "@${soap.children()[1]?.name()}@" 
                  if(soap.children()[0].name().toLowerCase() != "body" && soap.children()[1].name().toLowerCase() != "body"){
                     throw new Exception("SOAP format is invalid. envelope/body element missed")
                  }
  
                  xml = removeNamespace(xml, message);
                  //logger.debug "After removing namespace ${xml}";
                  // Retreive content of XML message
                  def xmlInit = xml.indexOf("<DAXMLDocument");
                  def xmlEnd = xml.lastIndexOf("</DAXMLDocument>")+16;
                  
                  //logger.debug "${xmlInit} - ${xmlEnd}";
                  if (xmlInit == -1 || xmlEnd == 15){
                    
                  
                    throw new Exception("DAXMLDocument node cannot be found.")
                  
                  }
                  
                  
                  message.put("soap_pre",xml.substring(0,xmlInit));
                  message.put("soap_post",xml.substring(xmlEnd));
                  
                  
                  def xmlQueryDA = xml.substring(xmlInit,xmlEnd);
                  //logger.debug "Searching for xmlns:xsi --> ${xmlQueryDA.indexOf('xmlns:xsi')}"
                  if(xmlQueryDA.indexOf("xmlns:xsi") == -1){
                     //logger.debug "There's no xmlns:xsi ---> Replacing"
                     xmlQueryDA = xmlQueryDA.replaceAll('<DAXMLDocument','<DAXMLDocument xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"')
                     //logger.debug "${xmlQueryDA}"
                  }
                  //logger.debug "Setting xmlDocument in message";
                  message.put('xmlDocument',xmlQueryDA);
  
				  // Get alias and validate
                  alias = soap.'soap:body'.DAXMLDocument.OCONTROL.ALIAS.text()
                  if(alias==null  || alias=='') {
                    alias = soap.Body.DAXMLDocument.OCONTROL.ALIAS.text().trim();
                  }
				  if(alias==null  || alias=='') {
                    alias = soap.body.DAXMLDocument.OCONTROL.ALIAS.text().trim();
                  }
                  
                  logger.info "Alias = ${alias}"
                  message.put("alias",alias);
                  
                  logger.info "Translated alias = ${aliasTranslation[alias]}"
                  if(aliasTranslation[alias] != null){
                      message.put("transAlias",aliasTranslation[alias]);
                  }
                  if(alias==null || alias==''){
                    message.put("error.code",3)
                    message.put("error.message","Input message is not valid. Error returned: No ALIAS element can be found.")
                  }else if(!validateUser(message, soap, alias )){
                     message.put("error.code",4)
                     message.put("error.message","You are not authorized to call this service.")
                  }else{  
                    if(validate){
                      if(message['transAlias'] != null && message['transAlias'].trim() != ''){
                        validateQueryDA(xmlQueryDA,message,message['transAlias']);
                      }else{
                        validateQueryDA(xmlQueryDA,message,alias);
                      }
                    }
                  }
                }
             }catch(Exception e){
                message.put("error.code",3)
                message.put("error.message","Input message is not valid. Error returned: ${e.getMessage()}");
                logger.error message.get("callID") + e.getMessage();
                logger.error message.get("callID") + "Message received by calling system: "+xml;
             }
             
             XmlSlurperCache.getInstance().free(sl);
             message.get('timeAgent').addPoint("VALIDATE")    
             message.put('daProp',prop);
          }
        }catch(Exception e){
            logger.error message.get("callID") + "An unexpected error has happened. Error returned: " + e.getMessage();
            logger.error message.get("callID") + "Message received by calling system: "+xml;
            message.put("error.code",10)
            message.put("error.message","An unexpected error has happened. Error returned: " + e.getMessage())
        }
        //logger.debug("END OF VALIDATION")
        message.get('timeAgent').addPoint("ENDVALIDATOR")    
        return TR_SUCCESS;
    }
    
    /**
     *  Performs the validation of the XML message based on the XSD for the strategy
     *  
     *  @param xml The XML data to be validated
     *  @param message The container
     *  @param alias The alias of the strategy
     *  @exception Exception
     *       
     */                   
    private boolean validateQueryDA(String xml, Message message, String alias) {
      Validator valid = null
      boolean result = false
      try{
        Source source = new StreamSource(new StringReader(xml));
        //message.get('timeAgent').addPoint("SOURCE")    
        valid = getValidator(alias,message);
        //message.get('timeAgent').addPoint("GETVALIDATOR")    
        if(valid != null){
          valid.validate(source);
          //message.get('timeAgent').addPoint("VALI")    
          result = true;
        }else{
          logger.error(message.get("callID") + "NO XSD FOUND")
          logger.error message.get("callID") + "DAXMLDocument message received by calling system: "+xml;
          message.put("error.code",2)
          message.put("error.message","No XSD found for alias " + alias)
        }
      
      }catch(Exception e){
        logger.error message.get("callID") + "Error while validating: " + e.getMessage() +  ": ${e.getStackTrace()}";
        logger.error message.get("callID") + "DAXMLDocument message received by calling system: "+xml;
        message.put("error.code",3)
        message.put("error.message","Input message is not valid. Error returned: " + e.getMessage())
      }
      if (valid != null)
        freeValidator(valid, alias);
      return result;
    }
    
    /**
     *  Based on the paths configured in decisionagent.properties, it looks for
     *  the alias_st.xsd file. The file is cached for future use.
     *  
     *  @param alias The alias of the strategy
     *  @param message The container                    
     *
     */              
    private File getXSD(String alias, Message message){
       def cleanCache = jmx?JMXHelper.needToResetCache(alias,NBSMManager.CACHE_FILE):false; 
       logger.debug "File Clean cache ${cleanCache}"
       
       if(cleanCache){
          logger.debug "File_cache = ${File_cache}"
          File_cache?.remove(alias)
          logger.debug "File_cache after = ${File_cache}"
          JMXHelper.cacheClenDone(alias,NBSMManager.CACHE_FILE)
       }
       
       if(File_cache[alias] != null){
          return File_cache[alias] 
       }
       int i = 1;
       String dirName;
       File fIn = null;
       while(true){
            dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i)?.trim();
            //logger.debug (message.get("callID") + "Searchin in directory " +  dirName)
            if(dirName == null || dirName == ''){
              break;
            }                                   
            fIn = new File(dirName+"/"+alias+"_st.xsd")
            if(fIn.exists()){
              //logger.info(message.get("callID") + "XSD is " + dirName+"/"+alias+"_st.xsd");
              break;
            }else{
              fIn = null;
            }
            i++;
       }
       if(fIn != null)
        File_cache[alias] = fIn;
       return fIn;
       
    }
    
    /**
     *  Creates the XSD validator for the strategy.  This validator is cached.
     *
     *  @param alias The alias of the strategy
     *  @param message The container                    
     *
     */                        
    private synchronized Validator getValidator(String alias, Message message) throws Exception{
      
      //message.get('timeAgent').addPoint("INITGETVALID") 
      def cleanCache = jmx?JMXHelper.needToResetCache(alias,NBSMManager.CACHE_XSD_VAL):false; 
      logger.debug "Clean cache ${cleanCache}"
      
      if(XSD_cache[alias] == null || cleanCache){
        logger.debug "Cleaning XSD cache"
        XSD_cache[alias] = new Stack();
        if(cleanCache)
          JMXHelper.cacheClenDone(alias,NBSMManager.CACHE_XSD_VAL)
        
      }
      if(XSD_cache[alias].empty()){
          SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
          File xsd = getXSD(alias,message)
          
          if(xsd!=null){
              Schema schema	= factory.newSchema(new StreamSource(xsd));
        	    Validator valid 		= schema.newValidator(); 
              //message.get('timeAgent').addPoint("CREATEVALID")    
              return valid;
          }else{
            return null;
          }
      }else{
          //message.get('timeAgent').addPoint("CACHEVALID")    
          return (Validator)XSD_cache[alias].pop();
          
      }
    
    }
    
    /**
     *  Releases the validator and includes it in the cache
     *
     *  @param valid The validator to be set free
     *  @param alias The alias of the strategy              
     *
     */   
    private synchronized void freeValidator(Validator valid, String alias){
        XSD_cache[alias].push(valid);
        //logger.info  "Cache size: " + XSD_cache[alias].size()
    }
    
    
    private String removeNamespace(String xml, Message message){
      if(xml.indexOf("<DAXMLDocument") != -1) return xml;
      //logger.debug "Hay que quitar el namespace";
      def initInt = xml.indexOf(":DAXMLDocument")-1;
      //logger.debug "initInt = ${initInt}";
      String namespace = "";
      while(xml.charAt(initInt) != '<'){
        namespace = new String(xml.charAt(initInt--)) + namespace;
        //logger.debug "Namespace = ${namespace} and initInt = ${initInt}"; 
        message.put("namespace",namespace);
      }
      
      String xmlDecStr = "xmlns:${namespace}=\"";
      def xmlDecInit = xml.indexOf(xmlDecStr);
      if(xmlDecInit != -1){
        def xmlDec =  xml.substring(xmlDecInit+xmlDecStr.length());
        xmlDec = xmlDec.substring(0,xmlDec.indexOf("\""));
        message.put("namespaceDec",xmlDec);
      }
      
      return xml.replaceAll(namespace+":DAXMLDocument","DAXMLDocument");
    }
    
    private boolean validateUser(Message message, GPathResult xml, String alias ){
	
	   return true; 	
       logger.debug message.get("callID") + "Auth method ${ authMethods[alias]}" 
       String user = "";
       String pass = "";
       //GIB-52: Change BASIC to be default
       if(authMethods[alias] == null || authMethods[alias] == '' || authMethods[alias].toUpperCase() == "BASIC"){
         def headers = message.get('http.request.headers')
         logger.debug message.get("callID") + " headers ${headers}"
         def auth = headers.containsKey('Authorization')?headers['Authorization']:headers['authorization'];
         if(auth==null || auth == ""){
          return false;
         }
         auth = auth.substring("Basic ".length());
         logger.debug message.get("callID") + " Auth string ${auth}"
         byte[] encoded = Base64.decodeBase64(auth.getBytes());     
         def userPass = new String(encoded);
         logger.debug message.get("callID") + " After decoded ${userPass}"
         user = userPass.split(":",-1)[0]
         pass = userPass.split(":",-1)[1]
         logger.debug message.get("callID") + " Basic auth: ${user}/${pass}"
         
       }else if(authMethods[alias].toUpperCase()  == "NONE"){
          return true;
       }else if(authMethods[alias].toUpperCase() == "SOAP" && message.get("REST")!="Y"){
         
	         user = xml.Header.Security.UsernameToken.Username.text();
           pass = xml.Header.Security.UsernameToken.Password.text();
         
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

       logger.debug message.get("callID") + " Validating against: ${authUsers[alias]} / ${authPass[alias]}"

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