package com.experian.util;

//Logger
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
// connection pool implementation
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

public class DBUtils
{
	protected static final ExpLogger logger = new ExpLogger(this);
	
	public static PoolingDataSource initSQLPoolConnection(String URL, String user, String pass)
	{
		ObjectPool connectionPool = new GenericObjectPool();
		ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(URL, user, pass);
		PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory,connectionPool, null, null, false, true);
		PoolingDataSource dataSource = new PoolingDataSource(connectionPool);
		return dataSource;
	}

}