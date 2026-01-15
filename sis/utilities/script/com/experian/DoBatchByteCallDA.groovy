package com.experian;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties;
import com.experian.stratman.datasources.runtime.IData
// Logger
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import java.io.*;
import java.util.*;

import com.experian.util.*;
import groovy.jmx.builder.JmxBuilder;




public class DoBatchByteCallDA implements GroovyComponent<Message> {
    
    
	protected static final ExpLogger  logger     = new ExpLogger(this);
	protected static final String     TR_SUCCESS = "success";
	protected static       boolean DAInitialized = false;
	protected static 	   String encoding = MyInterfaceBrokerProperties.getPropertyValue('byteBatchEncoding');
	
	protected static 	   int daLogLevel = Integer.parseInt(InterfaceBrokerProperties.getProperty('da.log.level'));
	
	protected static       Hashtable  XSD_cache    = new Hashtable();
    protected static       Hashtable  File_cache   = new Hashtable();
    protected static       Properties prop = new Properties();
    protected static       int daDefLogLevel = 0;
    protected static       String fieldSeparator = "\t"; 
    protected static       JmxBuilder jmxBuilder = new JmxBuilder();
    protected static       Hashtable typeCache = new Hashtable();
    protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));

	protected static String version = "2.11";
    	
 	public DoBatchByteCallDA()
    {
		logger.info "Intilising Groovy"
		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);
		
		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);
		
        InputStream inS = DoBatchByteCallDA.class.getClassLoader().getResourceAsStream("decisionagent.properties");
        prop.load(inS);
        inS.close();
        String sDaLogLevel = InterfaceBrokerProperties.getProperty("da.log.level");
        fieldSeparator = InterfaceBrokerProperties.getProperty("field.separator");
        daDefLogLevel = Integer.parseInt(sDaLogLevel)
        logger.debug "Creating thread"
		Thread.start 
		{
			while(true)
			{
				List lToRemove = new ArrayList();
				try
				{
					//logger.debug "Wake-up " + WrittingBuffer.getInstanceByte().files.keySet()
					// TODO Auto-generated method stub
					lToRemove.each()
					{
						WrittingBuffer.getInstanceByte().files.remove(it)
					}
					lToRemove.clear();
					WrittingBuffer.getInstanceByte().files.keySet().each 
					{
						long timeSinceLastWrite = System.currentTimeMillis() - ((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).lastWrite;
						
						if(((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).lastWrite != 0 && timeSinceLastWrite > 300000)
						{
							//If the last write is older than 10 minutes, we remove it
							lToRemove.add(it)
						}
						else
						{
							long remain = ((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).lastWrite == 0?300:((300000-timeSinceLastWrite)/1000)
							logger.debug "Checking pending data for ${it} for the next ${remain} secs."
							File fout = new File(((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).fileName);
							
							if(((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).header != "")
							{
								fout.append(((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).header+"\n")
								((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).header = "";
							}
							try
							{
								String line = (String)((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).lines.removeFirst();
								while(line != null)
								{
									fout.append(line+"\n");

									((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).lastWrite = System.currentTimeMillis();
									line = (String)((ResponseFileBuffer)WrittingBuffer.getInstanceByte().files[it]).lines.removeFirst();
								}
							}
							catch(NoSuchElementException e)
							{
								//logger.debug "No more lines"
							}
						}
					}
				}
				catch(Exception e)
				{
						logger.error "Error on the writting thread: ${e}"
						logger.error "${e.getStackTrace()}"
				}
				//logger.debug "Sleep"
				Thread.sleep(10000);
			}
		}
    }
   	
   	public String getVersion()
	{
		return this.version;
	}    

    public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception {
        logger.info "Inside Process Message"
        //logger.debug "Contents of message ${message}";
        message.put("error.code",0)
        message.put("error.message","");
        String record = message.get("recordKey");
        //logger.debug "Message: ${record}";
        String sPath = message.get('inputPath');
		String regExSeparator = "\\"+java.io.File.separator
		if(regExSeparator == "\\/"){
			regExSeparator = "/"
		}
		
        String outFile = sPath.replaceAll('incoming'+regExSeparator,'outcoming'+regExSeparator+'out_');
        logger.debug "Outfile == ${outFile}";
        String errFile = sPath.replaceAll('incoming'+regExSeparator,'error'+regExSeparator+'err_');
        message.put("errFile",errFile);
        if(message.get('recordId_Key')== 1 ){
	        WrittingBuffer.getInstanceByte().addLine(outFile, message.get('header'), true)
	    }
        int daLogLevel = daDefLogLevel;
        
        try{
          message.put("callID","" + (new java.util.Date()).getTime() + ": ");
          message.put('initMilis',System.currentTimeMillis());
          
          /*Init TimeAgent*/
          TimeAgent tagent = new TimeAgent(message.get("callID"));
          tagent.init()
          message.put('timeAgent',tagent);
		  
		 
          logger.info("Encoding is :$encoding");
		  byte[] dataAreas = record.getBytes(encoding);
		  
		  String hex = stringToHex(dataAreas);
		  
		  logger.debug "About to call DA with ${message.get('data')} "
		  logger.debug "HEX: ${hex} "
		  
		  Integer retCode = DAAgnostic.byteInterface.execute(dataAreas,encoding.getBytes(),daLogLevel);
	  
		  
		  logger.debug "Retcode ${retCode} :: ${new String(dataAreas)}"
		  message.put("error.code","BB${retCode}")
		  
		  WrittingBuffer.getInstanceByte().addLine(outFile, new String(dataAreas,encoding), false)
          tagent.stop("BB"+message.get("error.code"));
          tagent.trace();
          
          //if(jmx) JMXHelper.addexecution(alias,JMXHelper.BATCH,"BB"+message.get("error.code"),new Long(tagent.getTotalTime()));
          
        }catch(Exception e){
              logger.error message.get("callID") + "An unexpected error has happened. Error returned: " + e.getMessage();
              logger.error message.get("callID") + e.getStackTrace();
              logger.error message.get("callID") + "Index: "+message.get('recordId_Key');
              message.put("error.code",10)
              message.put("error.message","An unexpected error has happened. Error returned: " + e.getMessage())
			  WrittingBuffer.getInstanceByte().addLine(outFile, "BB10. An unexpected error has happened" + new String(dataAreas,encoding), false)
			  return TR_SUCCESS;
        }finally{
          message.remove('timeAgent');
        }
        //logger.debug("END OF VALIDATION")
        
        
        return TR_SUCCESS;
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