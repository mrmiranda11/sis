package com.experian.util



import com.experian.eda.component.decisionagent.IExecutableStrategy;
import com.experian.eda.decisionagent.discovery.JavaDataSource;
import com.experian.eda.decisionagent.discovery.Strategy;
import com.experian.eda.decisionagent.discovery.codegen.xmlinterface.CreateXMLSchema;

public class XMLLegacyCodeGen extends SMCodeGenerator {

    public XMLLegacyCodeGen(File serFile, String alias) {
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

        return map;
    }

}

