package com.experian.util


// Logger
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import groovy.util.slurpersupport.GPathResult;

public class XSDDefinition {
	
	private static Hashtable types = new Hashtable();
	private static ExpLogger logger = new ExpLogger(XSDDefinition.class);
	
	synchronized public static void loadDefinition(GPathResult xsd, String alias){
		try{
		
		 Hashtable areaType = null;
		 def root = xsd;
		 xsd.children().each{
			 
			 if(it.name()=="element" && it.@name.text()=="DAXMLDocument")
				 root = it;
			 
		 }
		 
		 root.complexType.all.children().each { area ->
			  
			  logger.debug "Creating types for ${alias+'-'+area.@name.text()}"
			 
			  areaType = new Hashtable();
			  
			  area.complexType.all.children().each{
			 
				  logger.debug 'it.@name.text() = ' +  it.@name.text();
				  def type = it.@type.text();
				  if(type == null || type == ""){
					if(isArray(it)){
					   //logger.debug "It's an array ${it}"
					   type = 'array#'+it.complexType.all.children()[0].@type.text();
					}else{ //If not an array, it's a subnode
					  // logger.debug "${it.@name.text()} its a subnode"
					   def nodePrefix = it.@name.text() + ".";
					   def subnodeDefinition = loadSubnodeDefinition(it,nodePrefix)
					   areaType.putAll(subnodeDefinition)
					   type=''
					}
				  }
				  if(type!=''){
					areaType[it.@name.text()] = type;
				  }
			
			  }
			  types[alias+"-"+area.@name.text()] = tableToString(areaType);
			  
		 }
		}catch(Exception e){
		  logger.error e.getMessage() + " on ${alias}";
		}
		
		logger.error "${types}"
		
	  }
	
	 private static String tableToString(Hashtable tb){
		 StringBuilder sb = new StringBuilder(10000);
		 logger.error "${tb}";
		 sb.append("&")
		 tb.keySet().each{
			 sb.append(it);
			 sb.append(">")
			 sb.append(tb[it]);
			 sb.append("&");
		 }
		 return sb.toString();
	 }
	  
	  synchronized private static Hashtable loadSubnodeDefinition(GPathResult xsd, String nodePrefix){
		  Hashtable ret = new Hashtable();
		  xsd.complexType.all.children().each() {
			  //logger.debug nodePrefix + ':::it.@name.text() = ' +  it.@name.text();
			  def type = it.@type.text();
			  //logger.debug "Type=${type}"
			  if(type == null || type == ""){
				if(isArray(it)){
				   //logger.debug "It's an array"
				   type = 'array#'+it.complexType.all.children()[0].@type.text();
				}else{ //If not an array, it's a subnode
				   //logger.debug "${it.@name.text()} its a subnode"
				   def subNodePrefix = nodePrefix + (it.@name.text()) + ".";
				   def subnodeDefinition = loadSubnodeDefinition(it,subNodePrefix)
				   ret.putAll(subnodeDefinition)
				   type=''
				}
			  }
			  if(type!='')
				ret[nodePrefix + it.@name.text()] = type;
		  }
		  return ret;
	  }
	  
	  public static String getDefinition(String alias, String area, String field){
		  long init = System.currentTimeMillis();
		  def ret = types[alias+"-"+area];
		  //logger.error ">>>ret";
		  int i = ret.indexOf("&"+field+">")+("&"+field+">").length();
		  //logger.error ">>> ${i}"
		  //logger.error "${ret.substring(i)}";
		  def x = ret.substring(i,ret.indexOf("&",i));
		  logger.error "====${System.currentTimeMillis()-init}"
		  return x;
	  }
	 
	  
	  private static boolean isArray(GPathResult xsd){
		//logger.debug "Checking if ${xsd.@name.text()} is an Array with ${xsd.complexType.all.children()[0].@name.text()}"
		
		return (xsd.complexType.all.children()[0].@name.text() == 'I1')
	  }

}
