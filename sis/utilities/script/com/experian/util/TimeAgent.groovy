package com.experian.util;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;


import java.util.*;
                                                                                      
public class TimeAgent implements Serializable{

  public final String INIT = "INIT"
  public final String END = "END"

  private List points;
  private Hashtable times;
  private long init;
  private String id;
  private long end;
  private String result;
  protected static final ExpLogger  logger     = new ExpLogger(this); 
    
  
  public TimeAgent(String id){
    times = new Hashtable();
    points = new ArrayList()
    this.id = id;
  }     
  
  public void init(){
    init = System.currentTimeMillis();
  }
  
  public void init(long init){
    this.init = init;
  }
  
  public void stop(String result){
    end = System.currentTimeMillis();
    this.result = result;
  }
  
  public void addPoint(String point){
    times[point] = System.currentTimeMillis();
    points.add(point);
  }
  
  public void trace(){
     String tr = "";
     tr += "${id}PERF> ${result}\t${end - init}\t";
     
     long diff = 0;
     long pt = init;
     points.each(){
        diff = times[it] - pt;
        tr += "${it},${diff}\t"
        pt = times[it]
     }
     
     diff = end - pt;
     
     tr += "${END},${diff}";
     logger.info tr
  }
  
  public long getTotalTime(){
      return end - init;
  }


}