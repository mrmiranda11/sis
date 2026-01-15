package com.experian.util

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Created by terueld on 02/10/2017.
 */
class SISSwaggerGenV2 {

    String jsonSchemaContent
    String strategy
    String host

    protected static final ExpLogger logger     = new ExpLogger("com.experian.util.SISSwaggerGen");

    SISSwaggerGenV2(String jsonSchemaContent, String strategy, String host){
        this.jsonSchemaContent = jsonSchemaContent
        this.strategy = strategy
        if(host == null || host.trim() == '' || host.trim() == 'null'){
            logger.debug "Setting default hostname "
            InetAddress x = InetAddress.getLocalHost()
            this.host = x.hostName + ":8092"
        }else {
            this.host = host
        }

    }

    String generateSwagger(){
        def swagger = [:]

        def content = new JsonSlurper().parseText(jsonSchemaContent)

        swagger.put('swagger','2.0')
        swagger.put('info',['description':'Smart Integration to Strategies','version':'2.13.0','title':'SIS','termsOfService':''])
        swagger.put('host',this.host)
        swagger.put('tags',[['name':'sis','description':'Smart Integration to Strategies']])
        swagger.put('schemes',['http','https'])

        def path = [:]
        path.put('post',
                ['tags':['sis'],
                 'summary':"Call SIS JSON Interface for strategy ${strategy}",
                 'operationId':'callDAJSON',
                 'consumes':['application/json'],
                 'produces':['application/json'],
                 'parameters':[['in':'body',
                                'name':'body',
                                'description':'DAJSONRequestV2',
                                'required':true,
                                'schema':['$ref':'#/definitions/DAJSONDocumentV2']]],
                 'responses':['200':['description':'successful response',
                                     'schema':['$ref':'#/definitions/DAJSONDocumentV2']]]
                ])
        swagger.put('paths',['/DAServiceJSON':path])

        def DAJSONDocument = [:]

        logger.debug ">>> ${content.properties}"

        content.properties.DAJSONDocumentV2.properties.each { k,v ->
            logger.debug("1: [$k] \t [$v] [${v.class}]")
            DAJSONDocument.put(k,processObject(k.toString(),(Map)v))
        }

		logger.debug "DAJSONDOcument is > $DAJSONDocument"

        swagger.put('definitions',['DAJSONDocumentV2':['type':'object','required':['OCONTROL'],'properties':DAJSONDocument]])



        return JsonOutput.toJson(swagger)

    }

    private Map processObject(String key, Map content){
        def ret = [:]
        def properties = [:]
		def required = []
        def required2 = []

        if(content.required != null){
            required2 = content.required
            logger.debug('REQ')
        }
        logger.debug("====== $key ===========")
        content.properties.each { k,v ->
            logger.debug("2: [$k] \t [$v]")
            
            def aux = v
            logger.debug "Required ${v.required}"
            if(v.required != null){
                required.add(k)
                aux.remove('required')
				logger.debug('REQ')
            }
                if(k == 'value' && v?.type == 'array') {
                    //k is a complex array
                    def arrayContent = [:]
logger.debug('Array')
                    properties.put('value',['type':'array','items':processObject('items',v.items)])
                }else if(v.type == 'object'){
logger.debug('OBJECT')
                    properties.put(k,processObject(k,aux))
                }else if(v.type == 'array') {

                    if(v.maxItems == null){
                        if (v.type != 'date') {
logger.debug('1')
                            properties.put(k, ['type'            : 'array',
                                               'items': ['type': v.items.type]])
                        }else{
logger.debug('2')
                            properties.put(k, ['type'            : 'array',
                                               'items': ['type': v.items.type]])
                        }
                    }else {
                        if (v.type != 'date') {
logger.debug('3')
                            properties.put(k, ['type'            : 'array',
                                               'items': ['type': v.items.type],
                                               'maxItems': v.maxItems])

                        }else{
logger.debug('4')
                            properties.put(k, ['type'            : 'array',
                                               'items': ['type': v.items.type],
                                               'maxItems': v.maxItems])
                        }
                    }


                }else{
                    if (v.type != 'date') {
logger.debug('5')
                        logger.debug ">> Type is ${v.type}. ${v.type instanceof String} ${v.type.size()}"
                        properties.put(k, ['type'            : (v.type instanceof String?v.type:v.type[0])])
                    }else{
logger.debug('6')
                        properties.put(k, ['type'            : (v.type instanceof String?v.type:v.type[0])])

                    }
                }
            

        }
        //ret.put(key,properties)
        if(required2 == []){
            return ['properties':properties,'additionalProperties':false]
        }else{
            return ['required':required2,'properties':properties,'additionalProperties':false]
        }
    }

    String getType(Object types){
        String ret = ""
        if(types instanceof ArrayList){
            ret = ((ArrayList)types).get(0).toString()
        }
        else {
            ret = types.toString()
        }
        return ret
    }

    Map getDataType(Map dataType){
        def ret = ['type':'string']
        if(dataType.pattern != null){
            ret.put('pattern',dataType.pattern)
        }
        return ret
    }




}
