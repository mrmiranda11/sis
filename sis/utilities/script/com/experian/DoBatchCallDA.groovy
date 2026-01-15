package com.experian;

import groovy.jmx.builder.*
import groovy.util.slurpersupport.GPathResult


import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import com.experian.jmx.*
import com.experian.stratman.datasources.runtime.IData
import com.experian.util.*
import groovy.transform.*
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;


public class DoBatchCallDA implements GroovyComponent<Message> 
{
	protected static final ExpLogger  logger       = new ExpLogger(this);
	protected static final String     TR_SUCCESS   = "success";
	protected static       Hashtable  XSD_cache    = new Hashtable();
	protected static       Hashtable  File_cache   = new Hashtable();
	protected static       Properties prop = new Properties();
	protected static       String     encoding = "ISO-8859-1";
	protected static       int daDefLogLevel = 0;
	protected static       String fieldSeparator = "\t";
	protected static       JmxBuilder jmxBuilder = new JmxBuilder();
	protected static       Hashtable typeCache = new Hashtable();
	protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
	protected static       String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
	protected static String version = "2.11";
	protected static       Hashtable aliasTranslation = new Hashtable();
	public static FileOutputStream outFileStream;
	public static String fileHeader;
	public static File fOut;
	public static String outFile;
	public static File fErr;
    public static FileOutputStream errFileStream;
    public static String errFile;
	
