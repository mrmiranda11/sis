package com.experian;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;
// Logger
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import java.io.*;
import java.util.*;
import com.experian.util.*;



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
public class SOAPGateway implements GroovyComponent<Message> {
    
    protected static final ExpLogger  logger       = new ExpLogger(this); 
    protected static final String     TR_SUCCESS   = "success";
	protected static String version = "2.11";

	public SOAPGateway()
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
        
        logger.debug "Entro en SOAPGateway"
    		if(message.get("__ProcessFlowId")!=null){
    			return TR_SUCCESS;
    		}
		
        def headers =  message.get('http.request.headers');
        headers.keySet().toArray().each{
          logger.debug "[ ${it} ] > [ ${headers[it]} ]"
        }
        
        def name = Thread.currentThread().getName();
        
        /* logger.debug "Name of the current thread : ${name}"
        def init = name.indexOf('/DAService') 
        
        if(init != -1){
            String process = name.substring(init+10);
            if(process?.trim() == "" || process?.trim() == "/"){
              message.put("__ProcessFlowId","callDA");
              return TR_SUCCESS;
            }
        
            logger.debug "Getting WSDL"
            logger.debug "Process = ${process}"
            
            int i = process.indexOf("?wsdl=")
            if( i != -1){
               def alias = process.substring(i+6);
               message.put("strategy",alias);
               logger.debug "Setting strategy to ${alias}"
            }
            message.put("__ProcessFlowId","getWSDL");
        } */
        
        if(message['wsdl'] != null){
           message.put("strategy",message['wsdl']);
           message.put("__ProcessFlowId","getWSDL");
        }else if(message['getRuntimeInfo'] != null){
            message.put("__ProcessFlowId","getRuntimeInfo");
        }else{
           message.put("__ProcessFlowId","callDA");
        }
        
        logger.debug "Result message ${message}"
        
        return TR_SUCCESS;
    }
    
    
}