package com.experian.util;

import com.experian.eda.component.decisionagent.IExecutableStrategy
import com.experian.eda.decisionagent.discovery.Strategy
import com.experian.eda.decisionagent.discovery.codegen.CodeGeneratorSupportException
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import org.apache.commons.io.IOUtils

public abstract class SMCodeGenerator {

	final protected File serFile;
	final protected String outFolder;
	final protected String alias;
	protected static final ExpLogger logger     = new ExpLogger(this);

	public SMCodeGenerator(File serFile,String alias) {
		this.serFile = serFile;
		this.alias = alias
	}

	String generate(String encoding) throws IOException, CodeGeneratorSupportException {
		logger.debug "Generating code for serfile [$serFile]"
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(serFile));
		IExecutableStrategy execStrategy;
		try {
			execStrategy = (IExecutableStrategy) ois.readObject();
		} catch (ClassNotFoundException e) {
			throw new CodeGeneratorSupportException("Unable to find required class for code generation",e);
		} finally {
			ois.close();
		}
		Strategy strategy = new Strategy(execStrategy);
		Map<String, InputStream> map = getGeneratorMap(execStrategy, strategy);

		def content = IOUtils.toString(map[this.alias], encoding)

		return content;

	}

	protected abstract Map<String, InputStream> getGeneratorMap(IExecutableStrategy execStrat, Strategy strategy);
}
