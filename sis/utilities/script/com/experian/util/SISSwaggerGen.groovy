package com.experian.util

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Created by terueld on 02/10/2017.
 */
class SISSwaggerGen {

    String jsonSchemaContent
    String strategy
    String host

    protected static final ExpLogger logger     = new ExpLogger("com.experian.util.SISSwaggerGen");

    SISSwaggerGen(String jsonSchemaContent, String strategy, String host){
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
                                'description':'DAJSONRequest',
                                'required':true,
                                'schema':['$ref':'#/definitions/DAJSONDocument']]],
                 'responses':['200':['description':'successful response',
                                     'schema':['$ref':'#/definitions/DAJSONDocument']]]
                ])
        swagger.put('paths',['/DAServiceJSON':path])

        def DAJSONDocument = [:]

        content.properties.DAJSONDocument.properties.each { k,v ->
            logger.debug("[$k] \t [$v] [${v.class}]")
            DAJSONDocument.put(k,processObject(k.toString(),(Map)v))
        }

		logger.debug "DAJSONDOcument is > $DAJSONDocument"

        swagger.put('definitions',['DAJSONDocument':['type':'object','required':['OCONTROL'],'properties':DAJSONDocument]])



        return JsonOutput.toJson(swagger)

    }

    private Map processObject(String key, Map content){
        def ret = [:]
        def properties = [:]
		def required = []
        logger.debug("====== $key ===========")
        content.properties.each { k,v ->
            logger.debug("[$k] \t [$v]")
            
            def aux = v
            if(v.required){
                required.add(k)
                aux.remove('required')
				logger.debug('REQ')
            }
                if(k == 'value' && v?.type == 'array') {
                    //k is a complex array
                    def arrayContent = [:]
logger.debug('Array')
                    properties.put('value',['type':'array','items':processObject('items',v.items)])
                }else if(v.properties?.data_type?.type != 'string'){
logger.debug('NOT STRING')
                    properties.put(k,processObject(k,aux))
                }else if(v.properties.value.type == 'array') {
                    if(v.properties.value.items.type == 'object'){
                        //Aqui iria un array complejo
                        //System.out.println("$k is a complex array")
                    }else{
                        if(v.properties.value.maxItems == null){
                            if (v.properties.date_format == null) {
logger.debug('1')
                                properties.put(k, ['required'            : ['data_type', 'value'],
                                                   "additionalProperties": false,
                                                   properties            : ['data_type': getDataType(v.properties.data_type),
                                                                            'value'    : ['type' : 'array',
                                                                                          'items': ['type': getType(v.properties.value.items.type)]]]])
                            }else{
logger.debug('2')
								properties.put(k, ['required'            : ['data_type', 'value'],
                                                   "additionalProperties": false,
                                                   properties            : ['data_type': getDataType(v.properties.data_type),
                                                                            'value'    : ['type' : 'array',
                                                                                          'items': ['type': getType(v.properties.value.items.type)]],
                                                                            'date_format': ['type':'string',
                                                                                            'pattern':v.properties.date_format.pattern]]])
                            }
                        }else {
                            if (v.properties.date_format == null) {
logger.debug('3')
                                properties.put(k, ['required'            : ['data_type', 'value'],
                                                   "additionalProperties": false,
                                                   properties            : ['data_type': getDataType(v.properties.data_type),
                                                                            'value'    : ['type'    : 'array',
                                                                                          'items'   : ['type': getType(v.properties.value.items.type)],
                                                                                          'maxItems': v.properties.value.maxItems]]])
                            }else{
logger.debug('4')
                                properties.put(k, ['required'            : ['data_type', 'value'],
                                                   "additionalProperties": false,
                                                   properties            : ['data_type': getDataType(v.properties.data_type),
                                                                            'value'    : ['type'    : 'array',
                                                                                          'items'   : ['type': getType(v.properties.value.items.type)],
                                                                                          'maxItems': v.properties.value.maxItems],
                                                                            'date_format': ['type':'string',
                                                                                            'pattern':v.properties.date_format.pattern]]])
                            }
                        }
                    }

                }else{
                    if (v.properties.date_format == null) {
logger.debug('5')
                        properties.put(k, ['required'            : ['data_type', 'value'],
                                           "additionalProperties": false,
                                           properties            : ['data_type': getDataType(v.properties.data_type),
                                                                    'value'    : ['type': getType(v.properties.value.type)]]])
                    }else{
logger.debug('6')
                        properties.put(k, ['required'            : ['data_type', 'value'],
                                           "additionalProperties": false,
                                           properties            : ['data_type': getDataType(v.properties.data_type),
                                                                    'value'    : ['type': getType(v.properties.value.type)],
                                                                    'date_format': ['type':'string',
                                                                                    'pattern':v.properties.date_format.pattern]]])

                    }
                }
            

        }
        //ret.put(key,properties)
        return ['required':required,'properties':properties,'additionalProperties':false]
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
