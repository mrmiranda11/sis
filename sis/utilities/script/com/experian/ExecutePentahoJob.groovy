package com.experian;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent
import com.experian.util.SimpleDateFormatThreadSafe;
// Tenant.properties
import static com.experian.eda.enterprise.properties.PropertiesRegistry.getTenantProperties;
import com.experian.eda.enterprise.startup.InterfaceBrokerProperties;
import com.experian.util.MyInterfaceBrokerProperties;
// Logger class
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
// DA-Utils
import com.experian.damonitoring.FileHandler;
// Pentaho classes
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.JobMeta;
// Date Formatter
import java.text.DateFormat;
import java.text.ParseException;
// MD5
import com.experian.util.MD5;

public class ExecutePentahoJob implements GroovyComponent<Message> 
{
    private static final ExpLogger logger = new ExpLogger(this);

	protected static String version = "2.11";
    
	public ExecutePentahoJob()
	{
		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);
		
		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + " md5checksum: " + checksum);	
	}
	
	public String getVersion()
	{
		return this.version;
	}	
	
    public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception 
    {
        String alias = message.get("alias");
        HashMap<String,String> params = loadPentahoParams(alias);
		String date = message.get("date");
		if (date != null)
		{
			try 
			{
				formatDate(date);
			}
			catch (ParseException e)
			{
				message.put("data", "Date must be in \"yyyy-mm-dd\" format");
				return "success";
			}
			catch (Exception e)
			{
				message.put("data", e.getMessage());
				return "success";
			}
		}
        LinkedHashMap<String, String> files = loadFiles(alias, date, params);
        JobMeta jobMeta = getJobMeta(params);
        for (String key : files.keySet())
        {
          logger.info("Loading table \"" + key + "\"");
          addJobParameter(jobMeta, "current_table", key);
		  addJobParameter(jobMeta, "current_file", files.get(key));
          if (params.get("db_type").equals("oracle"))
		  {
			addJobParameter(jobMeta, "curr_table_short", (key.length() > 28 ? key.substring(0,28) : key));
		  }
          Job job = new Job(null, jobMeta);
          job.start();
          job.waitUntilFinished();
		  //int error = job.getErrors();
		  long error = job.getResult().getNrErrors()
		  
          if (error != 0)
		  {
			message.put("data", "Pentaho Job returned error code = " + error + ", please check logs for errors");
			logger.error("Pentaho Job returned error code = " + error + ", please check logs for errors");
            return "success";
		  }
        }
		message.put("data", "Pentaho Execution finished successfully");
		logger.info("Pentaho Execution finished successfully");
        return "success";
    }
    
    HashMap<String, String> loadPentahoParams(String alias)
    {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("db_type", getTenantProperties().get(alias + ".DBTYPE"));
        map.put("db_server", getTenantProperties().get(alias + ".DBSERVER"));
        map.put("db_database", getTenantProperties().get(alias + ".DBNAME"));
        map.put("db_port", getTenantProperties().get(alias + ".DBPORT"));
        map.put("db_user", getTenantProperties().get(alias + ".DBUSER"));
        map.put("db_pass", getTenantProperties().get(alias + ".DBPASSWORD"));
        map.put("script_files_dir", MyInterfaceBrokerProperties.getPropertyValue("PENTAHOSCRIPTFILESDIR"));
        map.put("source_files_dir", getTenantProperties().get(alias + ".CSVPATH"));
        map.put("bad_files_dir", getTenantProperties().get(alias + ".BADFILESPATH"));
        map.put("file_type", getTenantProperties().get(alias + ".FILETYPE"));
        map.put("operation_type", getTenantProperties().get(alias + ".SYSTEMTYPE").equals("INSERT") ? "insert" : "merge");
		String tmp = getTenantProperties().get(alias + ".LOGLEVEL");
		map.put("write_to_log", (tmp == null || tmp.isEmpty()) ? "" : "_debug");
		String dateformat = getTenantProperties().get(alias + ".DATEFORMAT");
		if (dateformat == null)
		{
			map.put("date_format", getTenantProperties().get(alias + ".SYSTEMTYPE").equals("CMDS") ? "yyyyMMdd" : "yyyy/MM/dd HH:mm:ss");
		}
		else
		{
			map.put("date_format", dateformat);
		}
		map.put("log_files_dir", InterfaceBrokerProperties.getProperty("client.solution.home") + "\\logs");
		String delimiter = getTenantProperties().get(alias + ".DELIMITER");
		if (delimiter == null)
			delimiter = ",";
		map.put("delimiter", delimiter);
		map.put("log_date", formatDate())
        return map;
    }
    
    private LinkedHashMap<String, String> loadFiles(String alias, String date, HashMap<String,String> params)
    {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        FileHandler fh = new FileHandler();
        Integer Period;
		String filename;
        String period = getTenantProperties().get(alias + ".Period");
        if (period != null && period.equals("MONTHLY"))
            Period = 2;
        else
            Period = 1;
        
		String[] tables = getTenantProperties().get(alias + ".TABLES").split(",");
        for (int i = 0; i < tables.length; i++)
        {
          String tablename = tables[i];
		  if (date != null)
			filename = fh.getFilename(tablename, date, false);
          else
			filename = fh.getFilename(tablename, 1, Period, false);
		  
		  if (checkFile(params.get("source_files_dir") + "\\" + filename))
			map.put(tablename, filename);
        }
        
        return map;
    }
    
    private void renameFiles(HashMap<String, String> map, String folder)
    {
      File dir = new File(folder);
      File[] files = dir.listFiles();
      for (int i = 0; i < files.length; i++)
      {  
          if (files[i].isDirectory())
            continue;
          String name = files[i].getName().split(" ")[0];
          if (map.containsValue(name + ".CSV"))
            files[i].renameTo(new File(folder + "\\" + name + ".CSV"));
      }
    }
    
	private JobMeta getJobMeta(HashMap<String, String> params)
	{
		logger.info("Initializing the KettleEnvironment");
		KettleEnvironment.init();
		logger.info("Initializing jobMeta");        
		JobMeta jobMeta = new JobMeta(params.get("script_files_dir") + "\\main_etl.kjb", null);
		for (String key : params.keySet())
		{
			String value = params.get(key);
			logger.info("Adding Parameter \"" + key + ", value:\"" + value + "\"");
			addJobParameter(jobMeta, key, value);
		}
		return jobMeta;
	}
    
    private void addJobParameter(JobMeta jobMeta, String name, String value)
    {
      if (jobMeta.getParameterValue(name).equals(null))
        jobMeta.addParameterDefinition(name, value, "Description");
      else
        jobMeta.setParameterValue(name, value);
    }

	private void formatDate(String str)
	{		
		DateFormat formatter = new SimpleDateFormatThreadSafe("yyyy-MM-dd");
		Date date = (Date) formatter.parse(str);
	}

	private String formatDate()
	{
		DateFormat formatter = new SimpleDateFormatThreadSafe("yyyy-MM-dd");
		return "." + formatter.format(new Date());
	}
	
	private boolean checkFile(String file)
	{
		File f = new File(file);
		if (f.exists() && f.isFile())
			return true;
		return false;
	}
}