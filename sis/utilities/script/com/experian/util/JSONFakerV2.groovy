package com.experian.util

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Created by terueld on 12/07/2017.
 */
class JSONFakerV2 {

    protected static final ExpLogger logger     = new ExpLogger(this);

    static final int DUMMY_NUMBER = 0
    static final String DUMMY_STR = "X"
    static final String DUMMY_DATE = "1900-01-01"
    static final String DUMMY_DATE_FORMAT = "yyyy-MM-dd"

    String getFakeMessage(String jsonschema, String alias){

        def content = new JsonSlurper().parseText(jsonschema)

        logger.debug "Content: $content"
        def responseMap = [:]

        content.properties.DAJSONDocumentV2.properties.each{ k,v ->
            logger.debug "Faking block $k with content $v"
            responseMap.put(k,fakeBlock(k,v,alias))
        }
        def finalMap = [DAJSONDocumentV2:responseMap]

        def outputJson = JsonOutput.toJson(finalMap)
        return outputJson

    }

    Map fakeBlock(String name, Map schema, String alias){
        def retBlock = [:]
        logger.debug "Faking block $name"
		
        if(name == 'OCONTROL'){
            return ['ALIAS':alias,'SIGNATURE':"PLEASE PROVIDE SIGNATURE"]
        }else {
			if(name == "value"){
				schema.items.properties.each { k, v ->
                    def typeOfNode = getTypeOfNode(v)
                    switch (typeOfNode) {
                        case 'F':
                            retBlock.put(k, fakeField(k, v))
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
                        retBlock.put(k, fakeField(k, v))
                        break
                    case 'O':
                        retBlock.put(k, fakeBlock(k, v, alias))
												
                        break
                }
            }
        }
        return retBlock
    }

    String getTypeOfNode(Map schema){
        if(schema.type == null || schema.type == "object"){
            return 'O'
        }else {
           return 'F'
        }
    }

    Object fakeField(String name, Map schema){
        logger.debug "Fake Field $name > $schema"
        def data_type = "text"
        if(schema.type == "number" || schema.type[0] == "number" ){
            data_type = 'numeric'
        }else if(schema.type == 'date' || schema.type[0] == "date" ){
            data_type = 'date'
        }else{
            data_type = 'text'
        }

        def isArray = (schema.type == 'array')
        logger.debug "Data type is $data_type and isArray = $isArray"

        def dummyValue
        if(!isArray) {
            if (data_type == 'numeric') dummyValue = DUMMY_NUMBER
            else if (data_type == 'date') dummyValue = DUMMY_DATE
            else dummyValue = DUMMY_STR
        }else{
            def array_data_type = schema.items.type
            if (array_data_type == 'numeric' || data_type == 'numeric') dummyValue = [DUMMY_NUMBER]
            else if (array_data_type == 'date' || data_type == 'date') dummyValue = [DUMMY_DATE]
            else dummyValue = [DUMMY_STR]
        }
        def auxVal = dummyValue
        //if(data_type == 'date'){
        //    auxVal.put('date_format',DUMMY_DATE_FORMAT)
        //}

        return auxVal;
    }


}
