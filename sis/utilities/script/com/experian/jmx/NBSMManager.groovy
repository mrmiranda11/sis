package com.experian.jmx;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import com.experian.util.MyInterfaceBrokerProperties;

import java.util.*; 
                                                                                      
public class NBSMManager{

  private HashMap<String,Long> counters = new HashMap();
  private HashMap<String,Long> batchCounters = new HashMap();
  private HashMap<String,Long> jsoncounters = new HashMap();


  private HashMap<String,Long> countersCode = new HashMap();
  private HashMap<String,Long> batchCountersCode = new HashMap();
  private HashMap<String,Long> jsonCountersCode = new HashMap();

  private HashMap<String,List> cacheCleaners = new HashMap();
  private HashMap<String,Map> batchFiles = new HashMap();
  
  private List cacheCleanHistory = new ArrayList();
  
  private static final String CACHE_XSD_VAL = "CACHE_XSD_VAL";
  private static final String CACHE_FILE = "CACHE_FILE";
  private static final String CACHE_XSD_CALLDA = "CACHE_XSD_CALLDA";
  private static final String CACHE_BLOCK_DEFINITION = "CACHE_BLOCK_DEFINITION";
  
  private long total = 0;
  private long totalBatch = 0;
  private long totalJson = 0;

  private double avg = 0.0;
  private double avgBatch = 0.0;
  private double avgJson = 0.0;

    private boolean reloadPropertiesVal = false;
  private boolean reloadPropertiesCal = false;
  
  protected static final ExpLogger  logger       = new ExpLogger(NBSMManager.class); 
  
  
  public NBSMManager(){}
  
  
  public void resetCounters(){
     counters = new HashMap();
     countersCode = new HashMap();
     total = 0;
     avg = 0;
  }
  
  public void resetBatchCounters(){
       batchCounters = new HashMap();
       batchCountersCode = new HashMap();
       totalBatch = 0;
       avgBatch = 0;
  }

    public void resetJsonCounters(){
        jsoncounters = new HashMap();
        jsonCountersCode = new HashMap();
        totalJson = 0;
        avgJson = 0;
    }
  
  
  //Attributes
  public long getExecutionCounter(String alias){
      long ret = 0;
      if(counters.containsKey(alias)) ret+=counters[alias].longValue();
      if(jsoncounters.containsKey(alias)) ret+=jsoncounters[alias].longValue();
      if(batchCounters.containsKey(alias)) ret+=batchCounters[alias].longValue();
      return ret
  }
  
  
  public String getExecutionCounters(){
    String ret = "ONLINE\n";
    counters.keySet().toArray().each{
        ret += "\t*" + it + ": \t" + counters[it].toString() + "\n";
    }
    
    ret += "BATCH\n";
    batchCounters.keySet().toArray().each{
        ret += "\t*" + it + ": \t" + batchCounters[it].toString() + "\n";
    }

      ret += "JSON\n";
      jsoncounters.keySet().toArray().each{
          ret += "\t*" + it + ": \t" + jsoncounters[it].toString() + "\n";
      }
    
    return ret;
  
  }
  
  public String getExecutionCountersByCode(){
    String ret = "ONLINE\n";
    countersCode.keySet().toArray().each{
        ret += "\t*" + it + ": \t" + countersCode[it].toString() + "\n";
    }
    
    ret += "BATCH\n";
    batchCountersCode.keySet().toArray().each{
        ret += "\t*" + it + ": \t" + batchCountersCode[it].toString() + "\n";
    }

      ret += "JSON\n";
      jsonCountersCode.keySet().toArray().each{
          ret += "\t*" + it + ": \t" + jsonCountersCode[it].toString() + "\n";
      }
    
    return ret;
  
  }
  
  
  public String getAverageTimes(){
    String ret = "ONLINE\n";
    ret += "\tTotal ${total} -> Avg ${avg}\n"
    ret += "BATCH\n";
    ret += "\tTotal ${totalBatch} -> Avg ${avgBatch}\n"
      ret += "JSON\n";
      ret += "\tTotal ${totalJson} -> Avg ${avgJson}\n"
      return ret;
  
  }
  //Methods
  public synchronized addExecution(String alias, String code, Long time){
      if(counters[alias] == null){
        counters[alias] = new Long(1);
      }else{
        counters[alias] = new Long(counters[alias].longValue() + 1);
      }
      
      if(countersCode[code] == null){
        countersCode[code] = new Long(1);
      }else{
        countersCode[code] = new Long(countersCode[code].longValue() + 1);
      }
      
      avg = ((avg * total) + time.longValue()) / (total + 1)
      total += 1;
      
             	
  }