	public DoBatchCallDA()
	{
		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);
		
		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);
		
		trans.put('xs:string',1)
		trans.put('decimal-or-empty',2)
		trans.put('xs:decimal',3)
		trans.put('xs:integer',3)
		trans.put('xs:date',4)
		trans.put('xs:dateTime',4)
		
		InputStream inS = DoBatchCallDA.class.getClassLoader().getResourceAsStream("decisionagent.properties");
		prop.load(inS);
		inS.close();
		String sDaLogLevel = InterfaceBrokerProperties.getProperty("da.log.level");
		fieldSeparator = InterfaceBrokerProperties.getProperty("field.separator");
		daDefLogLevel = Integer.parseInt(sDaLogLevel)

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

	}
	
	public String getVersion() {
		return this.version;
	}
	
	public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception {
		//logger.debug "Contents of message ${message}";
		message.put("error.code",0)
		message.put("error.message","");
		String record = message.get("recordKey");
		//logger.debug "Message: ${record}";
		String sPath = message.get('inputPath');
		String regExSeparator = "\\"+java.io.File.separator
		if(regExSeparator == "\\/") {
			regExSeparator = "/"
		}
		outFile = sPath.replaceAll('incoming'+regExSeparator,'outcoming'+regExSeparator+'out_');
		//logger.debug "Outfile == ${outFile}";
		String errFile = sPath.replaceAll('incoming'+regExSeparator,'error'+regExSeparator+'err_');
		message.put("errFile",errFile);
		if(message.get('recordId_Key')== 1 ) {
			// WrittingBuffer.getInstance().addLine(outFile, message.get('header'), true)
			fileHeader = message.get('header');
		}
		int daLogLevel = daDefLogLevel;
		
		try
		{
		  message.put("callID","" + (new java.util.Date()).getTime() + ": ");
		  message.put('initMilis',System.currentTimeMillis());
		  
		  /*Init TimeAgent*/
		  TimeAgent tagent = new TimeAgent(message.get("callID"));
		  tagent.init()
		  message.put('timeAgent',tagent);
		  
		  //logger.debug "Before split"
			String splitSep = fieldSeparator;
			logger.debug ">> ${splitSep == "|"}"
			if(splitSep == "." || splitSep == "|"){
				splitSep = "\\$splitSep"
			}
			logger.debug ">> SplitSep = $splitSep"
		  String[] parts = record.split(splitSep,-1);
		  logger.debug "Record: ${record} +  parts: ${parts}";
		  if(parts.length < 2)
		  {
			  logger.error message.get("callID") + "Message doesn't have the minimum length";
			  message.put("error.code",1)
			  message.put("error.message","Message doesn't have the minimum length");
			  recordError(message);
			  return TR_SUCCESS;
		  }
		  
		  String alias = parts[0];
		  String signature = parts[1];
		  message.put('alias',alias);
		  message.put('signature',signature);
		  
		  
		  if(alias == null || alias.trim() == '')
		  {
			  message.put("error.code",2)
			  message.put("error.message","Input message is not valid. Error returned: No ALIAS element can be found.")
			  recordError(message);
			  return TR_SUCCESS;
		  }
			def aliasTrans = alias;
			if(aliasTranslation[alias] != null){
				aliasTrans = aliasTranslation[alias]
			}

		  def xsd = getXMLDefinition(aliasTrans,alias, message)
		  if(typeCache[alias] == null)
		  {
			  message.put("error.code",3)
			  message.put("error.message","No definition xml file found for alias ${alias}");
			  recordError(message);
			  return TR_SUCCESS;
		  }
		  
		  
		  List dictList = new ArrayList()
		  typeCache[alias].each
		  {
			 dictList.add(it[0].toString());
		  }
		  DataArea controlData = new DataArea("OCONTROL", logger);
		  
		  controlData.setValue("ALIAS", alias, true);
		  controlData.setValue("SIGNATURE", signature, true);
		  
		  controlData.setValue("EDITION", null, false);
		  controlData.setValue("OBJECTIVE", null, false);
		  controlData.setValue("EDITIONDATE", null, false);
		  controlData.setValue("ERRORCODE", null, false);
		  controlData.setValue("ERRORMSG", null, false);
		  
		  boolean isAppIdHeader = false
		  if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != "" && applicationIdHeaderField.trim() != "null")
		  {
			  controlData.setValue(applicationIdHeaderField, parts[2], false);
			  isAppIdHeader = true;
			  try
			  {
			  	daLogLevel = Integer.parseInt(parts[3])
			  }
			  catch(Exception e){}
		  }
		  else
		  {
		  	try
			{
			  	daLogLevel = Integer.parseInt(parts[2])
			}
			catch(Exception e){}
		  }
		  controlData.setValue("DALOGLEVEL", daLogLevel, false);		  
		  
		  
		  IData[] inputOutputAreas = createInputAreas(message, alias,parts, dictList, null,isAppIdHeader);
		  
		  
		  IData[] areas = new IData[dictList.size()];
		  int i = 1;
		  areas[0] = (IData)controlData;
		  inputOutputAreas.each
		  {
			areas[i++] = it
		  }
		  
		  if(message.get("error.code") != 0)
		  {
			recordError(message);
			return TR_SUCCESS;
		  }
		  
		  message.get('timeAgent').addPoint("INP");
		  int returnCode = 0;
		  
		  try
		  {
			 if(areas == null)
				logger.error  "Areas are empty"
				
			  //logger.debug "Areas before ${areas}"
			  logger.info "Areas before ${areas}"
					
			  returnCode = DAAgnostic.objectInterface.execute(areas, daLogLevel, message.get("callID"));
			  if(returnCode !=0) 
			  {
				  throw new Exception("The strategy execution returned an error. Please check DA logs for details: " + returnCode)
			  }			  
			  //logger.debug "Areas after ${areas}"
			  message.put('DAareas',areas);
		  }
		  catch(Exception iee)
		  {
			 logger.error message.get("callID") + "Error getting access to DA interface: ${iee.getMessage()}"
			 message.put("error.code",21);
			 message.put("error.message","Unable to connect to DA");
			 recordError(message);
			 return TR_SUCCESS;
			 
		  }
		  String errorCountStr = (String)controlData.getValue("ERRORCOUNT", true);
		  if(errorCountStr != null)
		  {
			int errorCount = Integer.parseInt(errorCountStr);
			message.get('timeAgent').addPoint("CALLDA");
			if (errorCount > 0)
			{
				//logger.debug message.get("callID") + "SM returned ${errorCount} error(s)";
				String errorCodesSM = "";
				for (int errorIndex = 1; errorIndex <= errorCount; errorIndex++) 
				{
					errorCodesSM += (String)controlData.getValue("ERROR[${errorIndex}]", true)
					if(errorIndex < errorCount)
					{
						errorCodesSM += ','
					}
				}
				//logger.error message.get("callID") + "SM error codes: ${errorCodesSM}";
				message.put("error.code",20);
				message.put("error.message",errorCodesSM);
			}
		  }
		  else
		  {
			 logger.error message.get("callID") + "Invalid call to DA interface (ERRORCOUNT is null)"
			 message.put("error.code",22);
			 message.put("error.message","Invalid call to DA interface");
			 recordError(message);
			 return TR_SUCCESS;
		  }
		  String outputStr = buildResponse(message);
		  outputStr = outputStr.substring(0,outputStr.length()-1);
		  message.put("DAareas","");
		  
		  //logger.debug "OutputString ${outputStr}"
		  //File fOut = new File(outFile);
		  //logger.debug "Record id = #" + message.get('recordId_Key') + "#";
		  
		  //fOut.append(outputStr+"\n");
		  //WrittingBuffer.getInstance().addLine(outFile, outputStr, false)
		  String outFileStr = outputStr+"\n";

	      // if file is closed or file is never iitialized yet then open the file stream
	      synchronized(DoBatchCallDA.class) {
		      if (outFileStream == null) {     	
			 	fOut = new File(outFile + ".tmp");
		      	outFileStream = new FileOutputStream(fOut, true);

		      	// Write the header
		      	if (fileHeader != "") {
			  		String outputHeader = fileHeader+"\n";
					outFileStream.write(outputHeader.getBytes());		
					if (jmx) { JMXHelper.setStatus(outFile,"PROCESSING"); }
					fileHeader = "";
	  			}
		      }
		  }
			String batchEncoding = InterfaceBrokerProperties.getProperty("batchfile.encoding");
			String daEncoding = InterfaceBrokerProperties.getProperty("da.encoding");
			
			if(batchEncoding == null)
			{
				batchEncoding = "ISO8859-1";
			}
			
			if(daEncoding == null)
			{
				daEncoding = "ISO8859-1";
			}
			byte[] encoding1 = outFileStr.getBytes(batchEncoding);     
			//logger.info("encoding is : ${encoding1}")		
			String outFileStrEnc = new String(encoding1, daEncoding);
			//logger.info("Outpur File Str: ${outFileStrEnc}")

		  synchronized(outFileStream) {
	      	outFileStream.write(outFileStrEnc.getBytes(daEncoding));
	      }  
		  tagent.stop("BF"+message.get("error.code"));
		  tagent.trace();
		  
		  if(jmx) JMXHelper.addexecution(alias,JMXHelper.BATCH,"BF"+message.get("error.code"),new Long(tagent.getTotalTime()));
		  
		}catch(Exception e){
			  logger.error message.get("callID") + "An unexpected error has happened. Error returned: " + e.getMessage();
			  logger.error message.get("callID") + e.getStackTrace();
			  logger.error message.get("callID") + "Index: "+message.get('recordId_Key');
			  message.put("error.code",10)
			  message.put("error.message","An unexpected error has happened. Error returned: " + e.getMessage())
			  recordError(message);
			  return TR_SUCCESS;
		}finally{
		  message.remove('timeAgent');
		}
		//logger.debug("END OF VALIDATION")
		
		
		return TR_SUCCESS;
	}
	
	public static void closeOutputFile() {
		// have a loop here to check folder if file with same name exists, then increment it 
		// by number. probably can use regex
		if (outFileStream != null) {
			File outFileDone = new File(outFile); 
			outFileStream.close();
	        outFileStream = null;
		    if(jmx) { JMXHelper.setStatus(outFile,"FINISHED"); }
		    if (outFileDone.exists()) {
		    	  renameDupFile(outFileDone, fOut);
		    } else {
	              fOut.renameTo(outFileDone);
	        }
	    }

	    if (errFileStream != null ){
	    	File outFileErr = new File(errFile); 
	    	errFileStream.close();
	    	errFileStream = null;
	    	if (outFileErr.exists()) {
		    	  renameDupFile(outFileErr, fErr);
		    } else {
		    	  fErr.renameTo(outFileErr);
		    }
	    }

	}

	private static void renameDupFile(File outFileDone, File tmpfile) {

    	 String fileName = outFileDone.getName(); 
    	 String pattern = ".*?" + fileName.substring(0,fileName.length()-4) + ".*";
    	 File folder = new File(outFileDone.getParent());
		 File[] listOfFiles = folder.listFiles();
		 int counter = 0;
		 int lastDot = fileName.lastIndexOf('.');

		 for (int i = 0; i < listOfFiles.length; i++) {
		      if (listOfFiles[i].isFile()) { 
			      if (listOfFiles[i].getName().matches(pattern)){
			      	counter++;
			      	String newFileName = fileName.substring(0,lastDot) + "_" + counter + fileName.substring(lastDot);
		 			File newFile = new File(folder.toString() + "/" + newFileName);
		 			if (!newFile.exists()) {
		 				tmpfile.renameTo(newFile);
		 				break;
		 			}
			      }
		      } 
		  }	 
	}

	private void recordError(Message message)
	{
		StringBuffer sbError = new StringBuffer(3000);
		if(message.get('error.code')<10)
			sbError.append('BF0'+message.get('error.code'));
		else
			sbError.append('BF'+message.get('error.code'));
		sbError.append("\t");
		sbError.append(message.get('error.message'));
		sbError.append("\t");
		sbError.append(message.get('recordKey'));
		sbError.append("\n");
		  //File fErr = new File(message.get('errFile'));
		  //fErr.getParentFile().mkdirs();
		  //fErr.append(sbError.toString());
		synchronized(DoBatchCallDA.class) {
			if (errFileStream == null) {
				errFile = new String(message.get('errFile'));
				fErr = new File(message.get('errFile')+".tmp");
		        errFileStream = new FileOutputStream(fErr, true);	
	        }
        }

        synchronized(errFileStream) {
        		errFileStream.write(sbError.toString().getBytes());
        }
		//WrittingBuffer.getInstance().addLine(message.get('errFile'), sbError.toString(), false)
		 
		message.get('timeAgent').stop("BF"+message.get("error.code"));
		message.get('timeAgent').trace();
	}
	
	/**
	 * This method build the SOAP response message based on the content of the message
	 * (error.code, error.message and DAareas)<p>
	 *
	 *  @param message The container
	 *
	 *
	 */
	private String buildResponse(Message message)
	{
	  HashMap<String,Integer> lists = new HashMap<String,Integer>();
	  StringBuffer sb = new StringBuffer(300000);
	  String item
	  int itemIndex
	  
	  
	  if (message.get("error.code") == 0)
	  {
		//logger.debug  message.get("callID") + "Successful call to DA"
		message.get('DAareas').eachWithIndex 
		{ it, ind ->
			//sb.append(it.toPlain(fieldSeparator,daDefLogLevel))
			sb.append(it.toPlainWithouOrder(fieldSeparator,typeCache[message.get("alias")].get(ind)[1],daDefLogLevel, applicationIdHeaderField))
			sb.append(fieldSeparator)
		}
		
	  }
	  else if(message.get("error.code")==20 && message.get('DAareas')!=null) 
	  {
		logger.info  message.get("callID") + "Error call to DA"
		//logger.debug message.get("callID") + " AREAS: " + message.get('DAareas')
		
		message.get('DAareas').eachWithIndex 
		{ it, ind ->
		   //sb.append(it.toPlain(fieldSeparator,daDefLogLevel))
		   sb.append(it.toPlainWithouOrder(fieldSeparator,typeCache[message.get("alias")].get(ind)[1], daDefLogLevel, applicationIdHeaderField))
		   sb.append(fieldSeparator)
		}
		
	  }
	  else
	  {
		
		  def alias = message.get("alias")
		  def signature = message.get("signature")
		  
		  def xml = message.get("data");
		  
		  //logger.debug "XML::::" + xml
		  
		  sb.append(alias)
		  sb.append("\t")
		  sb.append(signature);
		  sb.append("\t")
		  sb.append("\t\t\t\t");
		  def error_code =  message.get('error.code');
		  String sErrorCode = "";
		  if (error_code < 10)
			sErrorCode = "0"+ error_code
		  else
			sErrorCode = error_code
		  sb.append("BF${sErrorCode}\t")
		  sb.append(message.get('error.message'))
		  sb.append("\t")
	  }
	  return sb.toString();
	  
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
	synchronized private void getXMLDefinition(String aliasTrans, String alias, Message message){
	   //logger.debug "typeCache[${alias}] = ${typeCache[alias]}";
	   if(typeCache[alias] != null){
		  return;
	   }
	   File fIn = null;
	   int i = 1;
	   while(true)
	   {
			String dirName = prop.getProperty(DAAgnostic.deploymentFoldersPrefix+i);
			//logger.debug "Searchin in directory " +  dirName
			if(dirName == null || dirName == '')  break;
			fIn = new File(dirName+"/"+aliasTrans+"_st.xsd")
			if(fIn.exists())
			{
			  break;
			}
			i++;
	   }
	   try
	   {
		 if(fIn!=null)
		 {
			 XmlSlurper sl = XmlSlurperCache.getInstance().getXmlSlurper();
			 def xmlDocument = sl.parseText(fIn.getText())
			 XmlSlurperCache.getInstance().free(sl);
			 List l = null;
	   		 def legacy=1337
			 def ret;
       		 def sLegacy= MyInterfaceBrokerProperties.getPropertyValue('format.xsd.'+alias) 
        	legacy = sLegacy.isInteger() ? sLegacy.toInteger() : null
              
         switch(legacy) {            

                    case 1: 
                        l = getXMLStuctureLegacy(xmlDocument);
                        break; 
                    case 2: 
                         l = getXMLStucture(xmlDocument);
                        break; 
                    case 3: 
                       l = getXMLStuctureLegacyV3(xmlDocument);
                        break; 
                    case 4: 
                        l = getXMLStucturev4(xmlDocument);
                        break; 
                    default: 
                         l = getXMLStucture(xmlDocument);
                        break; 
                }



			//logger.debug "XMLStructure: " + l;
			typeCache[alias] = l;
			return;
		 }
		 else
		 {
			logger.error message.get("callID") + "No definition xml file found"
			return;
		 }
	   }catch(Exception e)
	   {
			logger.error message.get("callID") + "No definition xml file found"
			logger.error "${e}: ${e.getStackTrace()}"
			return;
		}
	   
	}
	
	public static void loadXMLDefinition()
	{
	}
	
	/**
	 *  This method is used to return the xsd structure to the cache once is not longer needed.
	 *
	 *  @param alias The alias of the strategy
	 *  @param xml The XSD structure
	 *
	 */
	synchronized private void freeXMLDefinition(String alias, Object xml)
	{
		XSD_cache[alias].push(xml);
	}
	
	
	private IData[] createInputAreas(Message message, String strategy, String[] parts, List dictionaries, GPathResult xsd, boolean appIdHeaderPresent)
	{
	   IData[] ret = new IData[dictionaries.size()-1];
	   List<String> output = new ArrayList<String>()
	   int lastIndex = 8;

	   if(appIdHeaderPresent) lastIndex++;
	   Hashtable cache = new Hashtable();
	   cache['lastIndex'] = lastIndex;
	   try
	   {
		 dictionaries.eachWithIndex 
		 { dictionary, indx ->
			if(dictionary != "OCONTROL")
			{
				logger.debug message.get("callID") + "Creating input-output areas for " + dictionary + " with index ${indx}"
				ret[indx-1] = mapInputDictionary(message, dictionary,parts,cache, xsd, strategy,indx)

					  //message.get('timeAgent').addPoint("${dic.name()}_BUILDINPUT");
			}
			 
			 
		 }
	   }
	   catch(NullPointerException npe)
	   {
		  logger.error message.get("callID") + "WS11: Impossible to build input areas for " + strategy
		  //logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument')
		  logger.error message.get("callID") + npe.getStackTrace();
		  message.put("error.code",11)
		  message.put("error.message","Impossible to build input areas for " + strategy)
	   }
	   catch(NumberFormatException nfe)
	   {
		  logger.error message.get("callID") + "WS11: Impossible to build input areas for " + strategy + ". ${nfe.getMessage()}"
		  //logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument')
		  logger.error message.get("callID") + nfe.getStackTrace();
		  message.put("error.code",11)
		  message.put("error.message","Impossible to build input areas for " + strategy+ ". ${nfe.getMessage()}")
	   }
	   catch(Exception e)
	   {
		  logger.error message.get("callID") + "General error: " + e;
		  //logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument')
		  logger.error message.get("callID") + e.getStackTrace();
		   message.put("error.code",11)
		   message.put("error.message","Impossible to build input areas for " + strategy+ ". ${e.getMessage()}")
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
	 public IData mapInputDictionary(Message message, String id, String[] parts, Hashtable cache, GPathResult xsd, String alias,int indx) throws Exception
	 {

	    def legacy=1337
		def ret;
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

		int offset = cache['lastIndex'];

		offset += ret.fromPlain(parts,offset,(typeCache[alias].get(indx)[1]),false);
		cache['lastIndex'] = offset;
		return ret;
	 }

	private static final Hashtable trans = new Hashtable();

	public static List getXMLStucture(GPathResult xsd) throws Exception
	{
	  def nIndx = 0;
	  def root = xsd.complexType.all
	  def rootElement = root;
	  List lTypes = new ArrayList();
	  xsd.children().each 
	  { element ->
		if(element.@name.text() == 'DAXMLDocument')
		{
			rootElement = element;
		}
	  }
 
	  rootElement.complexType.all.children().each
	  { rootE ->
		 logger.debug "Entrando en ${rootE.@name.text()}";
		  StringBuilder sb = new StringBuilder(10000);
		  def dicRootElements = rootE.complexType.all;
		  String dataType = "";
		  String fieldName = "";
		  List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
		  dicRootElements.children().each 
		  { field ->
			logger.debug "Entrando en ${field.@name.text()}";
			try
			{
			  dataType = field.@type.text();
			  fieldName = field.@name.text();
			  logger.debug "dataType ${dataType} fieldName ${fieldName}"
			  if(dataType == null || dataType == '')
			  {
				if(field.complexType.sequence.children()[0].@name.text() == 'item')
				{
				    logger.debug "It's an array"
				    int arrayInx = 1;
					def arrayItem = field.complexType.sequence.children()[0]
					def maxOcc = 20;
					if(arrayItem.@maxOccurs.text() != 'unbounded'){
						maxOcc = Integer.parseInt(arrayItem.@maxOccurs.text())
					}
					for(int i=arrayInx;i<=maxOcc;i++){
						def fieldNameItem = fieldName+"[${i}]";
					    logger.debug "Adding array Item ${i} for ${fieldName} named ${fieldNameItem}"
						dataType = arrayItem.@type.text();
					    lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))
					 
				   }
				}
				else
				{
				  lTypesElem.addAll(getXMLStuctureSubnode(fieldName + ".", field));
				}
			  }
			  else
			  {
				lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
			  }
			}
			catch(Exception e)
			{
			  logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
			  throw e;
		    }
		  }
		  Object[] val = new Object[2];
		  val[0] = rootE.@name.text();
		  val[1] = lTypesElem
		  lTypes.add(val);
		}
		return lTypes;
	}
	

	
	
	public static List getXMLStuctureSubnode(String path, GPathResult xsd) throws Exception
	{
		String dataType = "";
		String fieldName = "";
		def dicRootElements = xsd.complexType.all;
		StringBuilder sb = new StringBuilder(10000);
		List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
		dicRootElements.children().each 
		{ field ->
			try
			{
			  dataType = field.@type.text();
			  fieldName = path+field.@name.text();
			  if(dataType == null || dataType == '')
			  {
				if(field.complexType.sequence.children()[0].@name.text() == 'item')
				{
				   int arrayInx = 1;
					def arrayItem = field.complexType.sequence.children()[0]
					def maxOcc = 20;
					if(arrayItem.@maxOccurs.text() != 'unbounded'){
						maxOcc = Integer.parseInt(arrayItem.@maxOccurs.text())
					}
					for(int i=arrayInx;i<=maxOcc;i++){
						def fieldNameItem = fieldName+"[${i}]";
						logger.debug "Adding array Item ${i} for ${fieldName} named ${fieldNameItem}"
						dataType = arrayItem.@type.text();
						lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))

					}
				}
				else
				{
				   lTypesElem.addAll(getXMLStuctureSubnode(fieldName + ".", field));
				}
			  }
			  else
			  {
				lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
			  }
			}
			catch(Exception e)
			{
			  logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
			  throw e;
		   
		    }
	   }
	   return lTypesElem;
    }

	public static List getXMLStuctureLegacy(GPathResult xsd) throws Exception
	{
		logger.debug("getXMLStuctureLegacy")
		def nIndx = 0;
		def root = xsd.complexType.all
		def rootElement = root;
		List lTypes = new ArrayList();
		xsd.children().each
				{ element ->
					if(element.@name.text() == 'DAXMLDocument')
					{
						rootElement = element;
					}
				}

		rootElement.complexType.all.children().each
				{ rootE ->
					//logger.debug "Entrando en ${rootE.@name.text()}";
					StringBuilder sb = new StringBuilder(10000);
					def dicRootElements = rootE.complexType.all;
					String dataType = "";
					String fieldName = "";
					List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
					dicRootElements.children().each
							{ field ->
								//logger.debug "Entrando en ${field.@name.text()}";
								try
								{
									
									fieldName = field.@name.text();
									//logger.debug "dataType ${dataType} fieldName ${fieldName}"
									dataType = field.@type.text();
									if(dataType == null || dataType == '')
									{
										if(field.complexType.all.children()[0].@name.text() == 'I1')
										{
											//logger.debug "It's an array"
											int arrayInx = 1;

											field.complexType.all.children().each()
													{arrayItem ->
														def fieldNameItem = fieldName+"[${arrayInx++}]";
														//logger.debug "Adding array Item ${arrayInx} for ${fieldName} named ${fieldNameItem}"
														dataType = arrayItem.@type.text();
														lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))

													}
										}
										else
										{
											lTypesElem.addAll(getXMLStuctureSubnodeLegacy(fieldName + ".", field));
										}
									}
									else
									{
										lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
									}
								}
								catch(Exception e)
								{
									logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
									throw e;
								}
							}
					Object[] val = new Object[2];
					val[0] = rootE.@name.text();
					val[1] = lTypesElem
					lTypes.add(val);
				}
		return lTypes;
	}


