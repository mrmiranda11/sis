package com.experian;

import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;


import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import static com.experian.eda.enterprise.properties.PropertiesRegistry.getTenantProperties;
import com.experian.util.MyInterfaceBrokerProperties;
import com.experian.util.MD5;

public class MonitoringJunction implements GroovyComponent<Message>
{
	private static final ExpLogger logger = new ExpLogger(this);
	protected static String version = "2.11";
    
	public MonitoringJunction()
	{
		//Printing checksum //
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
        String monitor = getTenantProperties().get(alias + ".MONITORING");
		String outfile = MyInterfaceBrokerProperties.getPropertyValue(message.get("alias") + '.outfile.activated');
		//System.out.println "$alias: Outfile $outfile"
        if (monitor != null && monitor.equals("YES"))
		{
			message.put("Monitoring", "DirectMonitoring");
            return "yes"
		} 
		else if (outfile != null && outfile.equals("Y"))
		{
			message.put("Monitoring", "FullMonitoring");
			return "yes"
		}
        else
            return "no"
    }
}