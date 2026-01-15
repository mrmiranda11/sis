package com.experian.util


import com.experian.eda.component.decisionagent.IExecutableStrategy;
import com.experian.eda.decisionagent.discovery.JavaDataSource;
import com.experian.eda.decisionagent.discovery.Strategy;
import com.experian.eda.decisionagent.discovery.codegen.xmlinterface.CreateXMLSchema
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import org.apache.commons.io.IOUtils;

public class XMLCodeGen extends SMCodeGenerator {
    protected static final ExpLogger logger     = new ExpLogger(this);

    public XMLCodeGen(File serFile, String alias) {
        super(serFile, alias);
    }

    @Override
    protected Map<String, InputStream> getGeneratorMap(IExecutableStrategy execStrat, Strategy strategy) {
        List<JavaDataSource[]> sources = new ArrayList<>();
        int i = 0;
        JavaDataSource[] dSource;
        while ((dSource = strategy.getAllJavaDataSources(i++)) != null) {
            sources.add(dSource);
        }

        Map<String,InputStream> map = CreateXMLSchema.createXMLSchema(sources.toArray(new JavaDataSource[0][0]), outFolder, alias,
                execStrat.getRuntimeProperties().getFullBusinessObjectiveName(),
                ((Integer)execStrat.getRuntimeProperties().getEditionNumber()).toString());

        Map.Entry<String,InputStream> entry = map.entrySet().toArray()[0]

        logger.debug "Class = ${entry.value.class}"
        ByteArrayInputStream bo = (ByteArrayInputStream)entry.value
        //def str2 = IOUtils.toString(bo)
        //logger.debug "Str2 $str2"
        Map ret = [:]
        ret.put(alias,bo)
        return ret
    }
}

