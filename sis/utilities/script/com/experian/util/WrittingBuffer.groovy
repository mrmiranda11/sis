package com.experian.util;



import java.util.*;
                                                                                      
public class WrittingBuffer{

	
	public Hashtable files = new Hashtable();
	private static WrittingBuffer instance = new WrittingBuffer();
	private static WrittingBuffer instanceByte = new WrittingBuffer();
	
	private WrittingBuffer(){}
	
	public static WrittingBuffer getInstance(){
		
		return instance;
		
	}

	public static WrittingBuffer getInstanceByte(){

		return instanceByte;

	}
	
	public synchronized void addLine(String file, String line, boolean isHeader){
		
		if(files[file] == null){
			files[file] = new ResponseFileBuffer();
			((ResponseFileBuffer)files[file]).fileName = file;
		}
		if(isHeader)
			((ResponseFileBuffer)files[file]).header = line;
		else
			((ResponseFileBuffer)files[file]).lines.add(line);
		
	}
	
}