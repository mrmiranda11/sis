package com.experian;

import groovy.util.slurpersupport.GPathResult

import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import com.experian.jmx.*
import com.experian.stratman.datasources.runtime.IData
import com.experian.util.*

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
public class DoCallDA implements GroovyComponent<Message> 
{

    protected static final ExpLogger  logger     = new ExpLogger(this); 
    protected static final String     TR_SUCCESS = "success";
    protected static Hashtable  XSD_cache    = new Hashtable();
	protected static Hashtable template_cache = new Hashtable();	
    protected static int daDefLogLevel = 0;
    protected static boolean DAInitialized = false;
	protected static boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
	protected static String     applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
	protected static String version = "2.11";
	
	public DoCallDA()
	{
		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);
		
		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);
		try 
		{
			reloadProperties();
			// Initialize DA
			initDA()
		} 
		catch (Throwable e) 
		{
			def errMsg = "Error during ${DoCallDA.class.simpleName} static initialization : ${e.getClass().simpleName} / $e.message"
			logger.error errMsg 
			logger.error MiscUtil.getStackTrace(e) 
			throw new RuntimeException(errMsg,e)
		}
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
     if(jmx && JMXHelper.getReloadPropertiesCal()){
         reloadProperties();
         JMXHelper.setReloadPropertiesCal(false);
      }
        
    int errorCode = message.get("error.code")
    int daLogLevel = daDefLogLevel
     
    try
	{
		if(errorCode > 0)
		{
			return TR_SUCCESS;
        }

		def xml = message.get('xmlDocument');
		XmlSlurper sl = XmlSlurperCache.getInstance().getXmlSlurper();
        def xmlDocument = sl.parseText(xml)
		XmlSlurperCache.getInstance().free(sl);
		String alias = message.get("alias")
		String signature = xmlDocument.OCONTROL.SIGNATURE.text()
		String sDaLogLevelXML = xmlDocument.OCONTROL.DALOGLEVEL.text()
		if(sDaLogLevelXML != ''){
			daLogLevel = Integer.parseInt(sDaLogLevelXML)
		}
          
          //String xmlDef = getXMLDefinition(alias,message)
          //def xsd = sl.parseText(xmlDef)
		def xsd = getXMLDefinition((message['transAlias']!=null && message['transAlias'].trim() != '')?message['transAlias']:alias,message)
        def rootElement = xsd.depthFirst().collect { it }.find { it.name() == "element" && it.@name.text() == "DAXMLDocument" }

		def dict = rootElement.complexType.all.children()
		List dictList = new ArrayList()
		dict.each{
			dictList.add(it.@name.text())
		}
  
          //logger.debug message.get("callID") + "Creating IData structures"
          
          // message.get('timeAgent').addPoint("PAR");
          
          
          //logger.debug message.get("callID") +"Creating OCONTROL structure"
        IData controlData = null;

		 def legacy=1337
        def sLegacy= MyInterfaceBrokerProperties.getPropertyValue('format.xsd.'+alias) 
        legacy = sLegacy.isInteger() ? sLegacy.toInteger() : null
              
         switch(legacy) {            

                    case 1: 
                         controlData = new DataArea("OCONTROL", logger);
                        break; 
                    case 2: 
                        controlData = new HDataArea("OCONTROL", logger);
                        break; 
                    case 3: 
                         controlData = new DataArea2("OCONTROL", logger);
                        break; 
                    case 4: 
                       controlData = new HDataArea2("OCONTROL", logger);
                        break; 
                    default: 
                         controlData = new HDataArea("OCONTROL", logger);
                        break; 
                }
		
          
		  xmlDocument.OCONTROL.children().each {
			  controlData.setValue(it.name(), it.text(), true);
		   }
 
          //logger.debug message.get("callID") + "Seting ALIAS = " + alias;
          //controlData.setValue("ALIAS", alias, true);
          //logger.debug message.get("callID") + "Seting SIGNATURE = " + signature;
          //controlData.setValue("SIGNATURE", signature, true);
          
          String transegDataName = InterfaceBrokerProperties.getProperty("da.monitoring.area");
          
          //logger.debug "Transeg Data name = ${transegDataName}"
          
          IData transegData = null;
          
          if(transegDataName != null && transegDataName != ''){
              //logger.debug "Creating transeg data area" ;
              transegData = new DataArea(transegDataName, logger)
              transegData.setValue("TIMESTAMP", new Date(System.currentTimeMillis()), true);
			  if(applicationIdHeaderField!=null && applicationIdHeaderField != "" && applicationIdHeaderField != "null"){
				  transegData.setValue("BIZKEY", xmlDocument.OCONTROL."${applicationIdHeaderField}".text(), true);
			  }else{
			  	  transegData.setValue("BIZKEY", ""+System.currentTimeMillis(), true);
			  }
          }
          
          
          //logger.debug "End creation of transeg data area" ;
          
          if(message['transAlias'] != null){
              alias = message['transAlias']
          }
          
          IData[] inputOutputAreas = createInputAreas(message, alias,(GPathResult)xmlDocument, dictList, rootElement);
          freeXMLDefinition(alias,xsd)
          
          
          int nAreas = dictList.size();
          
          if(transegData != null){
            nAreas += 1;
          }
          
          IData[] areas = new IData[nAreas];
          
          int i = 1;
          areas[0] = (IData)controlData;
         
          //logger.debug "Transeg data area = " + transegData 
          if(transegData != null){
            //logger.debug "Adding transect area"
            areas[i++] = transegData;
          }
          
		  /* getting ordererList from cached template and setting to new object*/
		  HashMap<String, List> template = template_cache.get(alias);
          inputOutputAreas.each
		  {
			//List ordererList = template.get(it.getLayout());
			//it.setOrderedKeyList(ordererList);
            areas[i++] = it
          }
          
          if(message.get("error.code") != 0){
            return TR_SUCCESS;
          }
          
          
          message.get('timeAgent').addPoint("INP");
          int returnCode = 0;
          logger.debug message.get("callID") + "Ready to call SM. About to execute call...";
          logger.debug message.get("callID") + "Areas are: " + areas
          
          try{
            if(areas == null)
                logger.error  "Areas are empty"
            
            //logger.debug "Areas before: ${areas}"

            returnCode = DAAgnostic.objectInterface.execute(areas, daLogLevel, message.get("callID"));
            logger.debug("" +returnCode);
            logger.debug "Areas after: ${areas}"
			
       }catch(Exception e){
         e.printStackTrace();
         logger.error message.get("callID") + "Error global: " +  e
         logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument')
       }

       // Check for errors
       String errorCountStr = (String)controlData.getValue("ERRORCOUNT", true);
       if(errorCountStr != null)
	   {
			int errorCount = Integer.parseInt(errorCountStr);
			message.get('timeAgent').addPoint("CALLDA");
			if (errorCount > 0) 
			{
				String errorCodesSM = "";
				for (int errorIndex = 1; errorIndex <= errorCount; errorIndex++) 
				{
					errorCodesSM += (String)controlData.getValue("ERROR[${errorIndex}]", true);
					if(errorIndex < errorCount)
					{
						errorCodesSM += ',';
					}
				}
				logger.error message.get("callID") + "SM error codes: ${errorCodesSM}";
				logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument');
				message.put("error.code",20);
				message.put("error.message",errorCodesSM);
			}	
		} 
		else 
		{
			logger.error message.get("callID") + "Invalid call to DA interface (ERRORCOUNT is null)";
			logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument');
			message.put("error.code",22);
			message.put("error.message","Invalid call to DA interface");
			return TR_SUCCESS;
		}
		
	    message.put("DAareas",areas);

	} catch (Exception e) {
		e.printStackTrace();
		logger.error(e.getMessage());
	}
    return TR_SUCCESS;
  }
    
    /**
     * This method returns the XSD structure for the specified alias. 
     * It use the information of the decisionagent.properties for load the alias_st.xsd file
     * It keeps this information cached for improving perfomance.
     * 
     * @param alias The alias of the strategy               
     * @param message The container
     *
     *
     */                        
    synchronized private Object getXMLDefinition(String alias, Message message){
      def cleanCache = jmx?JMXHelper.needToResetCache(alias,NBSMManager.CACHE_XSD_CALLDA):false; 
      logger.debug "Clean cache ${cleanCache}"
       if(XSD_cache[alias] == null || cleanCache){
          XSD_cache[alias] = new Stack();
          if(cleanCache)
          JMXHelper.cacheClenDone(alias,NBSMManager.CACHE_XSD_CALLDA)
       }
       if(!XSD_cache[alias].empty()){
          //logger.debug "The XSD for ${alias} is cached"
          return XSD_cache[alias].pop()
       }
       File fIn = null;
       int i = 1;
       while(true){
            String dirName = ((Properties)message.get('daProp')).getProperty(DAAgnostic.deploymentFoldersPrefix+i).trim();
            //logger.debug "Searchin in directory " +  dirName
            if(dirName == null || dirName == '')  break;                                  
            fIn = new File(dirName+"/"+alias+"_st.xsd")
            if(fIn.exists()){
              break;
            }
            i++;
       }
       
       if(fIn!=null){
          XmlSlurper sl = XmlSlurperCache.getInstance().getXmlSlurper();
          def xmlDocument = sl.parseText(fIn.getText())

		  /* Adding ordererList of elements to cache */
		  if (!template_cache.containsKey(alias))
		  {
			IData[] areas = SampleBuilder.buildSampleAreas(xmlDocument,alias,logger);
			HashMap<String, List> template = new HashMap<String, List>();
			for (int k = 0; k < areas.size(); k++)
			{
				template.put(areas[k].getLayout(), areas[k].getOrderedKeyList());
			}
			template_cache.put(alias, template);
			logger.debug("Caching XML template for alias $alias");
		  }
		  
          XmlSlurperCache.getInstance().free(sl);
          return xmlDocument;
       }else{
          logger.error message.get("callID") + "No definition xml file found"
          logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument')
          return null;
       }
       
    }
    
    
    /**
     *  This method is used to return the xsd structure to the cache once is not longer needed.
     *  
     *  @param alias The alias of the strategy          
     *  @param xml The XSD structure
     *
     */
    synchronized private void freeXMLDefinition(String alias, Object xml){
        XSD_cache[alias].push(xml);
    }
    
    /**
     *  This method creates the input DataAreas based on the input XML message and the 
     *  XSD structure     
     *  
     *  @param message The container
     *  @param strategy The alias of the strategy
     *  @param inputData XML structure of the input message
     *  @param dictionaries List of the block names contained in the input message
     *  @param xsd XML structure of the XSD definition   
     *  @returns IData[] List of DataAreas                 
     *
     */
    
    private IData[] createInputAreas(Message message, String strategy, GPathResult inputData, List dictionaries, GPathResult xsd){
       IData[] ret = new IData[dictionaries.size()-1];
       List<String> output = new ArrayList<String>()
       try{
         dictionaries.eachWithIndex { dictionary, indx ->
           if(dictionary != "OCONTROL"){
             logger.debug message.get("callID") + "Creating input-output areas for " + dictionary
             inputData.children().eachWithIndex { dic,idx->
                  logger.debug "Dict: --> " + dic.name()
                  if(dictionary == dic.name()) {
                      logger.debug dictionary +  " is an input dict"
                      ret[indx-1] = mapInputDictionary(message, dic.name(),(GPathResult)dic, xsd, strategy)
                      //message.get('timeAgent').addPoint("${dic.name()}_BUILDINPUT");
                  }
             }
             
             if(ret[indx-1] == null){
                //Is an output dict, and must be created empty
                 logger.debug ">> $dictionary is empty and must be created but empty"
                 ret[indx-1] = mapInputDictionaryEmpty(message, dictionary, xsd, strategy)
                 //ret[indx-1] = new DataArea(dictionary,logger)
                 //output.add(dictionary)
             }
           }
         }
       }catch(NullPointerException npe){
          logger.error message.get("callID") + "WS11: Impossible to build input areas for " + strategy
          //logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument') 
          logger.error message.get("callID") + npe.getStackTrace();
          message.put("error.code",11)
          message.put("error.message","Impossible to build input areas for " + strategy)
       }catch(Exception e){
          logger.error message.get("callID") + "General error: " + e;
          //logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument')
          logger.error message.get("callID") + e.getStackTrace();
       }
       //logger.debug message.get("callID") + "End creating input areas"
       message.put('listOutputDictionaries',output);
       return ret;
      
    }

    /**
     *  This method builds the input DataArea for one of the data blocks
     *
     *  @param message The container
     *  @param id The name of the data area
     *  @param dic The XML structure of the data area
     *  @param xsd The XML structure of the XSD
     *  @param alias The alias of the strategy
     *
     */
    public IData mapInputDictionaryEmpty(Message message, String id, GPathResult xsd, String alias) throws Exception{
        logger.debug message.get("callID") + "Creating empty input area for dictionary " + id
        //logger.debug message.get("callID") + dic.name();
        IData ret = null;
         def legacy=1337
        def sLegacy= MyInterfaceBrokerProperties.getPropertyValue('format.xsd.'+alias) 
        legacy = sLegacy.isInteger() ? sLegacy.toInteger() : null
              
         switch(legacy) {            

                    case 1: 
                        ret = new DataArea(id,logger);
                        break; 
                    case 2: 
                         ret = new HDataArea(id, logger);
                        break; 
                    case 3: 
                        ret = new DataArea2(id,logger);
                        break; 
                    case 4: 
                       ret = new HDataArea2(id, logger);
                        break; 
                    default: 
                         ret = new HDataArea(id, logger);
                        break; 
                }
        logger.debug ">>> Legacy = $legacy"

        //message.get('timeAgent').addPoint("${id}_BEFOREFROMXML");

        ret.fromXMLEmpty(xsd,id, alias, message, false);
        //logger.debug message.get("callID") + "Input area built: " + ret + " time: " + (System.currentTimeMillis() - init);
        return ret;
    }

    /**
     *  This method builds the input DataArea for one of the data blocks
     *  
     *  @param message The container
     *  @param id The name of the data area
     *  @param dic The XML structure of the data area
     *  @param xsd The XML structure of the XSD
     *  @param alias The alias of the strategy                              
     *
     */              
    public IData mapInputDictionary(Message message, String id, GPathResult dic, GPathResult xsd, String alias) throws Exception{
         logger.debug message.get("callID") + "Creating input area for dictionary " + id
         //logger.debug message.get("callID") + dic.name();
         IData ret = null;

        def legacy=1337
        def sLegacy= MyInterfaceBrokerProperties.getPropertyValue('format.xsd.'+alias) 
        legacy = sLegacy.isInteger() ? sLegacy.toInteger() : null
             
         switch(legacy) {            

                    case 1: 
                        ret = new DataArea(id,logger);
                        break; 
                    case 2: 
                         ret = new HDataArea(id, logger);
                        break; 
                    case 3: 
                        ret = new DataArea2(id,logger);
                        break; 
                    case 4: 
                       ret = new HDataArea2(id, logger);
                        break; 
                    default: 
                         ret = new HDataArea(id, logger);
                        break; 
                }
         //message.get('timeAgent').addPoint("${id}_BEFOREFROMXML");
         ret.fromXML(dic,xsd,id, alias, message, false);

         //logger.debug ">> Array sizes for $alias ::> ${ret.fieldSize_}"
         return ret;
    }
    
    synchronized static void initDA(){
      if (!DAInitialized) {
        DAInitialized = true
        try{
          String lstStrategies = InterfaceBrokerProperties.getProperty("da.forceInitStrategies")
		  if(lstStrategies==null || lstStrategies=="") return;
          def lstToInit = lstStrategies.split("\\|",-1)
          for(int i=0;i<lstToInit.length;i++) {
			if(lstToInit[i]!=null && lstToInit[i].trim() != ""){
	            try{
	              logger.info "Forcing initialization of strategy "+lstToInit[i]
	              DataArea controlData = new DataArea("OCONTROL", null)
	              // Get list of strategies configured....
	              controlData.setValue("ALIAS", lstToInit[i], false)
	              controlData.setValue("SIGNATURE", "SIGNATURE", false)
	              IData[] areas = new IData[1]
	              areas[0] = (IData)controlData
	              int returnCode = DAAgnostic.objectInterface.execute(areas, 0, "Init")
	            }catch(Exception e1){}
			}
          }
        }catch(Exception e){}
      }
    }
    
    private static void reloadProperties(){
      try {
          String sDaLogLevel = InterfaceBrokerProperties.getProperty("da.log.level");
          daDefLogLevel = Integer.parseInt(sDaLogLevel)
        } catch(Exception e) { logger.error "Error initializing ode: " + e; }
    }
	
}