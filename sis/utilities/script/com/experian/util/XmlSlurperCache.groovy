package com.experian.util;


import java.util.*;
                                                                                      
public class XmlSlurperCache implements Serializable{

  private static Stack cache = new Stack();
  private static XmlSlurperCache instance = null;
  
  private XmlSlurperCache(){
  }     
  
  public static XmlSlurperCache getInstance(){
    if(instance == null){
      instance = new XmlSlurperCache();
    }
    return instance;
  }
  
  
  public synchronized XmlSlurper getXmlSlurper(){
    if(cache.empty()){
      XmlSlurper sl           = new XmlSlurper(); 
      return sl;
    }
    else{
      return  (XmlSlurper)cache.pop();
    }
  }
  
  public synchronized void free(XmlSlurper sl){
    cache.push(sl);
  }
  

}