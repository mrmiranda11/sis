package com.experian;

import com.experian.damonitoring.*;
import com.experian.eda.enterprise.core.api.Message;
import com.experian.eda.enterprise.script.groovy.GroovyComponent;
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;
import com.experian.stratman.datasources.runtime.IData;
import com.experian.util.MyInterfaceBrokerProperties;
import com.experian.util.FullMonitoring;
import com.experian.util.MD5
import com.experian.util.SimpleDateFormatThreadSafe;

import java.text.DateFormat;
import java.util.concurrent.ConcurrentLinkedQueue;
import static com.experian.eda.enterprise.properties.PropertiesRegistry.getTenantProperties;

public class DaResultToCSV implements GroovyComponent<Message>
{
	protected static final ExpLogger  logger       = new ExpLogger(this);
	HashMap<String, DaConfig> strategies = new HashMap<String, DaConfig>();
	FileHandler fh = new FileHandler();
	Queue queue; //queue for DirectMontiroing
	Queue queue2; //queue for FullMonitoring
	Boolean isThreadStarted = false;
	protected static String version = "2.11";
	boolean isReason;

	public DaResultToCSV()
	{
		Integer Period;

		/* Printing checksum */
		File groovyFile = new File(getClass().classLoader.resourceLoader.loadGroovySource(getClass().name).getFile().replaceAll("%20"," "))
		String checksum = MD5.getMD5Checksum(groovyFile);

		logger.warn("Starting " + getClass().getName() + " version-" + getVersion() + "md5checksum: " + checksum);

		String lstStrategies = MyInterfaceBrokerProperties.getPropertyValue("da.forceInitStrategies");
		String[] lstToInit = lstStrategies.split("\\|",-1);
		for (int i = 0; i < lstToInit.length; i++)
		{
			if (queue == null)
			{
				queue = new ConcurrentLinkedQueue();
			}

			String strategy = lstToInit[i];
			String monitor = getTenantProperties().get(strategy + ".MONITORING");
			if (monitor != null && monitor.equals("YES"))
			{
				logger.info("Initializing and caching parameters for Strategy \"" + strategy + "\" from tenants.properties...");
				String path = getTenantProperties().get(strategy + ".CSVPATH");
				if (path == null)
				{
					logger.error("Missing parameter \"CSVPATH\" for strategy " + strategy + "... Skipping");
					continue;
				}

				String delimiter = getTenantProperties().get(strategy + ".DELIMITER");
				if (delimiter == null)
					delimiter = ",";

				DaConfig dc = new DaConfig(path, delimiter, getPeriod(strategy));
				HashMap<String, String[]> params = getTenantParameters(strategy);
				if (params == null)
				{
					logger.error("Problem loading required Parameters for stratedy " + strategy + "...Skipping");
					continue;
				}

				dc.parameters = params;
				loadColumns(dc);
				HashMap<String, BufferedWriter> files = getFiles(strategy, dc);
				if (files == null)
					continue;
				closeFiles(files);
				strategies.put(strategy, dc);
			}
		}

		// if there is any Strategy monitored then start the background thread
		if (strategies.size() > 0)
		{
			new Thread(new Runnable()
			{
				public void run()
				{
					logger.warn("Starting DirectMonitoring file Extraction Thread...");
					while (true)
					{
						Integer size = queue.size();
						DaResult dr;
						if (size > 0)
						{
							for (String strategy : strategies.keySet())
							{
								DaConfig dc = strategies.get(strategy);
								dc.files = getFiles(strategy, dc);
							}

							while ((dr = (DaResult) queue.poll()) != null)
							{
								try
								{
									DaConfig dc = strategies.get(dr.strategy);
									dr.writeToFile(dc.Delimiter, dc.files);
								}
								catch (Exception e)
								{
									logger.error("Error writing monitoring data for Application ID: " + dr.appId + " reason : " + e.getMessage());
									e.printStackTrace();
								}
							}

							for (String strategy : strategies.keySet())
							{
								DaConfig dc = strategies.get(strategy);
								closeFiles(dc.files);
							}

							logger.info("Finished Extraction process, extracted " + size + " applications, sleeping 10 second...");
						}

						Thread.sleep(10000);
					}

					logger.warn("Stopping file Extraction Thread");

				}
			}).start();
			logger.info("Initialization finished.");
		}
	}

