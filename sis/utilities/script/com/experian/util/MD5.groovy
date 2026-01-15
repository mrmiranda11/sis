package com.experian.util;

import java.security.MessageDigest;

public class MD5
{

	public static String getMD5Checksum(File file)
	{
		StringBuilder sb = new StringBuilder();
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(readFile(file).getBytes());
		byte[] mdbytes  = md.digest();
		for (int i = 0; i < mdbytes.length; i++)
		{
			sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
		}
		
		return sb.toString();
	}
	
	private static String readFile(File file) throws IOException
	{
		StringBuilder fileContents = new StringBuilder((int)file.length());
		Scanner scanner = new Scanner(file);
		String lineSeparator = System.getProperty("line.separator");

		try {
			while(scanner.hasNextLine()) {        
				fileContents.append(scanner.nextLine() + lineSeparator);
			}
			return fileContents.toString();
		} finally {
			scanner.close();
		}
	}
}