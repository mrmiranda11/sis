package com.experian.util;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import java.io.*;
import java.util.*;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties;
                                                                                      
public class PropertiesUtils implements GroovyComponent<Message>{ 

    protected static final ExpLogger  logger         = new ExpLogger(PropertiesUtils.class); 
    public static       Properties prop              = new Properties();
    public static       Hashtable  aliasTranslation  = new Hashtable();
    public static       Hashtable  authMethods       = new Hashtable();
    public static       Properties authUsersProp     = new Properties();
    public static       Hashtable  authUsers         = new Hashtable();
    public static       Hashtable  authPass          = new Hashtable();
    public static       String     keyfile           = InterfaceBrokerProperties.getProperty('authentication.keyfile');
    
    
    public static loadProperties(){
       InputStream inS = PropertiesUtils.class.getClassLoader().getResourceAsStream("decisionagent.properties");
        prop.load(inS);
        inS.close();
        try{
           String auxTrans = InterfaceBrokerProperties.getProperty('alias.translation');
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
           logger.info "ALIAS TRANS: ${aliasTranslation}";
        }catch(Exception e){
            logger.error "Error creating translation table ${e.getMessage()} + ${e.getStackTrace()}";
        }
        
        try{
          String aux = InterfaceBrokerProperties.getProperty('authentication.method');
          logger.debug "authentication.method = ${aux}"
          
          if(aux != null || aux != ''){
            String[] parts = aux.split("\\|",-1);
            parts?.each{
              def auth = it.split(":",-1);
              logger.debug "Auth = ${auth}"
              authMethods[auth[0]] = auth[1];
            }
          }
        }catch(Exception e){
          logger.error "Error configuring authentication methods: ${e.getMessage()}; ${e.getStackTrace()}"
        }
        
        
        try{
          InputStream inputStream = PropertiesUtils.class.getClassLoader().getResourceAsStream("users");
          if (inputStream == null) {
            throw new FileNotFoundException("property file 'users' not found in the classpath");
          }
          authUsersProp.load(inputStream);
          logger.debug "authUsersProp loaded ${authUsersProp}"
        }catch(Exception e){
          logger.error "Error configuring authentication users: ${e.getMessage()}; ${e.getStackTrace()}"
        }
    }
    
    static{
        loadProperties();
    }
    
    public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception {return "sucess"}
    
  

}