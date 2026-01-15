package com.experian

import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
// Logger
import com.experian.util.MD5
import com.experian.util.TimeAgent

/**
 *   PreCallDA
 *   This class allows customizing the NBSM Builtin component after 
 *   calling the DA and building the response message
 *   <p>
 *   Custom code must be added in the process message method
 *   @version 2.2
 *   @author David Teruel 
 *
 */
public class JSONGateway implements GroovyComponent<Message> {

    protected static final ExpLogger  logger       = new ExpLogger(this);
    protected static final String     TR_SUCCESS   = "success";
	protected static String version = "2.11";

	public JSONGateway()
	{
		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);
		
		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);
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

        message.put("callID","" + (new java.util.Date()).getTime() + ": ");
        TimeAgent tagent = new TimeAgent(message.get("callID"));
        tagent.init()
        message.put('timeAgent',tagent)
        def reqMsg = message['data'];

        //logger.debug "Entro en JSONGateway"
        if(message.get("__ProcessFlowId")!=null){
    		return TR_SUCCESS;
    	}
		
        /*def headers =  message.get('http.request.headers');
        headers.keySet().toArray().each{
          logger.debug "[ ${it} ] > [ ${headers[it]} ]"
        }
        
        def name = Thread.currentThread().getName();
        */

        
        if(message['strategy'] != null){
           message.put("__ProcessFlowId","getJSONSchema");
        }else{
           message.put("__ProcessFlowId","callDAJSON");
        }
        
        logger.debug "Result message ${message}"
        
        return TR_SUCCESS;
    }
    
    
}