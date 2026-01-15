/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.experian;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import com.experian.util.DAAgnostic;
import com.experian.util.MD5
import com.experian.util.MyInterfaceBrokerProperties;
import com.ibm.mq.MQC;
import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQPutMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;

/**
 *
 * @author cuestae
 */

public class MQ_Listener implements GroovyComponent<Message> {

    protected static final  ExpLogger  logger       = new ExpLogger(this);
    protected static        String replyQueueName = MyInterfaceBrokerProperties.getPropertyValue('MQ.replyQueueName');
    protected static        String encoding = MyInterfaceBrokerProperties.getPropertyValue('MQ.encoding');
    protected static        int daLogLevel = Integer.parseInt(MyInterfaceBrokerProperties.getPropertyValue('da.log.level'));
	 
    //MQEnvironment.userID = InterfaceBrokerProperties.getProperty('MQ.user');
    //MQEnvironment.password = InterfaceBrokerProperties.getProperty('MQ.password');
    //set transport properties.
    protected static        String qMngrStr = MyInterfaceBrokerProperties.getPropertyValue('MQ.queueMng');
	protected static String version = "2.11";
    
    //Create a default local queue.
    MQQueueManager qManager;
    
	public MQ_Listener()
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
	
    public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception {
     
       	logger.debug "Message ${message}"
        
        logger.debug "Set MQ option values"
        int readOptions = MQC.MQOO_INPUT_SHARED;
        
        logger.debug "Set MQ connection credentials"
        //Set MQ connection credentials to MQ Envorinment.
       
         MQEnvironment.properties.put(MQC.TRANSPORT_PROPERTY, MQC.TRANSPORT_MQSERIES_CLIENT);
		   MQEnvironment.hostname = MyInterfaceBrokerProperties.getPropertyValue('MQ.hostName');
		 
		  
		  MQEnvironment.channel = MyInterfaceBrokerProperties.getPropertyValue('MQ.channel');
		  MQEnvironment.port = Integer.parseInt(MyInterfaceBrokerProperties.getPropertyValue('MQ.port'));
        
         try {
             //initialize MQ manager.
            qManager = new MQQueueManager(qMngrStr);
            logger.debug "Initialize MQ manager"
        } catch (MQException e) {
            logger.error "Error setting MQ manager: " + e.getMessage()  +  ": ${e.getStackTrace()}"
            throw e;
            return TR_SUCCESS;
        }
        
        
        try {
            logger.debug "Set MQ GET message"
            //get message from MQ.
            MQMessage getMessages = new MQMessage();
            //assign message id to get message.
            //getMessages.messageId = putMessage.messageId;
            //getMessages.correlationId = putMessage.messageId;
            
            //System.out.println("Correlation id= " + new String(getMessages.correlationId ));
            
            //get message options.
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options=MQC.MQGMO_WAIT;
            gmo.matchOptions = MQC.MQMO_NONE;
            gmo.waitInterval=MQC.MQWI_UNLIMITED;
            String inQueueName = MyInterfaceBrokerProperties.getPropertyValue('MQ.inQueueName');
            MQQueue InQueue = qManager.accessQueue(inQueueName, readOptions);
            
            //int openOptions = MQC.MQOO_INPUT_AS_Q_DEF | MQC.MQOO_OUTPUT; 
            //MQQueue replyQueue= qManager.accessQueue(replyQueueName, openOptions);
            
            while (true){
                logger.debug "Into Loop"
                InQueue.get(getMessages, gmo);
                processMqMessage(getMessages);
            }
            
        } catch (MQException e) {
            logger.error "Error using MQ env: " + e.getMessage()  +  ": ${e.getStackTrace()}"
            throw e;
        } catch (IOException e) {
            logger.error "Error using MQ env: " + e.getMessage()  +  ": ${e.getStackTrace()}"
            throw e;
        }
        
      return TR_SUCCESS;
    }
    
     public void processMqMessage(MQMessage getMessages) throws Exception {
        Thread.start { 
            try {
                int openOptions = MQC.MQOO_OUTPUT; 
                byte[] dataAreas = new byte[getMessages.getDataLength()];
                getMessages.readFully(dataAreas);

                String hex = stringToHex(dataAreas);
                logger.debug "HEX before: ${hex} "

                Integer retCode = DAAgnostic.byteInterface.execute(dataAreas,encoding.getBytes(),daLogLevel);
                logger.debug "Retcode ${retCode} :: ${new String(dataAreas)}"
                hex = stringToHex(dataAreas);
                logger.debug "HEX after: ${hex} "
                
					 MQQueueManager replyqManager = null;
                 try {
                    //initialize MQ manager.
                   replyqManager = new MQQueueManager(qMngrStr);
                   logger.debug "Initialize MQ manager"
               } catch (MQException e) {
                   logger.error "Error setting reply MQ manager: " + e.getMessage()  +  ": ${e.getStackTrace()}"
                   throw e;
                   return TR_SUCCESS;
               }
                
                logger.debug "MQ response options and message to be sent to queue: " + replyQueueName;
                MQQueue replyQueue= replyqManager.accessQueue(replyQueueName, openOptions);
                
                logger.debug "MQ response message creation"
                MQMessage putMessage = new MQMessage();
                putMessage.correlationId = getMessages.messageId;
                putMessage.write(dataAreas);
                putMessage.messageType = MQC.MQMT_DATAGRAM;
                putMessage.format = MQC.MQFMT_NONE;

                //putMessage.replyToQueueManagerName = qManager.getName();
                
                //putMessage.replyToQueueName = replyQueueName;
                MQPutMessageOptions pmo = new MQPutMessageOptions(); 
                
                //specify the message options...
                logger.debug "PUT MQ message";
                
                replyQueue.put(putMessage, pmo);
                logger.debug("Message is put on MQ.");
					 
					 replyQueue.close();
					 replyqManager.close();
					 
            } catch (MQException e) {
                logger.error "Error using MQ message: " + e.getMessage()  +  ": ${e.getStackTrace()}"
                throw e;
            } catch (IOException e) {
               logger.error "Error using MQ message: " + e.getMessage()  +  ": ${e.getStackTrace()}"
                throw e;
            } catch (Exception e) {
               logger.error "Error using MQ message: " + e.getMessage()  +  ": ${e.getStackTrace()}"
                throw e;
            }
        }
        return;
     }
     
    public String stringToHex(byte[] base){
	  StringBuilder sb = new StringBuilder()
	  base.each{
		  def aux = Integer.toHexString(it).toUpperCase();
		  if(aux.length() < 2 ) aux = "0"+aux;
		  sb.append(aux);
		  sb.append(" ");
	  }
	  return sb.toString();
    }
}
