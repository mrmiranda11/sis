package com.experian;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;
// Logger
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import java.io.*;
import java.util.*;
import java.text.*;
import com.experian.util.*;

import javax.management.*
import groovy.jmx.builder.*
import com.experian.jmx.*;
import org.apache.commons.lang.StringEscapeUtils;

/**
 *  BuildResponseMessage
 *  <p>
 *  This class builds the response message based on:
 *  <ul>
 *   <li>If the validator returns an error, it builds an error message </li>
 *   <li>If not, it uses the DataAreas returned by DoCAllDA to build a response message</li>
 *   </ul>
 *   
 *  @version 1.0
 *  @author David Teruel     
 *        
 *
 *
 */                                                                                         
public class BuildResponseMessage implements GroovyComponent<Message> 
{

    protected static final ExpLogger  logger     = new ExpLogger(this); 
    protected static final String     TR_SUCCESS = "success";
    protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
	protected static String version = "2.11";

	public BuildResponseMessage()
	{
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
    public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception
	{

		try
		{

			def soapVersion = MyInterfaceBrokerProperties.getPropertyValue("wsdl.version."+message['alias'])
			if(soapVersion == null || soapVersion.trim() == '' || soapVersion.trim() == 'null' || (soapVersion.trim() != '1.1' && soapVersion.trim() != '1.2')){
				soapVersion = '1.2'
			}
			if (message.get("error.code") != 1000){
            logger.debug "Building response message";
            logger.debug "Message ${message}"
            def tagent = message.get("timeAgent")
            def responseXML = buildResponse(message)
            
            logger.debug "ResponseXML = ${responseXML}"
             
            StringBuffer sb = new StringBuffer();
            if(message.get("REST") != "Y"){
              if(message.get("soap_pre") == null){
				  if(soapVersion == '1.2') {
					  sb.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?> <soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><soap:Body>")
				  }else{
					  sb.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?> <soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><soap:Body>")
				  }
              }else{
                sb.append(removeHeader(addXSINamespace(message.get("soap_pre"))))
              }
            }
            sb.append(responseXML)
            if(message.get("REST") != "Y"){
              
              if(message.get("soap_post") == null)
                sb.append("</soap:Body></soap:Envelope>")
              else
                sb.append(message.get("soap_post"))
            }  
            logger.debug message.get("callID") + sb.toString()
            
            message.put('data',sb.toString());
			
			def headers = ['Cache-Control':'no-cache']
		    
			logger.debug "ANTES ${message['http.response.headers']}"
			message.put("http.response.headers",headers);
			logger.debug "DESPUES ${message['http.response.headers']}"
            
			//logger.debug "Content type en message " + message['Content-Type'];

			if(soapVersion == '1.2') {
				message.put("contentType", "application/soap+xml")
			}else{
				message.put("contentType", "text/xml")
			}
            
            tagent.stop("WS"+message.get("error.code"));
            tagent.trace();
            message.remove("xmlDocument")
            if(jmx)
			{
				JMXHelper.addexecution(message.get("alias"),JMXHelper.ONLINE,"WS"+message.get("error.code"),new Long(tagent.getTotalTime()));
			}
          } 
		  else 
		  {
            message.put('data',"OK");
          }
        }
		catch(Exception e)
		{
            logger.error message.get("callID") + "Error building response: " + e.getMessage() + " :: " + message.get('DAareas')
			logger.error "${e.getStackTrace()}"
            logger.error message.get("callID") + "DAXMLDocument message received by calling system: " + message.get('xmlDocument')
        }
        return TR_SUCCESS;
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
		String transegDataName = MyInterfaceBrokerProperties.getPropertyValue("da.monitoring.area");
		String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
	  
		String mandatoryFields = MyInterfaceBrokerProperties.getPropertyValue('mandatory.additionalfields');
		String[] manFields = null;
		if (mandatoryFields!=null && mandatoryFields.trim() != "")
		{
			manFields = mandatoryFields.split("\\|",-1);
		}
	  
		String optionalFields = MyInterfaceBrokerProperties.getPropertyValue('optional.additionalfields');
		String[] optFields = null;
		if (optionalFields!=null && optionalFields.trim() != "")
			optFields = optionalFields.split("\\|",-1);

		// begin - change for v1.1
		String excludetags = MyInterfaceBrokerProperties.getPropertyValue('EXCLUDE_TAGS');
		String[] exclTags = null;
		if (excludetags != null && excludetags.trim() != "")
			exclTags = excludetags.split("\\|",-1);
		// end - change for v1.1		
	  
		HashMap<String,Integer> lists = new HashMap<String,Integer>();
		StringBuffer sb = new StringBuffer(300000);
		String item
		int itemIndex
		String namespace = message.get('namespace');
		String namespaceDc = message.get('namespaceDec');
		if (namespace==null)
		{
			namespace = '';
			namespaceDc = '';
		}
		else
		{
		   namespaceDc = " xmlns:"+namespace+"=\""+namespaceDc+"\"";
		   namespace += ":";
		}
      
		if (message.get("REST") == "Y")
		{  
		  namespaceDc = " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
		}
		if (message.get("error.code") ==0)
		{
			//logger.debug  message.get("callID") + "Successful call to DA"
			sb.append("<${namespace}DAXMLDocument${namespaceDc}>")
			
			// Insert an error code even if no error in DA execution
			// Ex: Error Code = X100
			//     Error Message = Success
			String insertError = MyInterfaceBrokerProperties.getPropertyValue('sis.fields.occurence');
		  
			message.get("DAareas").each 
			{
				logger.debug("Area to be inserted in Response: " + it.layoutName_)
				if(it.layoutName_ != transegDataName && !isExcluded(it.layoutName_, exclTags))
				{
					sb.append(it.toXML(manFields,optFields,applicationIdHeaderField));
				}
				message.get('timeAgent').addPoint("RESP_${it.layoutName_}")
			}
			sb.append("</${namespace}DAXMLDocument>")
		 } 
		 else if(message.get("error.code")==20 && message.get('DAareas')!=null) 
		 {
			//logger.debug  message.get("callID") + "Error call to DA"
			//logger.debug message.get("callID") + " AREAS: " + message.get('DAareas') 
			sb.append("<${namespace}DAXMLDocument${namespaceDc}>")
			sb.append(message.get('DAareas')[0].toXML(manFields,optFields, applicationIdHeaderField, true))
        
			sb.append("</${namespace}DAXMLDocument>")
		 }
		 else
		 {
			sb.append("<${namespace}DAXMLDocument${namespaceDc}><OCONTROL>")
			def alias = message.get("alias")
			def signature = message.get("signature")
          
			def xml = message.get("data");
          
			//logger.debug "XML::::" + xml

			if(alias == null || alias == '') 
			{
				if(xml != null)
				{
					def aliasInit = xml.indexOf("<ALIAS>") + 7;
					def aliasEnd = xml.indexOf("</ALIAS>");
               
					//logger.debug "Indexes de alias ${aliasInit} - ${aliasEnd}";
					if (aliasInit > 6 && aliasEnd > 0)
					{
						alias = xml.substring(aliasInit,aliasEnd);
					}
					else
					{
						alias = 'unknown'
					}
				}
				else
				{
					alias = 'unknown'
				}
			}
          
			if(signature == null || signature == '') 
			{
				if(xml != null)
				{
					def signatureInit = xml.indexOf("<SIGNATURE>") + 11;
					def signatureEnd = xml.indexOf("</SIGNATURE>");
              
					if(signatureInit > 10 && signatureEnd > 0)
					{
						signature = xml.substring(signatureInit,signatureEnd);
					}
					else
					{
						signature = 'unknown'
					}
				}
				else
				{
					signature = 'unknown'
				}
			}
          
			sb.append("<ALIAS>${StringEscapeUtils.escapeXml(alias)}</ALIAS>")
			sb.append("<SIGNATURE>${signature}</SIGNATURE>")
			//sb.append("<SIGNATURE>${StringEscapeUtils.escapeXml(signature)}</SIGNATURE>")
       
			def error_code =  message.get('error.code');
			String sErrorCode = "";
			if (error_code < 10)
				sErrorCode = "0"+ error_code
			else
				sErrorCode = error_code
			sb.append("<ERRORCODE>WS${sErrorCode}</ERRORCODE>")
			sb.append("<ERRORMSG><![CDATA[${message.get('error.message')}]]></ERRORMSG>")
			manFields.each 
			{
				def fieldInit = xml.indexOf("<${it}>")
				def fieldEnd = xml.indexOf("</${it}>")+"</${it}>".length();
				if (fieldInit >  0 && fieldEnd > "</${it}>".length()-1)
				{
					sb.append(xml.substring(fieldInit,fieldEnd));
			    }
				else
				{
					sb.append("<${it}/>");
				}
			}

			sb.append("</OCONTROL></${namespace}DAXMLDocument>")
		 }
		
		 return sb.toString().replaceAll("<data_type>array</data_type>","");
      
	}
    
    private String addXSINamespace(String header)
	{
		if (header.indexOf("xmlns:xsi=")!=-1) 
			return header;
	/*
		else if(header.indexOf("soap:Envelope") != -1) return header.replaceAll("soap:Envelope ","soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
		else if(header.indexOf("soap:envelope") != -1) return header.replaceAll("soap:envelope ","soap:envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "); 
		else if(header.indexOf("soapenv:Envelope") != -1) return header.replaceAll("soapenv:Envelope ","soapenv:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
		else if(header.indexOf("soapenv:envelope") != -1) return header.replaceAll("soapenv:envelope ","soapenv:envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "); 
	*/
		else
		{
			logger.debug "header: ${header}";
			int init = header.toUpperCase().indexOf("ENVELOPE");
			String retHeader = header.substring(0,init+8) + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " + header.substring(init+8);
		}       
	}
    
    
    private String removeHeader(String msg)
	{
        try
		{
            int headerInit = msg.toUpperCase().indexOf("HEADER");

            if (headerInit == -1)
			{
				return msg
			}
            int aux = headerInit;

            while(aux >= 0 && msg.charAt(aux) != '<')
			{
              aux--;
            }
            String tns = msg.substring(aux+1,headerInit).toUpperCase()
            
            headerInit = aux;
            String headerEndTag = "</"+tns+"HEADER>";
            int headerEnd = msg.toUpperCase().indexOf(headerEndTag)+headerEndTag.length();
            
            logger.debug "To remove ${msg.substring(headerInit,headerEnd)}"
            
            String ret = msg.substring(0,headerInit);
            ret += msg.substring(headerEnd);
            
            return ret;
            
         }
		 catch(Exception e)
		 {
            return msg;
         }
    }
	
    private void addexecution(String alias)
	{
        def mbeans =   new JmxBuilder().getMBeanServer();
        // Get the MBeanServer.
        logger.debug "Total MBeans: ${mbeans.MBeanCount}\n"
        mbeans.invoke(new ObjectName('jmx.builder:type=NBSMManager'), "addExecution", [alias] as Object[], ['java.lang.String'] as String[])
    }
	
	// begin - change for v1.1
	
	private boolean isExcluded(String tag, String[] tags)
	{
		if (tags == null)
			return false;
		for (int i = 0; i < tags.length; i++)
		{
			if (tag.equalsIgnoreCase(tags[i]))
				return true;
		}
		return false;
	}
	// End - change for v1.1

}	