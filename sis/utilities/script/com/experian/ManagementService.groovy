package com.experian

import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import com.experian.util.DAAgnostic
import com.experian.util.MD5
import com.experian.util.MyInterfaceBrokerProperties
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.commons.codec.binary.Base64

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
public class ManagementService implements GroovyComponent<Message>
{

    protected static final ExpLogger  logger     = new ExpLogger(this);
    protected static final String     TR_SUCCESS = "success";
    protected static final String     METHODS    = "listOfActions|getFailedCalls|getLoadedReasonCodes|getLoadedStrategies|getSuccessfulCalls|getStrategyInfo|getTotalCalls|isReasonCodeLoaded|isStrategyLoaded|loadReasonCode|loadStrategy|suspendStrategy|unloadAllReasonCodes|unloadAllStrategies|unloadReasonCode|unloadStrategy|unsuspendStrategy|getStrategyDeposits|getDAProperties|getSystemProperties"
    protected              Properties prop = new Properties();

    protected static String version = "2.11";

	public ManagementService()
	{
		/* Printing checksum */
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
      def response = [:]
      def reqMsg = message['data']
      if(!validateUser(message)){
          response.put('error', 'MS05')
          response.put('msg', 'Not authorized')
      }else {
          if (reqMsg == null || reqMsg.trim() == "") {
              response.put('error', 'MS01')
              response.put('msg', 'Empty request')
          } else {
              def content = new JsonSlurper().parseText(reqMsg)
              def action = content['action']
              def alias = content['alias']
              if (action == null || action.trim() == "") {
                  response.put('error', 'MS02')
                  response.put('msg', 'No action requested')
              } else {
                  if (METHODS.indexOf(action) == -1) {
                      response.put('error', 'MS03')
                      response.put('msg', 'Incorrect action requested')
                  } else {
                      response = executeAction(message, action, alias)
                  }
              }

          }
      }


      message.put("data",JsonOutput.toJson(response))
      HashMap headers = new HashMap();
      //headers['Content-Type']= "application/json; charset=${encoding}";
      headers['Cache-Control']= "no-cache";

      message.put("http.response.headers",headers);
      message.put("contentType", "application/json")



      return TR_SUCCESS;
  }

    public Map executeAction(Message message, String action, String alias){
        def response = [:]
        //"loadReasonCode|loadStrategy|suspendStrategy|unloadAllReasonCodes|unloadAllStrategies|unloadReasonCode|unloadStrategy|unsuspendStrategy|getStrategyDeposits"

        try{
            switch (action){
                case 'listOfActions':
                    String[] res = METHODS.split("\\|",-1)
                    response.put('actions',res)
                    break;
                case 'getFailedCalls':
                    int res = DAAgnostic.managementInterface.getFailedCalls(alias)
                    response.put('alias',alias)
                    response.put('failedCalls',res)
                    break;
                case 'getSuccessfulCalls':
                    int res = DAAgnostic.managementInterface.getSuccessfulCalls(alias)
                    response.put('alias',alias)
                    response.put('successfulCalls',res)
                    break;
                case 'getTotalCalls':
                    int res = DAAgnostic.managementInterface.getTotalCalls(alias)
                    response.put('alias',alias)
                    response.put('totalCalls',res)
                    break;
                case 'getLoadedReasonCodes':
                    String[] res = DAAgnostic.managementInterface.getLoadedReasonCodes()
                    response.put('reasonCodes',res)
                    break;
                case 'isReasonCodeLoaded':
                    boolean res = DAAgnostic.managementInterface.isReasonCodeLoaded(alias)
                    response.put('alias',alias)
                    response.put('loaded',res)
                    break;
                case 'loadReasonCode':
                    String res = DAAgnostic.managementInterface.loadReasonCode(alias)
                    response.put('alias',alias)
                    response.put('result',res?:"")
                    break;
                case 'unloadReasonCode':
                    String res = DAAgnostic.managementInterface.unloadReasonCode(alias)
                    response.put('alias',alias)
                    response.put('result',res?:"")
                    break
                case 'unloadAllReasonCodes':
                    String res = DAAgnostic.managementInterface.unloadAllReasonCodes()
                    response.put('result',res?:"")
                    break;
                case 'getLoadedStrategies':
                    String[] res = DAAgnostic.managementInterface.getLoadedStrategies()
                    response.put('strategies',res)
                    break;
                case 'getStrategyInfo':
                    String res = DAAgnostic.managementInterface.getStrategyInfo(alias)
                    response.put('alias',alias)
                    response.put('strategyInfo',res?:"")
                    break;
                case 'isStrategyLoaded':
                    boolean res = DAAgnostic.managementInterface.isStrategyLoaded(alias)
                    response.put('alias',alias)
                    response.put('loaded',res)
                    break;
                case 'loadStrategy':
                    String res = DAAgnostic.managementInterface.loadStrategy(alias)
                    response.put('alias',alias)
                    response.put('result',res?:"")
                    break;
                case 'suspendStrategy':
                    String res = DAAgnostic.managementInterface.suspendStrategy(alias)
                    response.put('alias',alias)
                    response.put('result',res?:"")
                    break;
                case 'unloadStrategy':
                    String res = DAAgnostic.managementInterface.unloadStrategy(alias)
                    response.put('alias',alias)
                    response.put('result',res?:"")
                    break;
                case 'unsuspendStrategy':
                    String res = DAAgnostic.managementInterface.unsuspendStrategy(alias)
                    response.put('alias',alias)
                    response.put('result',res?:"")
                    break;
                case 'unloadAllStrategies':
                    String res = DAAgnostic.managementInterface.unloadAllStrategies()
                    response.put('result',res?:"")
                    break;
                case 'getStrategyDeposits':
                    def deposits = []
                    File fIn = null;
                    String dirName
                    int i=1
                    while(true){
                        dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i);

                        if(dirName == null){
                            break
                        }
                        File fDir = new File(dirName)
                        def dirCont = []

                        fDir.listFiles().each{
                            dirCont.add([name:it.getName(),lastModified:new Date(it.lastModified()),size:it.size()])
                        }

                        deposits.add([deposit:"$dirName",content:dirCont])

                        i++
                    }
                    response.put('deposits',deposits)
                    break
                case 'getDAProperties':
                    def ret = []
                    prop.keySet().each{
                        ret.add(["key":it,"value":prop.getProperty(it)])
                    }
                    response.put('properties',ret)
                    break;
                case 'getSystemProperties':
                    def ret = []
                    MyInterfaceBrokerProperties.prop.keySet().each{
                        ret.add(["key":it,"value":MyInterfaceBrokerProperties.prop.getProperty(it)])
                    }
                    response.put('properties',ret)
            }
        }catch(Exception e){
            logger.error "Error during execution of MS: $e"
            logger.error "${e.getStackTrace()}"
            response.put('error', 'MS04')
            response.put('msg', 'Error in the execution of the Management Service')
        }
        return response
    }


    private boolean validateUser(Message message ){
        String user = "";
        String pass = "";


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
        //logger.debug message.get("callID") + " After decoded ${userPass}"
        user = userPass.split(":",-1)[0]
        pass = userPass.split(":",-1)[1]
        //logger.debug message.get("callID") + " Basic auth: ${user}/${pass}"

        if(user == MyInterfaceBrokerProperties.getPropertyValue("mng.user") &&
           pass == MyInterfaceBrokerProperties.getPropertyValue("mng.pass")){

            return true;
        }else{
            return false;
        }

    }


    

	
}