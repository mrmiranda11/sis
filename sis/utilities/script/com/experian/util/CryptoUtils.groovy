package com.experian.util;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import com.experian.eda.enterprise.cryptomanager.CryptoSys;


                                                                                      
public class CryptoUtils{

  protected static final ExpLogger  logger       = new ExpLogger(this); 
    
  public static String decrypt(String enc,String keyFile){
    try{
      if(!enc.startsWith("ENC{")){
        return enc;
      }
      String passwordEnc = enc.substring(4,enc.length()-1);
      logger.debug "passwordEnc = ${passwordEnc}"
      CryptoSys sys = new CryptoSys();
      logger.debug "sys  = ${sys}"
      return new CryptoSys(keyFile).decryptData(passwordEnc);
     }catch(Exception e){
        logger.error "Error decrypting data ${e.getMessage()}; ${e.getStackTrace()}"
     }
  }
  
  
  public static String encrypt(String str,String keyFile){
    try{
      CryptoSys sys = new CryptoSys();
      logger.debug "sys  = ${sys}"
      return new CryptoSys(keyFile).encryptData(str);
     }catch(Exception e){
        logger.error "Error encrypting data ${e.getMessage()}; ${e.getStackTrace()}"
     }
  }

  
  

}