public static List getXMLStuctureLegacyV3(GPathResult xsd) throws Exception
	{
	   logger.debug("getXMLStuctureLegacyv3")
		def nIndx = 0;
		def root = xsd.complexType.all
		def rootElement = root;
		List lTypes = new ArrayList();
		xsd.children().each
				{ element ->
					if(element.@name.text() == 'DAXMLDocument')
					{
						rootElement = element;
					}
				}

		rootElement.complexType.all.children().each
				{ rootE ->
					//logger.debug "Entrando en ${rootE.@name.text()}";
					StringBuilder sb = new StringBuilder(10000);
					def dicRootElements = rootE.complexType.all;
					String dataType = "";
					String fieldName = "";
					List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
					dicRootElements.children().each
							{ field ->
								//logger.debug "Entrando en ${field.@name.text()}";
								try
								{
									dataType = field.@type.text();
									  dataType = field.@type.text()
									if(dataType  == null  || dataType  == '' )
									{
									//If not  xs:date the type is set in <restriction>
									dataType=field.simpleType.restriction.@base
									}
									fieldName = field.@name.text();
									//logger.debug "dataType ${dataType} fieldName ${fieldName}"
									if(dataType == null || dataType == '')
									{
										if(field.complexType.all.children()[0].@name.text() == 'I1')
										{
											//logger.debug "It's an array"
											int arrayInx = 1;

											field.complexType.all.children().each()
													{arrayItem ->
														def fieldNameItem = fieldName+"[${arrayInx++}]";
														//logger.debug "Adding array Item ${arrayInx} for ${fieldName} named ${fieldNameItem}"
														dataType = arrayItem.@type.text();
														if (dataType == null || dataType == "")
														{
															//If not  xs:date the type is set in <restriction>
															dataType=arrayItem.simpleType.restriction.@base.text()
														}
														lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))

													}
										}
										else
										{
											lTypesElem.addAll(getXMLStuctureSubnodeLegacyv3(fieldName + ".", field));
										}
									}
									else
									{
										lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
									}
								}
								catch(Exception e)
								{
									logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
									throw e;
								}
							}
					Object[] val = new Object[2];
					val[0] = rootE.@name.text();
					val[1] = lTypesElem
					lTypes.add(val);
				}
		return lTypes;
	}

	public static List getXMLStuctureSubnodeLegacyv3(String path, GPathResult xsd) throws Exception
	{
		String dataType = "";
		String fieldName = "";
		def dicRootElements = xsd.complexType.all;
		StringBuilder sb = new StringBuilder(10000);
		List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
		dicRootElements.children().each
				{ field ->
					try
					{
						dataType = field.@type.text();
						fieldName = path+field.@name.text();
						if(dataType  == null  || dataType  == '' )
					    {
						//If not  xs:date the type is set in <restriction>
						dataType=field.simpleType.restriction.@base
					    }
						if(dataType == null || dataType == '')
						{
							if(field.complexType.all.children()[0].@name.text() == 'I1')
							{
								int arrayInx = 1;
								field.complexType.all.children().each()
										{arrayItem ->
											def fieldNameItem = fieldName+"[${arrayInx++}]";
											dataType = arrayItem.@type.text();
											if (dataType == null || dataType == "")
											{
												
												//If not  xs:date the type is set in <restriction>
												dataType=arrayItem.simpleType.restriction.@base.text()
											}
											lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))
										}
							}
							else
							{
								lTypesElem.addAll(getXMLStuctureSubnodeLegacyv3(fieldName + ".", field));
							}
						}
						else
						{
							lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
						}
					}
					catch(Exception e)
					{
						logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
						throw e;

					}
				}
		return lTypesElem;
	}

	public static List getXMLStuctureSubnodeLegacy(String path, GPathResult xsd) throws Exception
	{
		String dataType = "";
		String fieldName = "";
		def dicRootElements = xsd.complexType.all;
		StringBuilder sb = new StringBuilder(10000);
		List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
		dicRootElements.children().each
				{ field ->
					try
					{
						dataType = field.@type.text();
						fieldName = path+field.@name.text();
						if(dataType == null || dataType == '')
						{
							if(field.complexType.all.children()[0].@name.text() == 'I1')
							{
								int arrayInx = 1;
								field.complexType.all.children().each()
										{arrayItem ->
											def fieldNameItem = fieldName+"[${arrayInx++}]";
											dataType = arrayItem.@type.text();
											lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))
										}
							}
							else
							{
								lTypesElem.addAll(getXMLStuctureSubnode(fieldName + ".", field));
							}
						}
						else
						{
							lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
						}
					}
					catch(Exception e)
					{
						logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
						throw e;

					}
				}
		return lTypesElem;
	}
	
	/*****************************************************SIS 2.9*************************************/
	public static List getXMLStucturev4(GPathResult xsd) throws Exception
	{

logger.debug "getXMLStucturev4"
	  def nIndx = 0;
	  def root = xsd.complexType.all
	  def rootElement = root;
	  List lTypes = new ArrayList();
	  xsd.children().each 
	  { element ->
		if(element.@name.text() == 'DAXMLDocument')
		{
			rootElement = element;
		}
	  }
 
	  rootElement.complexType.all.children().each
	  { rootE ->
		 logger.debug "Entrando en ${rootE.@name.text()}";
		  StringBuilder sb = new StringBuilder(10000);
		  def dicRootElements = rootE.complexType.all;
		  String dataType = "";
		  String fieldName = "";
		  List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
		  dicRootElements.children().each 
		  { field ->
			logger.debug "Entrando en ${field.@name.text()}";
			try
			{
		        dataType = field.@type.text()
                if(dataType  == null ||  dataType  == '' )
                {
                  //If not  xs:date the type is set in <restriction>
                   dataType=field.simpleType.restriction.@base.text()
                }
			  fieldName = field.@name.text();
			  logger.debug "dataType ${dataType} fieldName ${fieldName}"
			  if(dataType == null || dataType == '')
			  {
				if(field.complexType.sequence.children()[0].@name.text() == 'item')
				{
				    logger.debug "It's an array"
				    int arrayInx = 1;
					def arrayItem = field.complexType.sequence.children()[0]
					def maxOcc = 20;
					if(arrayItem.@maxOccurs.text() != 'unbounded'){
						maxOcc = Integer.parseInt(arrayItem.@maxOccurs.text())
					}
					for(int i=arrayInx;i<=maxOcc;i++){
						def fieldNameItem = fieldName+"[${i}]";
					    logger.debug "Adding array Item ${i} for ${fieldName} named ${fieldNameItem}"
						dataType = arrayItem.@type.text()
                        if(dataType == null || dataType == "")
                        {
                            //If not  xs:date the type is set in <restriction>
                            dataType=arrayItem.simpleType.restriction.@base.text()
							logger.debug("dataType not date" + dataType)
                        }
					    lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))
					 
				   }
				}
				else
				{
				  lTypesElem.addAll(getXMLStuctureSubnodev4(fieldName + ".", field));
				}
			  }
			  else
			  {
				lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
			  }
			}
			catch(Exception e)
			{
			  logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
			  throw e;
		    }
		  }
		  Object[] val = new Object[2];
		  val[0] = rootE.@name.text();
		  val[1] = lTypesElem
		  lTypes.add(val);
		}
		return lTypes;
	}
	
