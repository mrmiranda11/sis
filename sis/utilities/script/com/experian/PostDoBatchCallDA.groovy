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
public class PostDoBatchCallDA implements GroovyComponent<Message> {

	protected static final ExpLogger  logger       = new ExpLogger(this);
	protected static final String     TR_SUCCESS   = "success";
	protected static String version = "2.11";

	public PostDoBatchCallDA()
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
	 *  @param genericFile
	 *  @param dataMap
	 *  @exception Exception
	 *  @returns success String
	 *
	 */
	public String processMessage(final Object genericFile, final Map<String, String> dataMap) throws Exception {

		logger.debug "PostDoBatchCallDA>>"
		// Close the output file
		DoBatchCallDA.closeOutputFile();
		logger.debug "Successfully close the file";

		return TR_SUCCESS;
	}

	public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception	{
		logger.debug "Calling processMessage(message)";
	}


}