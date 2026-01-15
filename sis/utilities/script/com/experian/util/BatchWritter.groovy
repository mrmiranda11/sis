package com.experian.util;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;


public class BatchWritter implements Runnable{
	
	protected final ExpLogger  logger  = new ExpLogger(this);
	

	@Override
	public void run() {
		// TODO Auto-generated method stub
		WrittingBuffer.getInstance().files.keySet().each { 
			long timeSinceLastWrite = System.currentTimeMillis() - ((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).lastWrite;
			if(timeSinceLastWrite > 600000){
				//If the last write is older than 10 minutes, we remove it
				WrittingBuffer.getInstance().files.remove(it)
			}
			logger.debug "Writting file ${it}"
			File fout = new File(((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).fileName);
			//fout.getParentFile().mkdirs();
			if(((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).header != ""){
				fout.append(((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).header)
				((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).header = "";
			}
			
			String line = (String)((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).lines.removeFirst();
			while(line != null){
				fout.append(line);
				line = (String)((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).lines.removeFirst();
				((ResponseFileBuffer)WrittingBuffer.getInstance().files[it]).lastWrite = System.currentTimeMillis();
			}
			
			
		}
	}

  


}