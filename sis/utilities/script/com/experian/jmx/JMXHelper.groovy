package com.experian.jmx;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import javax.management.remote.*
import javax.management.*
import groovy.jmx.builder.*

import java.util.*; 
                                                                                      
public abstract class JMXHelper{

    public static JmxBuilder jmxBuilder = new JmxBuilder();
    public static final BATCH = "B";
    public static final ONLINE = "O";
    public static final JSON = "J";

    public static void addexecution(String alias, String type, String retCode, long time){
        String method = "addExecution";
        if(type == BATCH){
          method = "addBatchExecution"  
        }else if(type == JSON){
            method = "addJSONExecution"
        }
        jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [alias,retCode,new Long(time)] as Object[], ['java.lang.String','java.lang.String','java.lang.Long'] as String[])
    }
    
    
    public static void resetCache(String alias){
        String method = "cleanCache";
        
        jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [alias] as Object[], ['java.lang.String'] as String[])
    }
    
    public static boolean needToResetCache(String alias, String step){
        String method = "cacheCleanPending";
        
        Object obj = jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [alias, step] as Object[], ['java.lang.String','java.lang.String'] as String[])
        
        return ((Boolean)obj).booleanValue();
    }
    
    
    public static boolean cacheClenDone(String alias, String step){
        String method = "cacheClenDone";
        
        jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [alias, step] as Object[], ['java.lang.String','java.lang.String'] as String[])
        
    }
    
    public static void setReloadPropertiesVal(boolean val){
        String method = "endReloadPropertiesVal";
        
        jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [] as Object[], [] as String[])
        
    }
    
    public static void setReloadPropertiesCal(boolean val){
        String method = "endReloadPropertiesCal";
        
        jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [] as Object[], [] as String[])
        
    }
    
    public static boolean getReloadPropertiesVal(){
        String method = "getReloadPropertiesVal";
        
        jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [] as Object[], [] as String[])
        
    }
    
    public static boolean getReloadPropertiesCal(){
        String method = "getReloadPropertiesCal";
        
        jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [] as Object[], [] as String[])
        
    }

    public static String getExecutionCounters(){
        String method = "getExecutionCounters";

        return jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [] as Object[], [] as String[])

    }

    public static String getExecutionCountersByCode(){
        String method = "getExecutionCountersByCode";

        return jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [] as Object[], [] as String[])

    }

    public static Map getFilesStatus(){
        String method = "getFilesStatus";

        return jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [] as Object[], [] as String[])

    }

    public static Map setStatus(String filename,String status){
        String method = "setStatus";

        return jmxBuilder.getMBeanServer().invoke(new ObjectName('jmx.builder:type=NBSMManager'), method, [filename,status] as Object[], ['java.lang.String','java.lang.String'] as String[])

    }

    public static Map getAllPropertiesMBean(String mbeanname){

        def ret = [:]
        def objName = new ObjectName(mbeanname)
        MBeanInfo mbeaninfo = jmxBuilder.getMBeanServer() getMBeanInfo(objName)
        mbeaninfo.attributes.each{
            ret.put(it.getName(),jmxBuilder.getMBeanServer().getAttribute(objName,it.getName()));
        }
        return ret

    }
    
    
    
  
  
   


}