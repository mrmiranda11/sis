package com.experian

import com.experian.damonitoring.DAVariable

import com.experian.eda.enterprise.connectivity.service.ServiceConstant
import com.experian.eda.enterprise.core.DefaultMessage
import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.enterprise.ipf.ClientApiExecutor
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import groovy.xml.*
// Logger
import com.experian.jmx.JMXHelper
import com.experian.stratman.datasources.runtime.IData
import com.experian.util.DAAgnostic
import com.experian.util.MD5
import com.experian.util.MyInterfaceBrokerProperties
import com.experian.util.SampleBuilder
import com.experian.util.XMLCodeGen
import com.experian.util.XMLCodeGenV2
import com.experian.util.XMLCodeGenV3
import com.experian.util.XMLCodeGenV4
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import java.lang.reflect.Method
import java.nio.channels.FileChannel
import org.apache.commons.codec.binary.Base64;
import com.experian.util.*;

/**
 *
 *
 *
 *
 */
public class BuildWSDL implements GroovyComponent<Message> {

    protected static final ExpLogger  logger     = new ExpLogger(this)
    protected static final String     TR_SUCCESS = "success"
    protected              int        hashCode   = 0
    protected              Properties prop = new Properties()
    protected              int        errorCode = 0
    protected              String     errorMessage = ""
    protected              String 	  fieldSeparator = InterfaceBrokerProperties.getProperty("field.separator")
    protected              String 	  wsdlHostTag = InterfaceBrokerProperties.getProperty("wsdl.host.tag")
    protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'))
    protected              boolean 	  generateXSD = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue("xsd.generate"))
    protected static String newline = System.getProperty("line.separator");
    protected static String version = "2.11";
	protected static       Hashtable  authUsers = new Hashtable();
    protected static       Hashtable  authPass = new Hashtable();	
	protected static       Hashtable  authMethods = new Hashtable();
    protected static       Properties authUsersProp = new Properties();
    protected static       String     keyfile = MyInterfaceBrokerProperties.getPropertyValue('authentication.keyfile');

    private static ClientApiExecutor cae  = new ClientApiExecutor();

    public BuildWSDL()
    {
        /* Printing checksum */
        File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
        String checksum = MD5.getMD5Checksum(groovyFile);

        logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);

        InputStream inS = DoBatchCallDA.class.getClassLoader().getResourceAsStream("decisionagent.properties");
        prop.load(inS);
        inS.close();
        List repos = new ArrayList()
        int i = 1;
        String dirName = prop.getProperty("DirectoryBasedStrategyLoader.path"+i);
        //logger.debug "dirName ==> ${dirName}"
        while(dirName != null)
        {
            repos.add(dirName);
            i++;
            dirName = prop.getProperty("DirectoryBasedStrategyLoader.path"+i);
        }
        if (InterfaceBrokerProperties.getProperty("autodeploy") == "Y")
        {
            Thread.start
                    {
                        HashMap strategies = new HashMap()
                        Thread.sleep(15000);
                        while(true)
                        {
                            try
                            {
                                repos.each
                                        {
                                            File dir = new File(it);
                                            //logger.debug ">> Repo ${dir.getCanonicalPath()}"
                                            if (dir.isDirectory() && dir.exists())
                                            {

                                                /* getting DA Variables into tenants.properties_E(Editio) file */
                                                DAVariable dv = new DAVariable();
                                                dv.printVariables(it, logger);
                                                /* End of modifications */

                                                File[] listOfFiles = dir.listFiles();
                                                listOfFiles.each
                                                        { file ->
                                                            //logger.debug "Autodeploy >> File ${file.name}"
                                                            if(file.name.endsWith(".ser"))
                                                            {
                                                                if(strategies[file.name] == null || file.lastModified() != strategies[file.name])
                                                                {  
                                                                    strategies[file.name] = file.lastModified()
                                                                    Message im = new DefaultMessage()
                                                                    def alias = file.name.replaceAll("\\.ser","")
                                                                    im.put(ServiceConstant.WORKFLOW_ID_KEY, 'getWSDL')
                                                                    im.put('strategy', file.name.replaceAll('\\.ser',''))
                                                                    im.put('reload','yes')
                                               
                                                                   // logger.debug "Calling getWSDL for ${im['strategy']}"
                                                                    Message locOutMessage = cae.callProcessFlow('getWSDL', im)

                                                                    if(locOutMessage.getException() != null){
                                                                        logger.error "Error calling getWSDL ${locOutMessage.getException().getMessage()}"
                                                                    }

                                                                }
                                                                else
                                                                {
                                                                    //logger.debug ">> Nothing to do with ${file.name}"
                                                                }
                                                            }

                                                        } //listOfFiles.each
                                            } //dir.exists
                                        } //repos.each
                            }
                            catch(Exception e)
                            {
                                logger.error "Error with the autodeploy ${e}"
                                logger.error "${e.getStackTrace()}"
                            }
                            //logger.debug "Sleep"
                            Thread.sleep(10000);
                        }//while
                    } //thread.start
        }
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
          InputStream inputStream = BuildWSDL.class.getClassLoader().getResourceAsStream("users");
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
	
        String encoding = "ISO-5589-1"; //Def encoding
        Hashtable auxMap = new Hashtable();
        String strategy 	= (String)message.get("strategy");
        logger.debug("Received strategy: " + strategy);
        String wsdl = "";
        if(strategy == null || strategy.trim() == ""){
            errorCode = 1;
            errorMessage = "Strategy name should be provided";
        }else{
            def xsd = ''
            def soapVersion = MyInterfaceBrokerProperties.getPropertyValue("wsdl.version."+strategy)
            logger.debug "SoapVersion to be generated: '$soapVersion'"
            if(soapVersion == null || soapVersion.trim() == '' || soapVersion.trim() == 'null' || (soapVersion.trim() != '1.1' && soapVersion.trim() != '1.2')){
                soapVersion = '1.2'
            }
         def legacy=1337
         def sLegacy= MyInterfaceBrokerProperties.getPropertyValue('format.xsd.'+strategy) 
         legacy = sLegacy.isInteger() ? sLegacy.toInteger() : null
         switch(legacy) {            

                    case 1: 
                        xsd = getXSDLegacy(strategy, auxMap, message, soapVersion);
                        break; 
                    case 2: 
                        xsd = getXSD(strategy, auxMap, message, soapVersion);
                        break; 
                    case 3: 
                        xsd = getXSDv3Legacy(strategy, auxMap, message, soapVersion);
                        break; 
                    case 4: 
                        xsd = getXSD4(strategy, auxMap, message, soapVersion);
                        break; 
                    default: 
                        xsd = getXSD(strategy, auxMap, message, soapVersion);
                        break; 
                }
            def authmethod = getAuthMethod(strategy);
            //logger.debug "AuthMethod for ${strategy} is ${authmethod}"
            if(xsd != null && xsd != ''){

                def pre = getPredefinition(authmethod,soapVersion)
                def post = getPostdefinition(authmethod,soapVersion)
                int aux = xsd.indexOf("<xs:schema")
                String xsd_no_head = xsd.substring(aux)
                String xsd_xml_head = xsd.substring(0,xsd.indexOf('?>')+2)
                String encAux =  xsd_xml_head.substring(xsd_xml_head.indexOf("encoding=")+9)
                if(encAux.length()>0){
                    encoding = encAux.replaceAll('"','').replaceAll('\\?','').replaceAll('>','').trim();
                }

                wsdl = xsd_xml_head + pre + xsd_no_head + post;
                HashMap headers = new HashMap();
                headers['Content-Type']= "application/xml; charset=${encoding}".toString();
                headers['Cache-Control']= "no-cache";

                message.put("http.response.headers",headers);

                message.put("contentType","application/xml; charset=${encoding}".toString())

            }
        }

        if(errorCode > 0){
            if(message.get("REST")!="Y"){
                message.put("data","ERR_"+errorCode+": "+ errorMessage);
            }else{
                message.put("data","<init><strategy>${message.get('strategy')}</strategy><result>ERR_${errorCode}: ${errorMessage}</result></init>".toString())
            }
        }else{
            if(message.get("REST")!= "Y"){
                FileWriter fw = new FileWriter(new File(auxMap['dirName']+"/"+strategy+".wsdl"));
                fw.write(wsdl.toString());
                fw.close();
                message.put("data",wsdl.toString());
            }else{
                message.put("data","<init><strategy>${message.get('strategy')}</strategy><result>OK</result></init>".toString());
            }

        }
        errorCode = 0;
        errorMessage = "";

        if(message['reload'] == null || message['reload']=='yes') {
            //logger.debug "Calling reloadStrategies"
            reloadStrategies(strategy)
        }

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
		String alias=message['wsdl'];	
		
		logger.debug "authMethods: $authMethods"
		
		if(message['reload']=='yes' || (authMethods[alias] == null || authMethods[alias] == '' || authMethods[alias]?.toUpperCase()  == "NONE")){
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


    private String getPredefinition(String authmetod, String version){


        String pre = "";
        if(version == '1.2') {
            pre += '<wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/" xmlns:http="http://schemas.xmlsoap.org/wsdl/http/" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/" xmlns:mime="http://schemas.xmlsoap.org/wsdl/mime/" xmlns:tns="http://tempuri.org" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
        }else{
            pre += '<wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:http="http://schemas.xmlsoap.org/wsdl/http/" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/" xmlns:mime="http://schemas.xmlsoap.org/wsdl/mime/" xmlns:tns="http://tempuri.org" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
        }
        if(authmetod == 'SOAP'){
            pre += ' xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" xmlns:wsp="http://www.w3.org/ns/ws-policy"'
        }
        pre += ' targetNamespace=\"http://tempuri.org\">'
        pre += '         <wsdl:types>'
        return pre
    }


    private String getPostdefinition(String authmetod, String version){


        String post = "</wsdl:types>"

        if(version == '1.2') {

            post += "   	<wsdl:message name=\"QueryDARequest\">";
            post += "   		<wsdl:part name=\"queryDA\" element=\"DAXMLDocument\"/>";
            post += "   	</wsdl:message> ";
            post += "   	<wsdl:message name=\"QueryDAResponse\"> ";
            post += "   		<wsdl:part name=\"responseDA\" element=\"DAXMLDocument\"/>";
            post += "   	</wsdl:message>";
            post += "   	<wsdl:portType name=\"QueryDA\"> ";
            post += "   		<wsdl:operation name=\"queryDA\"> ";
            post += "   			<wsdl:input message=\"tns:QueryDARequest\"/> ";
            post += "   			<wsdl:output message=\"tns:QueryDAResponse\"/> ";
            post += "   		</wsdl:operation> ";
            post += "   	</wsdl:portType>";
            post += "   	<wsdl:binding name=\"QueryDASoap12\" type=\"tns:QueryDA\">";
            post += "   		<soap12:binding style=\"document\" transport=\"http://schemas.xmlsoap.org/soap/http\"/> ";
            post += "   		<wsdl:operation name=\"queryDA\"> ";
            post += "   			<soap12:operation soapAction=\"\"/> ";
            post += "   			<wsdl:input>";
            post += "   				<soap12:body use=\"literal\"/>  ";
            post += "   			</wsdl:input>";
            post += "   			<wsdl:output> ";
            post += "   				<soap12:body use=\"literal\"/> ";
            post += "   			</wsdl:output>";
            post += "   		</wsdl:operation>";
            post += "   	</wsdl:binding>";
            post += "   	<wsdl:service name=\"QueryDAService\">";
            post += "   		<wsdl:port name=\"QueryDASoap12\" binding=\"tns:QueryDASoap12\">";
            post += "   			<soap12:address location=\"http://${wsdlHostTag}/DAService\"/>";
            post += "   		</wsdl:port>";
            post += "   	</wsdl:service> ";
            post += getBasicAuthentication(authmetod)
            post += "   </wsdl:definitions> ";
        }else{
            post += "   	<wsdl:message name=\"QueryDARequest\">";
            post += "   		<wsdl:part name=\"queryDA\" element=\"DAXMLDocument\"/>";
            post += "   	</wsdl:message> ";
            post += "   	<wsdl:message name=\"QueryDAResponse\"> ";
            post += "   		<wsdl:part name=\"responseDA\" element=\"DAXMLDocument\"/>";
            post += "   	</wsdl:message>";
            post += "   	<wsdl:portType name=\"QueryDA\"> ";
            post += "   		<wsdl:operation name=\"queryDA\"> ";
            post += "   			<wsdl:input message=\"tns:QueryDARequest\"/> ";
            post += "   			<wsdl:output message=\"tns:QueryDAResponse\"/> ";
            post += "   		</wsdl:operation> ";
            post += "   	</wsdl:portType>";
            post += "   	<wsdl:binding name=\"QueryDASoap\" type=\"tns:QueryDA\">";
            post += "   		<soap:binding style=\"document\" transport=\"http://schemas.xmlsoap.org/soap/http\"/> ";
            post += "   		<wsdl:operation name=\"queryDA\"> ";
            post += "   			<soap:operation soapAction=\"\"/> ";
            post += "   			<wsdl:input>";
            post += "   				<soap:body use=\"literal\"/>  ";
            post += "   			</wsdl:input>";
            post += "   			<wsdl:output> ";
            post += "   				<soap:body use=\"literal\"/> ";
            post += "   			</wsdl:output>";
            post += "   		</wsdl:operation>";
            post += "   	</wsdl:binding>";
            post += "   	<wsdl:service name=\"QueryDAService\">";
            post += "   		<wsdl:port name=\"QueryDASoap\" binding=\"tns:QueryDASoap\">";
            post += "   			<soap:address location=\"http://${wsdlHostTag}/DAService\"/>";
            post += "   		</wsdl:port>";
            post += "   	</wsdl:service> ";
            post += getBasicAuthentication(authmetod)
            post += "   </wsdl:definitions> ";
        }
        return post
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

    private String getBasicAuthentication(String authmetod){
        String ret = "";

        if(authmetod == "SOAP"){
            ret += '<wsp:Policy wsu:Id="UsernamePolicy">\n'
            ret += '<wsp:ExactlyOne>\n'
            ret += '	<wsp:All>\n'
            ret += '		<sp:SupportingTokens>\n'
            ret += '			<wsp:Policy>\n'
            ret += '				<sp:UsernameToken sp:IncludeToken="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702/IncludeToken/AlwaysToRecipient">\n'
            ret += '					<wsp:Policy>\n'
            ret += '						<sp:WssUsernameToken10/>\n'
            ret += '					</wsp:Policy>\n'
            ret += '				</sp:UsernameToken>\n'
            ret += '			</wsp:Policy>\n'
            ret += '		</sp:SupportingTokens>\n'
            ret += '	</wsp:All>\n'
            ret += '</wsp:ExactlyOne>\n'
            ret += '</wsp:Policy>\n'
        }

        return ret;

    }

   private String getXSD4(String alias,Hashtable ret, Message message, String version){
       logger.debug("Inside getXSD4")
        int i = 1;
        String dirName;
        File fIn = null;
        //logger.debug(prop.stringPropertyNames().toString())  ;
        while(true){
            dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i);
            //logger.debug("Dirname = ${dirName}");
            //logger.debug("Looking for  " +  dirName+"/"+alias+".xsd") ;
            if(dirName == null){
                break;
            }
            fIn = new File(dirName+"/"+alias+".ser");
            if(fIn.exists()){
                break;
            }
            fIn = null;
            i++;
        }
        File xsdFile = null;
        if(fIn != null){
            def jsonSchema = "";
            if(generateXSD){
                XMLCodeGenV4 gen = new XMLCodeGenV4(fIn,alias)
                def xsdContent = gen.generate("UTF-8")
                logger.debug "xsdContent > $xsdContent"
                xsdFile = new File(dirName+"/"+alias+".xsd");
                xsdFile.write(xsdContent)
            }else{
                xsdFile = new File(dirName+"/"+alias+".xsd");
            }
        }

        if(xsdFile!=null && xsdFile.exists()){

            String fileData  = xsdFile.text;
            //NEW Dadate is now the only DA type... writeNormalAtomicCharc
            String xsd_mod = fileData.toString().replaceAll('type="DAdate"','type="xs:date" nillable="true"')
            //Should leave those now ? We don't anymore have the element type in the root <element> type is now only present in <xs:element name="data_type" data_type and <xs:restriction


            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="text"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="numeric"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="date"/>','')
            
           //!!!?!?!?!?!?! DaDate is again special check if I need to add simpletype nadanada writeAtomicCharcDynamicArray
            xsd_mod = xsd_mod.replaceAll('type="DADynamicdatearray" minOccurs="0"/>','minOccurs="0"><xs:complexType><xs:sequence><xs:element name="item" type="xs:date" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>')
           
            xsd_mod = xsd_mod.replaceAll('data source -->'+newline+'\\s+','data source -->')
            xsd_mod = xsd_mod.replaceAll('data source --><xs:element name','data source -->'+newline+'<xs:element minOccurs="0" name')
  
            xsd_mod = xsd_mod.replaceAll('<xs:element name="element" type="DAnumericarrayelement"','<xs:element name="item" type="xs:decimal"')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="element" type="DAstringarrayelement"','<xs:element name="item" type="xs:string"')

            xsd_mod = xsd_mod.replaceAll('type="DADynamicstringarray" minOccurs="0"/>','minOccurs="0"><xs:complexType><xs:sequence><xs:element name="item" type="xs:string" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>')
            xsd_mod = xsd_mod.replaceAll('type="DADynamicnumericarray" minOccurs="0"/>','minOccurs="0"><xs:complexType><xs:sequence><xs:element name="item" type="xs:decimal" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>')
            xsd_mod = xsd_mod.replaceAll('type="DADynamicdatearray" minOccurs="0"/>','minOccurs="0"><xs:complexType><xs:sequence><xs:element name="item" type="xs:date" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>')




            String annotation = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"1.0\">"
            annotation +=  "\n<xs:annotation>"
            annotation +=  "<xs:appinfo>${alias}</xs:appinfo>"
            annotation +=  "  <xs:documentation xml:lang=\"en\">"
            annotation +=  "  This is the strategy the wsdl is about"
            annotation +=  "  </xs:documentation>"
            annotation +=  "</xs:annotation>"


            xsd_mod = xsd_mod.replaceAll('<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" version="1.0">',annotation)

            // Defines the number of occurences for each field.
            //String occurence = MyInterfaceBrokerProperties.getPropertyValue('sis.fields.occurence');
            logger.debug "Evaluating [${xsd_mod}]"
            logger.debug "Index: ${xsd_mod.indexOf('<xs:element name="OCONTROL">')}"
            def iStartOcontrol = xsd_mod.indexOf('<xs:element minOccurs="0" name="OCONTROL">');
            if(iStartOcontrol == -1) iStartOcontrol = xsd_mod.indexOf('<xs:element name="OCONTROL">');
            String xsd_preOcontrol = xsd_mod.substring(0,iStartOcontrol)
            def iEndOcontrol = xsd_mod.substring(iStartOcontrol).indexOf('<!--');
            String xsd_postOcontrol = xsd_mod.substring(iStartOcontrol).substring(iEndOcontrol)//.replaceAll('<xs:element name="', '<xs:element minOccurs="' + occurence + '" name="');

            String newOControl ='';

            newOControl += '<xs:element name="OCONTROL">\n'
            newOControl += '<xs:complexType>\n'
            newOControl += '<xs:all>\n'
            newOControl += '<xs:element name="ALIAS" type="xs:string"/>\n'
            newOControl += '<xs:element name="SIGNATURE" type="xs:string"/>\n'
            newOControl += '<xs:element name="DALOGLEVEL" minOccurs="0" >\n'
            newOControl += '<xs:simpleType>\n'
            newOControl += '<xs:restriction base="xs:integer">\n'
            newOControl += '<xs:minInclusive value="0"/>\n'
            newOControl += '<xs:maxInclusive value="31"/>\n'
            newOControl += '</xs:restriction>\n'
            newOControl += '</xs:simpleType>\n'
            newOControl += '</xs:element>\n'
            newOControl += '<xs:element name="EDITION" minOccurs="0" type="xs:decimal"/>\n'
            newOControl += '<xs:element name="OBJECTIVE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="EDITIONDATE" minOccurs="0" type="xs:date"/>\n'
            newOControl += '<xs:element name="ERRORCODE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="ERRORMSG" minOccurs="0" type="xs:string"/>\n'

            String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
            if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != "" && applicationIdHeaderField.trim() != "null"  ){
                newOControl += "<xs:element name=\"${applicationIdHeaderField}\" type=\"xs:string\"/>\n"
            }


            String additionalMandatoryFields = MyInterfaceBrokerProperties.getPropertyValue('mandatory.additionalfields');
            if(additionalMandatoryFields!=null && additionalMandatoryFields.trim() != ""){
                String[] fieldsToAddMan = additionalMandatoryFields.split("\\|",-1);
                fieldsToAddMan.each {
                    newOControl += '<xs:element name="'+it+'" type="xs:string"/>\n'
                }
            }
            String additionalOptionalFields = MyInterfaceBrokerProperties.getPropertyValue('optional.additionalfields');
            if(additionalOptionalFields!=null && additionalOptionalFields.trim() != ""){

                String[] fieldsToAddOpt = additionalOptionalFields.split("\\|",-1);
                fieldsToAddOpt.each {
                    newOControl += '<xs:element name="'+it+'" minOccurs="0" type="xs:string"/>\n'
                }
            }
            newOControl += '</xs:all>\n'
            newOControl += '</xs:complexType>\n'
            newOControl += '</xs:element>\n'




            xsd_mod = xsd_preOcontrol + newOControl + xsd_postOcontrol

           def xml = new XmlParser().parseText(xsd_mod)
           def bs = xml.'**'.findAll { (it.'@name' == 'value') }
            bs.each { b ->
                b.children().collect { it instanceof Node ? it.clone() : it }.each { b.parent().children().add(it) }
                b.parent().remove(b)
            }
            
            def elements = xml.'**'.findAll { (it.'@name' == 'element') }
                elements.each { e ->
             logger.debug("Child:"+ e.children()[0].children()[0].name()) 
             if(e.children()[0].children()[0].name()=='xs:sequence')
             {  
                 logger.debug("Setting to item")
                 e.'@name' = 'item'
             }
            }

            xsd_mod =  XmlUtil.serialize(xml) 
            

            xsd_mod = xsd_mod.replaceAll("<xs:complexType>(?s)\\s+<xs:sequence>(?s)\\s+<xs:simpleType>", '<xs:simpleType>')
            .replaceAll("<\\/xs:simpleType>(?s)\\s+<\\/xs:sequence>(?s)\\s+<\\/xs:complexType>", '</xs:simpleType>')
            logger.debug("XSD_MOD"+xsd_mod)

           // "Pretty Print"
            xml = new XmlParser().parseText(xsd_mod)
            xsd_mod =  XmlUtil.serialize(xml) 
            FileWriter fw = new FileWriter(new File(dirName+"/"+alias+"_st.xsd"));
            fw.write(xsd_mod);
            fw.close();


            ret['dirName'] = dirName;

            //Build the documentation and sample files

            try{
                String encoding = "ISO-5589-1";
                String xsd_xml_head = xsd_mod.substring(0,xsd_mod.indexOf('?>')+2)
                String encAux =  xsd_xml_head.substring(xsd_xml_head.indexOf("encoding=")+9)
                if(encAux.length()>0){
                    encoding = encAux.replaceAll('"','').replaceAll('\\?','').replaceAll('>','').trim();
                }
                generateXMLFile(alias, xsd_mod,dirName, encoding, applicationIdHeaderField, message.get("REST")=="Y", version)
                generateBatchFile(alias, xsd_mod,dirName, encoding)

            }catch(Throwable th){
                logger.error("Error creating the XML file: " +  th.getMessage());
            }

            try{
                generateHTMLDocumentation(alias,dirName)
            }catch(Throwable th){
                logger.error("Error creating the HTML documentation: " +  th.getMessage());
            }


            return xsd_mod;
        }else{
            logger.error "No XSD file found in paths";
            errorCode = 2;
            errorMessage = "No XSD file found in paths";
            return null;
        }

    }
   private String getXSD(String alias,Hashtable ret, Message message, String version){
        int i = 1;
        String dirName;
        File fIn = null;
        logger.debug("Inside getXSD")
        //logger.debug(prop.stringPropertyNames().toString())  ;
        while(true){
            dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i);
            //logger.debug("Dirname = ${dirName}");
            //logger.debug("Looking for  " +  dirName+"/"+alias+".xsd") ;
            if(dirName == null){
                break;
            }
            fIn = new File(dirName+"/"+alias+".ser");
            if(fIn.exists()){
                break;
            }
            fIn = null;
            i++;
        }
        File xsdFile = null;
        if(fIn != null){
            def jsonSchema = "";
            if(generateXSD){
                XMLCodeGenV2 gen = new XMLCodeGenV2(fIn,alias)
                def xsdContent = gen.generate("UTF-8")
                logger.debug "xsdContent > $xsdContent"
                xsdFile = new File(dirName+"/"+alias+".xsd");
                xsdFile.write(xsdContent)
            }else{
                xsdFile = new File(dirName+"/"+alias+".xsd");
            }
        }

        if(xsdFile!=null && xsdFile.exists()){

            String fileData  = xsdFile.text;

            String xsd_mod = fileData.toString().replaceAll('type="DAnumeric"','type="xs:decimal" nillable="true"')
            xsd_mod = xsd_mod.replaceAll('type="DAdate"','type="xs:date" nillable="true"')
            xsd_mod = xsd_mod.replaceAll('type="DAstring"','type="xs:string" nillable="true"')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="text"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="numeric"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="date"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="element" type="DAnumericarrayelement"','<xs:element name="item" type="xs:decimal"')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="element" type="DAstringarrayelement"','<xs:element name="item" type="xs:string"')

            xsd_mod = xsd_mod.replaceAll('type="DADynamicstringarray" minOccurs="0"/>','minOccurs="0"><xs:complexType><xs:sequence><xs:element name="item" type="xs:string" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>')
            xsd_mod = xsd_mod.replaceAll('type="DADynamicnumericarray" minOccurs="0"/>','minOccurs="0"><xs:complexType><xs:sequence><xs:element name="item" type="xs:decimal" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>')
            xsd_mod = xsd_mod.replaceAll('type="DADynamicdatearray" minOccurs="0"/>','minOccurs="0"><xs:complexType><xs:sequence><xs:element name="item" type="xs:date" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>')

            xsd_mod = xsd_mod.replaceAll('data source -->'+newline+'\\s+','data source -->')
            xsd_mod = xsd_mod.replaceAll('data source --><xs:element name','data source -->'+newline+'<xs:element minOccurs="0" name')






            String annotation = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"1.0\">"
            annotation +=  "\n<xs:annotation>"
            annotation +=  "<xs:appinfo>${alias}</xs:appinfo>"
            annotation +=  "  <xs:documentation xml:lang=\"en\">"
            annotation +=  "  This is the strategy the wsdl is about"
            annotation +=  "  </xs:documentation>"
            annotation +=  "</xs:annotation>"


            xsd_mod = xsd_mod.replaceAll('<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" version="1.0">',annotation)

            // Defines the number of occurences for each field.
            //String occurence = MyInterfaceBrokerProperties.getPropertyValue('sis.fields.occurence');
            logger.debug "Evaluating [${xsd_mod}]"
            logger.debug "Index: ${xsd_mod.indexOf('<xs:element name="OCONTROL">')}"
            def iStartOcontrol = xsd_mod.indexOf('<xs:element minOccurs="0" name="OCONTROL">');
            if(iStartOcontrol == -1) iStartOcontrol = xsd_mod.indexOf('<xs:element name="OCONTROL">');
            String xsd_preOcontrol = xsd_mod.substring(0,iStartOcontrol)
            def iEndOcontrol = xsd_mod.substring(iStartOcontrol).indexOf('<!--');
            String xsd_postOcontrol = xsd_mod.substring(iStartOcontrol).substring(iEndOcontrol)//.replaceAll('<xs:element name="', '<xs:element minOccurs="' + occurence + '" name="');

            String newOControl ='';

            newOControl += '<xs:element name="OCONTROL">\n'
            newOControl += '<xs:complexType>\n'
            newOControl += '<xs:all>\n'
            newOControl += '<xs:element name="ALIAS" type="xs:string"/>\n'
            newOControl += '<xs:element name="SIGNATURE" type="xs:string"/>\n'
            newOControl += '<xs:element name="DALOGLEVEL" minOccurs="0" >\n'
            newOControl += '<xs:simpleType>\n'
            newOControl += '<xs:restriction base="xs:integer">\n'
            newOControl += '<xs:minInclusive value="0"/>\n'
            newOControl += '<xs:maxInclusive value="31"/>\n'
            newOControl += '</xs:restriction>\n'
            newOControl += '</xs:simpleType>\n'
            newOControl += '</xs:element>\n'
            newOControl += '<xs:element name="EDITION" minOccurs="0" type="xs:decimal"/>\n'
            newOControl += '<xs:element name="OBJECTIVE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="EDITIONDATE" minOccurs="0" type="xs:date"/>\n'
            newOControl += '<xs:element name="ERRORCODE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="ERRORMSG" minOccurs="0" type="xs:string"/>\n'

            String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
            if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != "" && applicationIdHeaderField.trim() != "null"  ){
                newOControl += "<xs:element name=\"${applicationIdHeaderField}\" type=\"xs:string\"/>\n"
            }


            String additionalMandatoryFields = MyInterfaceBrokerProperties.getPropertyValue('mandatory.additionalfields');
            if(additionalMandatoryFields!=null && additionalMandatoryFields.trim() != ""){
                String[] fieldsToAddMan = additionalMandatoryFields.split("\\|",-1);
                fieldsToAddMan.each {
                    newOControl += '<xs:element name="'+it+'" type="xs:string"/>\n'
                }
            }
            String additionalOptionalFields = MyInterfaceBrokerProperties.getPropertyValue('optional.additionalfields');
            if(additionalOptionalFields!=null && additionalOptionalFields.trim() != ""){

                String[] fieldsToAddOpt = additionalOptionalFields.split("\\|",-1);
                fieldsToAddOpt.each {
                    newOControl += '<xs:element name="'+it+'" minOccurs="0" type="xs:string"/>\n'
                }
            }
            newOControl += '</xs:all>\n'
            newOControl += '</xs:complexType>\n'
            newOControl += '</xs:element>\n'

            xsd_mod = xsd_preOcontrol + newOControl + xsd_postOcontrol


            FileWriter fw = new FileWriter(new File(dirName+"/"+alias+"_st.xsd"));
            fw.write(xsd_mod);
            fw.close();


            ret['dirName'] = dirName;

            //Build the documentation and sample files

            try{
                String encoding = "ISO-5589-1";
                String xsd_xml_head = xsd_mod.substring(0,xsd_mod.indexOf('?>')+2)
                String encAux =  xsd_xml_head.substring(xsd_xml_head.indexOf("encoding=")+9)
                if(encAux.length()>0){
                    encoding = encAux.replaceAll('"','').replaceAll('\\?','').replaceAll('>','').trim();
                }
                generateXMLFile(alias, xsd_mod,dirName, encoding, applicationIdHeaderField, message.get("REST")=="Y", version)
                generateBatchFile(alias, xsd_mod,dirName, encoding)

            }catch(Throwable th){
                logger.error("Error creating the XML file: " +  th.getMessage());
            }

            try{
                generateHTMLDocumentation(alias,dirName)
            }catch(Throwable th){
                logger.error("Error creating the HTML documentation: " +  th.getMessage());
            }


            return xsd_mod;
        }else{
            logger.error "No XSD file found in paths";
            errorCode = 2;
            errorMessage = "No XSD file found in paths";
            return null;
        }

    }
    private String getXSDLegacy(String alias,Hashtable ret, Message message, String version){
        logger.debug "Generating XSD Legacy for $alias"
        int i = 1;
        String dirName;
        File fIn = null;
        //logger.debug(prop.stringPropertyNames().toString())  ;
        while(true){
            dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i);
            //logger.debug("Dirname = ${dirName}");
            //logger.debug("Looking for  " +  dirName+"/"+alias+".xsd") ;
            if(dirName == null){
                break;
            }
            fIn = new File(dirName+"/"+alias+".ser");
            if(fIn.exists()){
                break;
            }
            fIn = null;
            i++;
        }
        File xsdFile = null;
        if(fIn != null){
            if(generateXSD){
                //Generar JSONSChema
                XMLCodeGen gen = new XMLCodeGen(fIn,alias)
                def xsdContent = gen.generate("UTF-8")
                logger.debug "xsdContentLegacy > $xsdContent"
                xsdFile = new File(dirName+"/"+alias+".xsd");
                xsdFile.write(xsdContent)
            }else{
                xsdFile = new File(dirName+"/"+alias+".xsd");
            }
        }

        if(xsdFile!=null){

            String fileData  = xsdFile.text
            String xsd_mod = fileData.toString().replaceAll('type="DAnumeric"','type="xs:decimal" nillable="true"').replaceAll('type="DAdate"','type="xs:date" nillable="true"').replaceAll('type="DAstring"','type="xs:string" nillable="true"').replaceAll('<xs:element name="data_type" type="DAarray"/>','')
            String annotation = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"1.0\">"
            annotation +=  "\n<xs:annotation>"
            annotation +=  "<xs:appinfo>${alias}</xs:appinfo>"
            annotation +=  "  <xs:documentation xml:lang=\"en\">"
            annotation +=  "  This is the strategy the wsd is about"
            annotation +=  "  </xs:documentation>"
            annotation +=  "</xs:annotation>"

            /*annotation +=  '<xs:simpleType name="empty-string">'
            annotation +=  '<xs:restriction base="xs:string">'
            annotation +=  '<xs:enumeration value="" />'
            annotation +=  '</xs:restriction>'
            annotation +=  '</xs:simpleType>'

            annotation +=  '<xs:simpleType name="decimal-or-empty">'
            annotation +=  '<xs:union memberTypes="xs:decimal empty-string" />'
            annotation +=  '</xs:simpleType>'
            */
            xsd_mod = xsd_mod.replaceAll('<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" version="1.0">',annotation)

            // Defines the number of occurences for each field.
            String occurence = MyInterfaceBrokerProperties.getPropertyValue('sis.fields.occurence');

            def iStartOcontrol = xsd_mod.indexOf('<xs:element name="OCONTROL">');
            String xsd_preOcontrol = xsd_mod.substring(0,iStartOcontrol)
            def iEndOcontrol = xsd_mod.substring(iStartOcontrol).indexOf('<!--');
            String xsd_postOcontrol = xsd_mod.substring(iStartOcontrol).substring(iEndOcontrol).replaceAll('<xs:element name="', '<xs:element minOccurs="' + occurence + '" name="');

            String newOControl ='';

            newOControl += '<xs:element name="OCONTROL">\n'
            newOControl += '<xs:complexType>\n'
            newOControl += '<xs:all>\n'
            newOControl += '<xs:element name="ALIAS" type="xs:string"/>\n'
            newOControl += '<xs:element name="SIGNATURE" type="xs:string"/>\n'
            newOControl += '<xs:element name="DALOGLEVEL" minOccurs="0" >\n'
            newOControl += '<xs:simpleType>\n'
            newOControl += '<xs:restriction base="xs:integer">\n'
            newOControl += '<xs:minInclusive value="0"/>\n'
            newOControl += '<xs:maxInclusive value="31"/>\n'
            newOControl += '</xs:restriction>\n'
            newOControl += '</xs:simpleType>\n'
            newOControl += '</xs:element>\n'
            newOControl += '<xs:element name="EDITION" minOccurs="0" type="xs:decimal"/>\n'
            newOControl += '<xs:element name="OBJECTIVE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="EDITIONDATE" minOccurs="0" type="xs:date"/>\n'
            newOControl += '<xs:element name="ERRORCODE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="ERRORMSG" minOccurs="0" type="xs:string"/>\n'

            String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
            if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != "" && applicationIdHeaderField.trim() != "null"){
                newOControl += "<xs:element name=\"${applicationIdHeaderField}\" type=\"xs:string\"/>\n"
            }


            String additionalMandatoryFields = MyInterfaceBrokerProperties.getPropertyValue('mandatory.additionalfields');
            if(additionalMandatoryFields!=null && additionalMandatoryFields.trim() != ""){
                String[] fieldsToAddMan = additionalMandatoryFields.split("\\|",-1);
                fieldsToAddMan.each {
                    newOControl += '<xs:element name="'+it+'" type="xs:string"/>\n'
                }
            }
            String additionalOptionalFields = MyInterfaceBrokerProperties.getPropertyValue('optional.additionalfields');
            if(additionalOptionalFields!=null && additionalOptionalFields.trim() != ""){

                String[] fieldsToAddOpt = additionalOptionalFields.split("\\|",-1);
                fieldsToAddOpt.each {
                    newOControl += '<xs:element name="'+it+'" minOccurs="0" type="xs:string"/>\n'
                }
            }
            newOControl += '</xs:all>\n'
            newOControl += '</xs:complexType>\n'
            newOControl += '</xs:element>\n'

            xsd_mod = xsd_preOcontrol + newOControl + xsd_postOcontrol


            FileWriter fw = new FileWriter(new File(dirName+"/"+alias+"_st.xsd"));
            fw.write(xsd_mod);
            fw.close();
            //reader.close();

            ret['dirName'] = dirName;

            //Build the documentation and sample files

            try{
                String encoding = "ISO-5589-1";
                String xsd_xml_head = xsd_mod.substring(0,xsd_mod.indexOf('?>')+2)
                String encAux =  xsd_xml_head.substring(xsd_xml_head.indexOf("encoding=")+9)
                if(encAux.length()>0){
                    encoding = encAux.replaceAll('"','').replaceAll('\\?','').replaceAll('>','').trim();
                }
                generateXMLFile(alias, xsd_mod,dirName, encoding, applicationIdHeaderField, message.get("REST")=="Y", version)
                generateBatchFile(alias, xsd_mod,dirName, encoding)

            }catch(Throwable th){
                logger.error("Error creating the XML file: " +  th.getMessage());
            }

            try{
                generateHTMLDocumentation(alias,dirName)
            }catch(Throwable th){
                logger.error("Error creating the HTML documentation: " +  th.getMessage());
            }


            return xsd_mod;
        }else{
            logger.error "No SER/XSD file found in paths";
            errorCode = 2;
            errorMessage = "No SER/XSD file found in paths";
            return null;
        }

    }

      private String getXSDv3Legacy(String alias,Hashtable ret, Message message, String version){
           logger.debug("Inside getXSDv3Legacy")
        logger.debug "Generating XSD Legacy for $alias"
        int i = 1;
        String dirName;
        File fIn = null;
        //logger.debug(prop.stringPropertyNames().toString())  ;
        while(true){
            dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i);
            //logger.debug("Dirname = ${dirName}");
            //logger.debug("Looking for  " +  dirName+"/"+alias+".xsd") ;
            if(dirName == null){
                break;
            }
            fIn = new File(dirName+"/"+alias+".ser");
            if(fIn.exists()){
                break;
            }
            fIn = null;
            i++;
        }
        File xsdFile = null;
        if(fIn != null){
            if(generateXSD){
                //Generar JSONSChema
                XMLCodeGenV3 gen = new XMLCodeGenV3(fIn,alias)
                def xsdContent = gen.generate("UTF-8")
                logger.debug "xsdContentLegacy > $xsdContent"
                xsdFile = new File(dirName+"/"+alias+".xsd");
                xsdFile.write(xsdContent)
            }else{
                xsdFile = new File(dirName+"/"+alias+".xsd");
            }
        }

        if(xsdFile!=null){

            String fileData  = xsdFile.text
            String xsd_mod = fileData.toString().replaceAll('type="DAnumeric"','type="xs:decimal" nillable="true"').replaceAll('type="DAdate"','type="xs:date" nillable="true"').replaceAll('type="DAstring"','type="xs:string" nillable="true"').replaceAll('<xs:element name="data_type" type="DAarray"/>','')
            String annotation = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"1.0\">"
            annotation +=  "\n<xs:annotation>"
            annotation +=  "<xs:appinfo>${alias}</xs:appinfo>"
            annotation +=  "  <xs:documentation xml:lang=\"en\">"
            annotation +=  "  This is the strategy the wsd is about"
            annotation +=  "  </xs:documentation>"
            annotation +=  "</xs:annotation>"

            /*annotation +=  '<xs:simpleType name="empty-string">'
            annotation +=  '<xs:restriction base="xs:string">'
            annotation +=  '<xs:enumeration value="" />'
            annotation +=  '</xs:restriction>'
            annotation +=  '</xs:simpleType>'

            annotation +=  '<xs:simpleType name="decimal-or-empty">'
            annotation +=  '<xs:union memberTypes="xs:decimal empty-string" />'
            annotation +=  '</xs:simpleType>'
            */
            xsd_mod = xsd_mod.replaceAll('<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" version="1.0">',annotation)
            xsd_mod = fileData.toString().replaceAll('type="DAdate"','type="xs:date" nillable="true"')
            //Should leave those now ? We don't anymore have the element type in the root <element> type is now only present in <xs:element name="data_type" data_type and <xs:restriction

            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="array"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="text"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="numeric"/>','')
            xsd_mod = xsd_mod.replaceAll('<xs:element name="data_type" type="xs:string" fixed="date"/>','')
            xsd_mod = xsd_mod.replaceAll('minOccurs="0"','nillable="true" minOccurs="0" ')
			xsd_mod = xsd_mod.replaceAll('nillable="true" nillable="true"','nillable="true"')
            
            // Defines the number of occurences for each field.
            String occurence = MyInterfaceBrokerProperties.getPropertyValue('sis.fields.occurence');

            def iStartOcontrol = xsd_mod.indexOf('<xs:element name="OCONTROL">');
            String xsd_preOcontrol = xsd_mod.substring(0,iStartOcontrol)
            def iEndOcontrol = xsd_mod.substring(iStartOcontrol).indexOf('<!--');
            String xsd_postOcontrol = xsd_mod.substring(iStartOcontrol).substring(iEndOcontrol)

            String newOControl ='';

            newOControl += '<xs:element name="OCONTROL">\n'
            newOControl += '<xs:complexType>\n'
            newOControl += '<xs:all>\n'
            newOControl += '<xs:element name="ALIAS" type="xs:string"/>\n'
            newOControl += '<xs:element name="SIGNATURE" type="xs:string"/>\n'
            newOControl += '<xs:element name="DALOGLEVEL" minOccurs="0" >\n'
            newOControl += '<xs:simpleType>\n'
            newOControl += '<xs:restriction base="xs:integer">\n'
            newOControl += '<xs:minInclusive value="0"/>\n'
            newOControl += '<xs:maxInclusive value="31"/>\n'
            newOControl += '</xs:restriction>\n'
            newOControl += '</xs:simpleType>\n'
            newOControl += '</xs:element>\n'
            newOControl += '<xs:element name="EDITION" minOccurs="0" type="xs:decimal"/>\n'
            newOControl += '<xs:element name="OBJECTIVE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="EDITIONDATE" minOccurs="0" type="xs:date"/>\n'
            newOControl += '<xs:element name="ERRORCODE" minOccurs="0" type="xs:string"/>\n'
            newOControl += '<xs:element name="ERRORMSG" minOccurs="0" type="xs:string"/>\n'

            String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
            if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != "" && applicationIdHeaderField.trim() != "null"){
                newOControl += "<xs:element name=\"${applicationIdHeaderField}\" type=\"xs:string\"/>\n"
            }


            String additionalMandatoryFields = MyInterfaceBrokerProperties.getPropertyValue('mandatory.additionalfields');
            if(additionalMandatoryFields!=null && additionalMandatoryFields.trim() != ""){
                String[] fieldsToAddMan = additionalMandatoryFields.split("\\|",-1);
                fieldsToAddMan.each {
                    newOControl += '<xs:element name="'+it+'" type="xs:string"/>\n'
                }
            }
            String additionalOptionalFields = MyInterfaceBrokerProperties.getPropertyValue('optional.additionalfields');
            if(additionalOptionalFields!=null && additionalOptionalFields.trim() != ""){

                String[] fieldsToAddOpt = additionalOptionalFields.split("\\|",-1);
                fieldsToAddOpt.each {
                    newOControl += '<xs:element name="'+it+'" minOccurs="0" type="xs:string"/>\n'
                }
            }
            newOControl += '</xs:all>\n'
            newOControl += '</xs:complexType>\n'
            newOControl += '</xs:element>\n'
 
            xsd_mod = xsd_preOcontrol + newOControl + xsd_postOcontrol
            def xml = new XmlParser().parseText(xsd_mod)
           def bs = xml.'**'.findAll { (it.'@name' == 'value')}

            bs.each { b ->
                b.children().collect { it instanceof Node ? it.clone() : it }.each { b.parent().children().add(it) }
                b.parent().remove(b)
            }
            
            logger.debug("xsd_mod before"+xsd_mod)

            logger.debug("xsd_mod after"+xsd_mod)
            xsd_mod =  XmlUtil.serialize(xml) 
             xsd_mod = xsd_mod.replaceAll("<xs:complexType>(?s)\\s+<xs:sequence>(?s)\\s+<xs:simpleType>", '<xs:simpleType>')
            .replaceAll("<\\/xs:simpleType>(?s)\\s+<\\/xs:sequence>(?s)\\s+<\\/xs:complexType>", '</xs:simpleType>')

            //Hack to pretty print
              xsd_mod =  XmlUtil.serialize(xsd_mod) 
            FileWriter fw = new FileWriter(new File(dirName+"/"+alias+"_st.xsd"));
            fw.write(xsd_mod);
            fw.close();
            //reader.close();

            ret['dirName'] = dirName;

            //Build the documentation and sample files

            try{
                String encoding = "ISO-5589-1";
                String xsd_xml_head = xsd_mod.substring(0,xsd_mod.indexOf('?>')+2)
                String encAux =  xsd_xml_head.substring(xsd_xml_head.indexOf("encoding=")+9)
                if(encAux.length()>0){
                    encoding = encAux.replaceAll('"','').replaceAll('\\?','').replaceAll('>','').trim();
                }
                generateXMLFile(alias, xsd_mod,dirName, encoding, applicationIdHeaderField, message.get("REST")=="Y", version)
                generateBatchFile(alias, xsd_mod,dirName, encoding)

            }catch(Throwable th){
                logger.error("Error creating the XML file: " +  th.getMessage());
            }

            try{
                generateHTMLDocumentation(alias,dirName)
            }catch(Throwable th){
                logger.error("Error creating the HTML documentation: " +  th.getMessage());
            }


            return xsd_mod;
        }else{
            logger.error "No SER/XSD file found in paths";
            errorCode = 2;
            errorMessage = "No SER/XSD file found in paths";
            return null;
        }

    }

    public void printMethodList(Object o){

        Method[] met = o.getClass().getDeclaredMethods();
        met.each {
            //logger.debug("Method: " + it.getName());
        }

    }

    private void generateXMLFile(String alias, String xsdText, String dirName, String encoding, String applicationIdHeaderField, boolean isREST, String version){
        try{
            //logger.debug "Generating sample xml message file"
            def xsd = new XmlSlurper().parseText(xsdText)

            //logger.debug xsd.name();

            IData[] areas = SampleBuilder.buildSampleAreas(xsd,alias,logger)

            StringBuffer sb = new StringBuffer(1024)
            sb.append("<?xml version=\"1.0\" encoding=\"${encoding}\"?>\n")
            if(!isREST) {
                if (version == '1.2') {
                    sb.append("<soap:envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
                }else{
                    sb.append("<soap:envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
                }
                sb.append("<soap:body>\n")
            }
            sb.append("<DAXMLDocument xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")

            logger.info "Areas for sample message: $areas"
            areas.each{

                sb.append(it.toXML(null,null,applicationIdHeaderField, false))
                sb.append("\n")

                logger.debug "${it.getLayout()} :: Sample message returned > ${sb.toString()}"
            }
            sb.append("</DAXMLDocument>\n")
            if(!isREST){
                sb.append("</soap:body>\n")
                sb.append("</soap:envelope>")
            }
            FileWriter fw = new FileWriter(new File(dirName+"/"+alias+"_sample.xml"));
            fw.write(sb.toString());
            fw.close();
        }catch(Throwable th){
            logger.error "Error generating sample message : " + th.getMessage()
            logger.error "${th.getStackTrace()}"
        }

    }

    private void generateBatchFile(String alias, String xsdText, String dirName, String encoding){
        try{
            //logger.debug "Generating sample xml message file"
            def xsd = new XmlSlurper().parseText(xsdText)

            //logger.debug xsd.name();

            IData[] areas = SampleBuilder.buildSampleAreas(xsd,alias,logger)

            //logger.debug "Areas for the sample batch ${areas}"

            StringBuffer sb = new StringBuffer(3000)
            String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
            areas.each{

                sb.append(it.toPlainHeader(fieldSeparator,applicationIdHeaderField))
                //sb.append(fieldSeparator)

            }

            sb.append(newline);

            areas.each{

                sb.append(it.toPlain(fieldSeparator, 31,applicationIdHeaderField))
                //sb.append(fieldSeparator)

            }

            sb.append(newline);

            FileWriter fw = new FileWriter(new File(dirName+"/"+alias+"_batch_sample.txt"));
            fw.write(sb.toString());
            fw.close();
        }catch(Throwable th){
            logger.error th.getMessage()
        }

    }

    public void generateHTMLDocumentation(String alias, String dir){
        try {

            TransformerFactory tFactory = TransformerFactory.newInstance();

            Transformer transformer =  tFactory.newTransformer(new StreamSource(this.getClass().getClassLoader().getResourceAsStream("da.xsl")));

            FileOutputStream fos = new FileOutputStream(dir+"/"+alias+".html")

            transformer.transform(new StreamSource(dir+"/"+alias+"_st.xsd"), new StreamResult(fos))

            fos.close()

            copyFile(this.getClass().getClassLoader().getResource("experian.png").getPath(),dir+"/experian.png")

        }catch(Exception e){
            logger.error "Error generating HTML documentation: " + e.getMessage()
        }
    }

    public void copyFile(String sourcePath, String destPath) throws IOException {


        File sourceFile = new File(sourcePath);
        File destFile = new File(destPath);


        if(destFile.exists()) {
            return;
        }

        destFile.createNewFile();
        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
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