    public synchronized addJSONExecution(String alias, String code, Long time){
        if(jsoncounters[alias] == null){
            jsoncounters[alias] = new Long(1);
        }else{
            jsoncounters[alias] = new Long(jsoncounters[alias].longValue() + 1);
        }

        if(jsonCountersCode[code] == null){
            jsonCountersCode[code] = new Long(1);
        }else{
            jsonCountersCode[code] = new Long(jsonCountersCode[code].longValue() + 1);
        }

        avgJson = ((avgJson * totalJson) + time.longValue()) / (totalJson + 1)
        totalJson += 1;


    }
  
  public synchronized addBatchExecution(String alias, String code, Long time){
      if(batchCounters[alias] == null){
        batchCounters[alias] = new Long(1);
      }else{
        batchCounters[alias] = new Long(batchCounters[alias].longValue() + 1);
      }
      if(batchCountersCode[code] == null){
        batchCountersCode[code] = new Long(1);
      }else{
        batchCountersCode[code] = new Long(batchCountersCode[code].longValue() + 1);
      }
      
      avgBatch = ((avgBatch * totalBatch) + time.longValue()) / (totalBatch + 1)
      totalBatch += 1;
      
             	
  }
  
  public synchronized void cleanCache(String alias){
  
      cacheCleanHistory.add("${alias}: Setting cache for cleaning");
      cacheCleaners[alias] = new ArrayList();
      ((List)cacheCleaners[alias]).add(CACHE_XSD_VAL)
      ((List)cacheCleaners[alias]).add(CACHE_FILE)
      ((List)cacheCleaners[alias]).add(CACHE_XSD_CALLDA)
      ((List)cacheCleaners[alias]).add(CACHE_BLOCK_DEFINITION)  
      
  
  }
  
  public synchronized boolean cacheCleanPending(String alias, String step){
     if (cacheCleaners[alias] == null) return false
     else return ((List)cacheCleaners[alias]).contains(step);
  }
  
  public synchronized void cacheClenDone(String alias, String step){
      cacheCleanHistory.add("${alias}: ${step} cleaning done");
      ((List)cacheCleaners[alias]).remove(step);
  }
  
  public String getCacheStatus(){
    String ret = "";
    cacheCleaners.keySet().toArray().each{
        ret += it + ": \t" + cacheCleaners[it].toString() + "\n";
    }
    
    return ret;
  }
  
  public void resetProperties(){
     reloadPropertiesVal = true;
     reloadPropertiesCal = true;
     MyInterfaceBrokerProperties.reloadProperties();
  }
  
  public synchronized void endReloadPropertiesVal(){
     reloadPropertiesVal = false;
  }
  
  public synchronized void endReloadPropertiesCal(){
     reloadPropertiesCal = false;
  }
  
  public synchronized boolean getReloadPropertiesVal(){
    return reloadPropertiesVal;
  }
  
  public synchronized boolean getReloadPropertiesCal(){
    return reloadPropertiesCal;
  }


  public Map getFilesStatus(){
      return batchFiles;
  }

  public void setStatus(String filename,String status){
      if(batchFiles[filename] == null){
          batchFiles.put(filename,['name':filename,'status':status,'time':new Date(System.currentTimeMillis())])
      }else{
          batchFiles[filename] = ['name':filename,'status':status,'time':new Date(System.currentTimeMillis())]
      }
  }

}