package com.experian.util

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Created by terueld on 12/07/2017.
 */
class JSONFaker {

    protected static final ExpLogger logger     = new ExpLogger(this);

    static final int DUMMY_NUMBER = 0
    static final String DUMMY_STR = "X"
    static final String DUMMY_DATE = "1900-01-01"
    static final String DUMMY_DATE_FORMAT = "yyyy-MM-dd"

    String getFakeMessage(String jsonschema, String alias){

        def content = new JsonSlurper().parseText(jsonschema)

        def responseMap = [:]

        content.properties.DAJSONDocument.properties.each{ k,v ->
            responseMap.put(k,fakeBlock(k,v,alias))
        }
        def finalMap = [DAJSONDocument:responseMap]

        def outputJson = JsonOutput.toJson(finalMap)
        return outputJson

    }

    Map fakeBlock(String name, Map schema, String alias){
        def retBlock = [:]
        logger.debug "Faking block $name"
		
        if(name == 'OCONTROL'){
            return ['ALIAS':["data_type":"text","value":alias],'SIGNATURE':["data_type":"text","value":"PLEASE PROVIDE SIGNATURE"]]
        }else {
			if(name == "value")
			{
				schema.items.properties.each { k, v ->
                def typeOfNode = getTypeOfNode(v)
                switch (typeOfNode) {
                    case 'F':
                        retBlock.put(k, fakeField(k, v.properties))
                        break
                    case 'O':
                        retBlock.put(k, fakeBlock(k, v, alias))
						
						break
                }
            }
			}
            schema.properties.each { k, v ->
                def typeOfNode = getTypeOfNode(v)
                switch (typeOfNode) {
                    case 'F':
                        retBlock.put(k, fakeField(k, v.properties))
                        break
                    case 'O':
						 if(v.type =="array")
						 {
							 retBlock.put(k, [fakeBlock(k, v, alias)])
						 }
						 else{
							 retBlock.put(k, fakeBlock(k, v, alias))
						 }
                        												
                        break
                }
            }
        }
        return retBlock
    }

    String getTypeOfNode(Map schema){
        if(schema.properties?.data_type == null){
            return 'O'
        }else {
           return 'F'
        }
    }

    Map fakeField(String name,Map schema){
        logger.debug "Fake Field $name > $schema"
        def data_type = "text"
        if(schema.value.type == "number"  || schema.data_type.pattern == 'numeric'){
            data_type = 'numeric'
        }else if(schema.data_type.pattern == 'date'){
            data_type = 'date'
        }else{
            data_type = 'text'
        }

        def isArray = (schema.value.items != null);


        def dummyValue
        if(!isArray) {
            if (data_type == 'numeric') dummyValue = DUMMY_NUMBER
            else if (data_type == 'date') dummyValue = DUMMY_DATE
            else dummyValue = DUMMY_STR
        }else{
            def array_data_type = schema.value.items.type
            if (array_data_type == 'numeric' || data_type == 'numeric') dummyValue = [DUMMY_NUMBER]
            else if (array_data_type == 'date' || data_type == 'date') dummyValue = [DUMMY_DATE]
            else dummyValue = [DUMMY_STR]
        }
        def auxVal = [data_type:data_type,value:dummyValue]
        if(data_type == 'date'){
            auxVal.put('date_format',DUMMY_DATE_FORMAT)
        }

        return auxVal;
    }


}