	public String getVersion()
	{
		return this.version;
	}

	public String processMessage(final Message message, final Map<String, String> dataMap) throws Exception
	{
		String result;
		isReason = false;
		int errorCode = message.get("error.code");
		if (errorCode > 0)
			return "success";

		//message.get("timeAgent").addPoint("CSV BEGIN");
		String alias = message.get("alias");
		IData[] daAreas = message.get("DAareas");

		if (message.get("Monitoring").equals("DirectMonitoring"))
			result = processDirectMonitoring(alias, daAreas);
		else {

			result = processFullMonitoring(alias, daAreas);
		}
		//message.get("timeAgent").addPoint("CSV END");

		return result;
	}

	private String processDirectMonitoring(String alias, IData[] daData)
	{
		try
		{
			String BIZKEY = null;
			String timestamp = null;
			DaResult dr;

			if (!strategies.containsKey(alias))
			{
				return "success";
			}

			if (daData == null)
				return "success";

			String transegDataName = MyInterfaceBrokerProperties.getPropertyValue("da.monitoring.area");
			if (transegDataName == null)
				transegDataName = "TRANSEG";

			/* Getting BIZKEY value and creating DaResult object*/
			Map contents = getContents(daData, transegDataName);
			BIZKEY = getValue(contents, "BIZKEY", null);
			def auxDate = getValue(contents, "TIMESTAMP", null)
			//logger.debug "${auxDate} is ${auxDate.class}"
			timestamp = formatDate(getValue(contents, "TIMESTAMP", null));

			DaConfig dc = strategies.get(alias);
			dr = new DaResult(BIZKEY, alias, dc.Columns.size(), dc.AdditionalColumns.size());
			dr.setDecisionDate(timestamp);

			for (int i = 0; i < daData.size(); i++)
			{
				IData daElement = daData[i];
				if (!daElement.getLayout().equals(transegDataName))
				{
					contents = daElement.areaContents_;
					//printContents(contents, BIZKEY);
					loadDaResult(dc, contents, dr, timestamp);
				}
			}

			queue.add(dr);
			return "success";
		}
		catch(Exception e)
		{
			logger.error "Exception occurs writing the monitoring data: ${e}";
			logger.error "${e.getStackTrace()}"
			return "error";
		}

	}

	private String processFullMonitoring(String alias, IData[] daData)
	{
		System.out.println "Starting full monitoring for $alias"
		FullMonitoring fm;
		if (queue2 == null)
		{
			queue2 = new ConcurrentLinkedQueue();
		}

		if (!isThreadStarted)
		{
			new Thread(new Runnable()
			{
				public void run()
				{
					//System.out.println("Starting FullMonitoring file Extraction Thread...");
					while (true)
					{
						Integer size = queue2.size();
						if (size > 0)
						{
							HashMap files = new HashMap<String, BufferedWriter>();
							BufferedWriter bw;
							while ((fm = (FullMonitoring) queue2.poll()) != null)
							{
								//System.out.println "FM: ${fm.data}"
								try
								{
									if (!files.containsKey(fm.alias))
									{
										String outDir = MyInterfaceBrokerProperties.getPropertyValue(fm.alias + '.outfile.dir');
										String date = new SimpleDateFormatThreadSafe("yyyyMMdd").format(new Date());
										File fOut = new File(outDir, fm.alias + "_" + date + ".csv");
										fOut.getParentFile().mkdir();
										//System.out.println "File created"
										bw = new BufferedWriter(new FileWriter(fOut, true));
										//System.out.println "BW > $bw"
										if (fOut.size() == 0) // New
										{
											//System.out.println "Writting header"
											bw.write(fm.header);
											bw.write("\n");
										}

										//System.out.println "Saving file"
										files.put(fm.alias, bw);
									}
									else
									{
										bw = files.get(fm.alias);
										//System.out.println "Read saved file $bw"

									}
									//logger.debug "Writting data ${fm.data}"
									bw.write(fm.data);
									bw.write("\n");
								}
								catch (Exception e)
								{
									logger.error "Error building flat buffer $e > ${e.getStackTrace()}";
								}
							}
							closeFiles(files);
							logger.debug("Finished FullMonitoring Extraction process, extracted " + size + " applications, sleeping 10 second...");
						}
						Thread.sleep(10000);
					}
					logger.warn("Stopping FullMonitoring Extraction Thread");
				}

			}).start();

			isThreadStarted = true;
		}

		fm = buildFlatBuffer(alias, daData);
		queue2.add(fm);
		return "success";
	}

