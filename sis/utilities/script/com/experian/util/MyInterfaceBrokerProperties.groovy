package com.experian.util;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties;

import java.util.*;
                                                                                      
public class MyInterfaceBrokerProperties{

  public static Properties prop = null;
  protected static final ExpLogger  logger       = new ExpLogger(this)
  private static String home = InterfaceBrokerProperties.getProperty("client.solution.home")
  
  static{
    reloadProperties();
  } 
  
  public static void reloadProperties(){
    prop = new Properties();
    try{
        InputStream inputStream = MyInterfaceBrokerProperties.class.getClassLoader().getResourceAsStream("system.properties");
        if (inputStream == null) {
          throw new FileNotFoundException("Error loading system.properties file");
        }
        prop.load(inputStream);
        inputStream.close();
        logger.debug "System properties file loaded"
      }catch(Exception e){
        logger.error "Error loading properties files: ${e.getMessage()}; ${e.getStackTrace()}"
      } 
  }
  
  public static String getPropertyValue(String key){
    String ret = prop.getProperty(key).toString();
    def init = ret.indexOf('${client.solution.home}') 
    if(init > -1){
      ret = ret.substring(0,init) + home + ret.substring(init+23)  
    }
    return ret;
  }


}


/*

  private static Properties prop = null;  
  
  static{
     reloadProperties();
  }
  
  public static String getProperty(String key){
    return prop?.getProperty(key)
  }
  
  public static void reloadProperties(){
    prop = new Properties();
    
  }
  */