public static List getXMLStuctureSubnodev4(String path, GPathResult xsd) throws Exception
	{
		String dataType = "";
		String fieldName = "";
		def dicRootElements = xsd.complexType.all;
		StringBuilder sb = new StringBuilder(10000);
		List<TypeElement> lTypesElem = new ArrayList<TypeElement>();
		dicRootElements.children().each 
		{ field ->
			try
			{
			  dataType = field.@type.text()
                if(dataType  == null ||  dataType  == '' )
                {
                  //If not  xs:date the type is set in <restriction>
                   dataType=field.simpleType.restriction.@base.text()
                }
			  fieldName = path+field.@name.text();
			  if(dataType == null || dataType == '')
			  {
				if(field.complexType.sequence.children()[0].@name.text() == 'item')
				{
				   int arrayInx = 1;
					def arrayItem = field.complexType.sequence.children()[0]
					def maxOcc = 20;
					if(arrayItem.@maxOccurs.text() != 'unbounded'){
						maxOcc = Integer.parseInt(arrayItem.@maxOccurs.text())
					}
					for(int i=arrayInx;i<=maxOcc;i++){
						def fieldNameItem = fieldName+"[${i}]";
						logger.debug "Adding array Item ${i} for ${fieldName} named ${fieldNameItem}"
						dataType = arrayItem.@type.text();
                        if(dataType == null || dataType == "")
                        {
                            //If not  xs:date the type is set in <restriction>
                            dataType=arrayItem.simpleType.restriction.@base.text()
                        }
						lTypesElem.add(new TypeElement(fieldNameItem,trans[dataType]))

					}
				}
				else
				{
				   lTypesElem.addAll(getXMLStuctureSubnodev4(fieldName + ".", field));
				}
			  }
			  else
			  {
				lTypesElem.add(new TypeElement(fieldName,trans[dataType]))
			  }
			}
			catch(Exception e)
			{
			  logger.error "Error creating the object for field ${field.@name.text()}: ${e}";
			  throw e;
		   
		    }
	   }
	   return lTypesElem;
    }
	
}