	void printContents(Map contents, String appId)
	{
		logger.debug "*** PRINT CONTENTS for Monitoring variables ***"
		for (String key : contents.keySet())
		{
			String value = toString(contents.get(key));

			if (!(value.isEmpty()))
			//System.out.println(key + ":" + value)
				logger.debug  "appID - ${key}:${value}"
		}
	}

	HashMap<String, String[]> getTenantParameters(String alias)
	{
		String value;
		HashMap<String, String[]> params = new HashMap<String, String[]>();

		value = getTenantProperties().get(alias + ".lastdecision");
		if (value == null)
		{
			logger.error("ERROR: Missing mandatory Parameter \"lastdecision\" for strategy " + alias);
			return null;
		}
		addParameters(0, "lastdecision", params, alias);

		value = getTenantProperties().get(alias + ".lastreasoncode");
		if (value != null)
			addParameters(0, "lastreasoncode", params, alias);

		value = getTenantProperties().get(alias + ".Components");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "component", params, alias);

		value = getTenantProperties().get(alias + ".Decisions");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "decision", params, alias);

		value = getTenantProperties().get(alias + ".DerivedDatas");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "deriveddata", params, alias);

		value = getTenantProperties().get(alias + ".ScoreCards");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "scorecard", params, alias);

		value = getTenantProperties().get(alias + ".Treatments");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "treatment", params, alias);

		value = getTenantProperties().get(alias + ".ValueSetters");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "valuesetter", params, alias);

		value = getTenantProperties().get(alias + ".ValueSetterVars");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "valuesettervar", params, alias);

		value = getTenantProperties().get(alias + ".AdditionalColumns");
		if (value != null && Integer.parseInt(value) > 0)
			addParameters(Integer.parseInt(value), "additionalcolumn", params, alias);

		return params;
	}

	void loadColumns(DaConfig dc)
	{
		HashMap<String, String[]> parameters = dc.parameters;
		Integer loopIndex;
		dc.AdditionalColumns.put(1, "APPLICATION_DATE");
		for (String key : parameters.keySet())
		{
			String[] params = parameters.get(key);
			String type = params[params.length-2];
			Integer index = Integer.parseInt(params[params.length-1]);
			if (type.equals("deriveddata"))
			{
				dc.Columns.put(index, params[2]);
			}
			if (type.equals("additionalcolumn")) {
				dc.AdditionalColumns.put(index + 1, params[0]);
			}
			if (type.equals("scorecard"))
			{
				if (params.length > 6 && params[4] != null && !params[4].isEmpty())
				{
					loopIndex = Integer.parseInt(params[4].split(",")[0]);
					if (loopIndex > 1)
					{
						dc.isScorecardLoop = true;
					}
				}
			}
			if (type.equals("decision"))
			{
				if (params.length > 5 && params[3] != null && !params[3].isEmpty())
				{
					loopIndex = Integer.parseInt(params[3].split(",")[0]);
					if (loopIndex > 1)
					{
						dc.isDecisionLoop = true;
					}
				}
			}
		}

	}

	boolean isLoop(String[] params, String type)
	{
		Integer loopIndex;
		if (type.equals("scorecard"))
		{
			if (params.length > 6 && params[4] != null && !params[4].isEmpty())
			{
				loopIndex = Integer.parseInt(params[4].split(",")[0]);
				if (loopIndex > 1)
				{
					return true;
				}
			}
			return false;
		}
		if (type.equals("decision"))
		{
			if (params.length > 5 && params[3] != null && !params[3].isEmpty())
			{
				loopIndex = Integer.parseInt(params[3].split(",")[0]);
				if (loopIndex > 1)
				{
					return true;
				}
			}
			return false;
		}
		return false;
	}

	void closeFiles(HashMap<String, BufferedWriter> files)
	{
		for (String file : files.keySet())
		{
			BufferedWriter bf = files.get(file);
			bf.close();
		}
	}

	Integer getPeriod(String alias)
	{
		String period = getTenantProperties().get(alias + ".PERIOD");
		if (period != null && period.equals("MONTHLY"))
			return 2;
		else
			return 1;
	}

	HashMap<String, BufferedWriter> getFiles(String alias, DaConfig dc)
	{
		HashMap<String, BufferedWriter> map = new HashMap<String, BufferedWriter>();
		String[] tables = getTenantProperties().get(alias + ".TABLES").split(",");
		String type;
		for (int i = 0; i < tables.length; i++)
		{
			String table = tables[i];
			if (table.equals("APPLICATION_SCORECARD") || table.equals("APPLICATION_SCORECARD_RESULTS"))
			{
				if (dc.isScorecardLoop)
				{
					type = "TYPE";
				}
			}
			else if (table.equals("APPLICATION_DECISION_SETTER") || table.equals("APPLICATION_REASON_CODE"))
			{
				if (dc.isDecisionLoop)
				{
					type = "TYPE";
				}
			}
			else
			{
				type = null;
			}
			try
			{
				BufferedWriter bf = fh.getWriter(dc.Path, table, table.equals("APPLICATION_DERIVED_DATA_COL") ? dc.Columns : null,
						dc.Period, dc.Delimiter, dc.AdditionalColumns, type, false);
				map.put(table, bf);
			}
			catch (Exception e)
			{
				logger.error("Problem Creating files for Strategy: " + alias);
				logger.error("SDS Monitoring will not be activated for the Strategy: " + alias);
				logger.error(e.getMessage());
				e.printStackTrace();
				return null;
			}
		}
		return map;
	}

	void addParameters(Integer count, String type, HashMap<String, String[]> params, String alias)
	{
		String param;
		String[] parts;
		if (count == 0)
		{
			param = getTenantProperties().get(alias + "." + type)+ ";" + type + ";1";
			parts = param.split(";");
			params.put(type, parts);

		}
		else
		{
			for (int i = 0; i < count; i++)
			{
				param = getTenantProperties().get(alias + "." + type+(i+1))+ ";" + type + ";" + (i+1);
				parts = param.split(";");
				logger.info("Adding Parameter:" + type+(i+1) + "," + param);
				params.put(type+(i+1), parts);
			}
		}
	}

	String getValue(Map map, String key, String type)
	{
		if (map.containsKey(key))
		{
			String value = toString(map.get(key));
			if (value != null && !value.trim().isEmpty())
			{
				logger.debug("matched result for \"" + key + "\":" + value);
				return value;
			}
			else if (type != null)
			{
				if (type.equals("String") || type.equals("Date"))
					return "";
				else
					return "0";
			}
		}
		return null;
	}

	String getDate(Integer period)
	{
		SimpleDateFormatThreadSafe sdf = new SimpleDateFormatThreadSafe(period == 1 ? "yyyyMMdd" : "yyyyMM");
		Date date = new Date();
		return sdf.format(date);
	}

	String formatDate(String str)
	{
		//DateFormat formatter = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy");

		DateFormat formatter = new SimpleDateFormatThreadSafe("EE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
		SimpleDateFormatThreadSafe sdf = new SimpleDateFormatThreadSafe("dd/MM/yyyy HH:mm:ss");
		Date date = (Date) formatter.parse(str);
		return sdf.format(date);
	}

	void loadDaResult(DaConfig dc, Map contents, DaResult dr, String timestamp)
	{
		StringBuilder sb = new StringBuilder();
		//boolean isReason = false;

		for (String key : dc.parameters.keySet())
		{
			String[] params = dc.parameters.get(key);
			String type = params[params.length-2];
			String value;
			Integer accounts;
			String Prtype;
			Integer order = Integer.parseInt(params[params.length-1]);
			boolean isloop;
			if (type.equals("scorecard"))
			{
				ScoreCard sc;
				Account acc;
				ArrayList<ScoreCardResult> array;
				isloop = isLoop(params, "scorecard");
				if (isloop)
				{
					accounts = Integer.parseInt(params[4].split(",")[0]);
				}
				else
				{
					accounts = 1;
				}

				for (int a = 0; a < accounts; a++)
				{
					if (isloop)
					{
						Prtype = getValue(contents, getLoopElement(params[4].split(",")[1], a + 1), null);
					}
					else
					{
						Prtype = getValue(contents,params[4].split(",")[1],null);
					}

					if (Prtype != null)
					{
						acc = getAccount(dr.Accounts, Prtype, dc);
						sc = getScoreCard(acc.ScoreCards, order);
					}
					//logger.error(Prtype);

					/* Loading Scorecard Data */
					for (int k = 0; k < 3; k++)
					{
						value = getValue(contents, (isloop ? getLoopElement(params[k], a+1) : params[k]), null);
						if (value != null)
						{
							if (acc == null)
							{
								acc = getAccount(dr.Accounts, Prtype, dc);
							}
							if (sc == null)
							{
								sc = getScoreCard(acc.ScoreCards, order);
							}
							sc.setParam(k, value);
						}
					}

					/* Loading Scorecard Results Data */
					String[] tabs = params[3].split(",");
					for (int i = 0; i < Integer.parseInt(tabs[1]); i++)
					{
						value = getValue(contents, (isloop ? getLoopElement(tabs[0], a+1) : tabs[0]) +"[" + (i+1) + "]", null);
						if (value != null)
						{
							array = sc.Results;

							try
							{
								ScoreCardResult scr = new ScoreCardResult(value);
								array.add(scr);
							}
							catch (Exception e)
							{
								//System.out.println(e.getMessage());
							}
						}
						else
							break;
					}
				}
			} //if type.equals("scorecard")

			if (type.equals("decision"))
			{
				Account acc;
				Decision ds;
				Reason rs;
				isloop = isLoop(params, "decision");
				String name = null, desc = null, code = null;
				String[] tabs;

				if (isloop)
				{
					accounts = Integer.parseInt(params[3].split(",")[0]);
				}
				else
				{
					accounts = 1;
				}

				for (int a = 0; a < accounts; a++)
				{
					if (isloop)
					{
						Prtype = getValue(contents, getLoopElement(params[3].split(",")[1], a + 1), null);
					}
					else
					{
						Prtype = getValue(contents, params[3].split(",")[1], null);
					}

					if (Prtype != null)
					{
						acc = getAccount(dr.Accounts, Prtype, dc);
						ds = getDecision(acc.Decisions, order, timestamp);
					}
					//logger.error(Prtype);

					value = getValue(contents, (isloop ? getLoopElement(params[0], a + 1) : params[0]), null);
					if (value != null)
					{
						if (acc == null)
						{
							acc = getAccount(dr.Accounts, Prtype, dc);
						}
						if (ds == null)
						{
							ds = getDecision(acc.Decisions, order, timestamp);
						}
						ds.setDecision(value);
					}

					if (params[1] != null && params[1].length() > 0) // decision Table is specified in tenants.properties
					{
						tabs = params[1].split(",");
						for (int i = 0; i < Integer.parseInt(tabs[1]); i++)
						{
							value = getValue(contents, (isloop ? getLoopElement(tabs[0], a + 1) : tabs[0])+"[" + (i+1) + "]", null);
							if (value != null)
							{
								//String x = value.replaceAll('^ +| +$|(  )+','$1');
								//logger.error(x);
								//String[] res = x.split("  ");
								name = value.substring(0, 20);
								desc = value.substring(20, 20);
								code = value.substring(40);
								rs = new Reason(name.trim(),code.trim());
								ds.Reasons.add(rs);
								if (i == 0)
								{
									ds.setCode(code.trim());
									if (!isReason) {
										dr.setReason(code.trim());
										//isReason = true;
									}
								}
							}
							else
								break;
						}
					}
					else // using ReasonCode Table
					{
						tabs = params[2].split(",");
						for (int i = 0; i < Integer.parseInt(tabs[1]); i++)
						{
							value = getValue(contents, (isloop ? tabs[0] + (a + 1) : tabs[0])+"[" + (i+1) + "]", null);
							if (value != null)
							{
								rs = new Reason("SYSTEM", value.trim());
								ds.Reasons.add(rs);
								if (i == 0)
								{
									ds.setCode(value.trim());
									if (!isReason) {
										dr.setReason(value.trim());
										//isReason = true;
									}
								}
							}
						}
					}
				}
			}

			if (type.equals("deriveddata"))
			{
				DerivedData dd;

				value = getValue(contents, params[0], params[3]);
				if (value != null)
				{
					dd = getDerivedData(dr.DerivedDatas, order, params);
					dd.setValue(value);
				}
			}

			if (type.equals("lastdecision"))
			{
				value = getValue(contents, params[0], null);
				if (value != null)
					dr.setDecision(value);
			}

			if (type.equals("lastreasoncode"))
			{
				value = getValue(contents, params[0], null);
				if (value != null) {
					dr.setReason(value);
					isReason = true;
				}
			}


			if (type.equals("valuesetter"))
			{
				ValueSetter vs = getValueSetter(dr.ValueSetters, order);

				for (int i = 0; i < params.length - 2; i++)
				{
					value = getValue(contents, params[i], null);
					if (value != null)
					{
						vs.setParam(i, value);
					}
				}
			}

			if (type.equals("component"))
			{
				Component cm = getComponent(dr.Components, order, params[params.length -3]);

				for (int i = 0; i < params.length - 3; i++)
				{
					value = getValue(contents, params[i], null);
					if (value != null)
					{
						cm.setParam(i, value);
					}
				}
			}

			if (type.equals("treatment"))
			{
				Treatment tr;
				value = getValue(contents, params[0], params[4]);
				if (value != null)
				{
					tr = getTreatment(dr.Treatments, order, params);
					tr.setValue(value);
				}
			}
			if (type.equals("valuesettervar"))
			{
				ValueSetterVar vr;
				value = getValue(contents, params[0], null);
				if (value != null)
				{
					vr = getValueSetterVar(dr.ValueSetterVars, order, params);
					vr.setValue(value);
				}
			}
			if (type.equals("additionalcolumn"))
			{
				String[] tabs = params[0].split(";");
				/*if (tabs[0].equals("CALL_DATE"))
				{
					value = timestamp;
				}
				else
				{
					value = getValue(contents, tabs[0], null);
				}*/
				value = getValue(contents, tabs[0], null);
				if (value != null)
				{
					dr.additionalColumns.put(order+1, value);
				}
			}
		} //for
		dr.additionalColumns.put(1,timestamp)
	}

	String getLoopElement(String element, Integer loopIndex)
	{
		StringBuilder sb = new StringBuilder();
		String[] tmp = element.split("\\.");
		Integer len = tmp.length;
		for (int i = 0; i < len; i++)
		{
			sb.append(i == 0 ? "" : ".").append(i == len -2 ? (tmp[i] + loopIndex) : tmp[i]);
		}
		return sb.toString();
	}

	Decision getDecision(HashMap<Integer, Decision> decisions, int order, String timestamp)
	{
		if (decisions.containsKey(order))
			return decisions.get(order);
		else
		{
			//logger.error("adding new decision with order \"" + order + "\"");
			Decision ds = new Decision(timestamp);
			decisions.put(order, ds);
			return ds;
		}
	}

	ScoreCard getScoreCard(HashMap<Integer, ScoreCard> scorecards, int order)
	{
		if (scorecards.containsKey(order))
			return scorecards.get(order);
		else
		{
			ScoreCard sc = new ScoreCard();
			sc.initResults();
			scorecards.put(order, sc);
			return sc;
		}
	}

	DerivedData getDerivedData(HashMap<Integer, DerivedData> deriveddatas, int order, String[] params)
	{
		if (deriveddatas.containsKey(order))
			return deriveddatas.get(order);
		else
		{
			DerivedData dd = new DerivedData(params[1], params[2], params[3], params[4], params[5]);
			deriveddatas.put(order, dd);
			return dd;
		}
	}

	ValueSetter getValueSetter(HashMap<Integer, ValueSetter> valuesetters, int order)
	{
		if (valuesetters.containsKey(order))
			return valuesetters.get(order);
		else
		{
			ValueSetter vs = new ValueSetter();
			valuesetters.put(order, vs);
			return vs;
		}
	}

	Treatment getTreatment(HashMap<Integer, Treatment> treatments, int order, String[] params)
	{
		if (treatments.containsKey(order))
		{
			return treatments.get(order);
		}
		else
		{
			Treatment tr = new Treatment(params[1], params[2], params[3], params[4], params[5], params[6]);
			treatments.put(order, tr);
			return tr;
		}
	}

	ValueSetterVar getValueSetterVar(HashMap<Integer, ValueSetterVar> valuesettervars, int order, String[] params)
	{
		if (valuesettervars.containsKey(order))
		{
			return valuesettervars.get(order);
		}
		else
		{
			ValueSetterVar vr = new ValueSetterVar(params[1], params[2], params[3], params[4], params[5], params[6], params[7], params[8]);
			valuesettervars.put(order, vr);
			return vr;
		}
	}

	Component getComponent(HashMap<Integer, Component> components, int order, String type)
	{
		if (components.containsKey(order))
		{
			return components.get(order);
		}
		else
		{
			Component dc = new Component(type);
			components.put(order, dc);
			return dc;
		}
	}

	Account getAccount(HashMap<String, Account> accounts, String type, DaConfig dc)
	{
		if (accounts.containsKey(type))
		{
			return accounts.get(type);
		}

		//logger.error("adding new Account \"" + type + "\"");
		Account ac = new Account(type, dc.isDecisionLoop, dc.isScorecardLoop);
		accounts.put(type, ac);
		return ac;
	}

	String toString	(Object obj)
	{
		if (obj == null)
			return "" ;

		if (obj instanceof BigDecimal)
		{
			return obj.toPlainString();
		}
		else
		{
			return obj.toString();
		}
		return "";
	}

	Map getContents(IData[] areas, String layout)
	{
		for (int i = 0; i < areas.size(); i++)
		{
			if (areas[i].getLayout().equals(layout))
				return areas[i].areaContents_;
		}
		return null;
	}

	FullMonitoring buildFlatBuffer(String alias, IData[] datas)
	{
		String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
		String fieldSeparator = MyInterfaceBrokerProperties.getPropertyValue('field.separator');
		if (fieldSeparator == null || fieldSeparator.isEmpty())
		{
			fieldSeparator = "\t";
		}

		StringBuffer sb = new StringBuffer(300000);
		StringBuffer sbHeader = new StringBuffer(300000);
		logger.debug "Building flat buffer >> "
		datas.eachWithIndex
				{ it, ind ->
					sbHeader.append(it.toPlainHeader(fieldSeparator, applicationIdHeaderField,true));
					sb.append(it.toPlain(fieldSeparator,1, applicationIdHeaderField,false));
				}

		FullMonitoring fm = new FullMonitoring(alias, sbHeader.toString(), sb.toString());
		//System.out.println "Record to be saved >> ${sb.toString()}"
		return fm
	}
}
 
