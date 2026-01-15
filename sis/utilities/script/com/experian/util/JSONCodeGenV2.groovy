package com.experian.util

import com.experian.eda.component.decisionagent.IExecutableStrategy
import com.experian.eda.decisionagent.discovery.JavaDataSource
import com.experian.eda.decisionagent.discovery.Strategy
import com.experian.eda.decisionagent.discovery.codegen.jsoninterface.JSONCodeGeneratorSupport
import com.experian.eda.decisionagent.discovery.codegen.jsoninterface.JSONV2CodeGeneratorSupport
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger

public class JSONCodeGenV2 extends SMCodeGenerator {

    protected static final ExpLogger logger     = new ExpLogger(this);

    public JSONCodeGenV2(File serFile, String alias) {
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
        Map<String,InputStream> map = new HashMap<String,InputStream>();
        JSONV2CodeGeneratorSupport obj = new JSONV2CodeGeneratorSupport();

        try{
            //obj.getWizardPanel(execStrat);
            obj.strategy = execStrat;
            InputStream st = obj.createSchemaStream(alias, execStrat.getRuntimeProperties().getFullBusinessObjectiveName(), ((Integer)execStrat.getRuntimeProperties().getEditionNumber()).toString());
            map.put(alias, st);
        }catch(Exception e){
            logger.error "Error: " + e
            logger.error "${e.getStackTrace()}"
        }


        return map;
    }

}
