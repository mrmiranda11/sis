package com.experian.util;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import groovy.util.slurpersupport.GPathResult;
import com.experian.stratman.datasources.runtime.IData

import java.util.*;


public class SampleBuilder{

    protected static       String    defaultDecimal = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.decimal.default')
    protected static       String    defaultDouble = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.double.default')
    protected static       String    defaultDate = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.date.default')
    private SimpleDateFormatThreadSafe myDateFormatter = new SimpleDateFormatThreadSafe("yyyy-MM-dd");
    protected static       String maxDyn = MyInterfaceBrokerProperties.getPropertyValue('max.dynarray.elements')
    private static int MAX_DYN = 1//(maxDyn == null || maxDyn == 'null' || maxDyn.trim() == '')?5:Integer.parseInt(maxDyn.trim())


    public static IData[] buildSampleAreas(GPathResult xsd, String alias, ExpLogger logger) {

        //logger.debug "Build Sample Areas"

        //Find the dictionary areas
        def rootElement = xsd.depthFirst().collect { it }.find { it.name() == "element" && it.@name.text() == "DAXMLDocument" }

        //logger.debug rootElement.name();

        def dictionaries = rootElement.complexType.all.children()

        IData[] ret = new IData[dictionaries.size()]

        int i = 0;
        def legacy=1337
        def sLegacy= MyInterfaceBrokerProperties.getPropertyValue('format.xsd.'+alias) 
        legacy = sLegacy.isInteger() ? sLegacy.toInteger() : null
     
        dictionaries.each { dict ->
                 
         switch(legacy) {            

                    case 1: 
                        ret[i++] = buildSampleDictLegacy(dict, logger, alias, dict.@name.text() == 'OCONTROL')
                        break; 
                    case 2: 
                         ret[i++] = buildSampleDict(dict, logger, alias, dict.@name.text() == 'OCONTROL')
                        break; 
                    case 3: 
                        ret[i++] = buildSampleDictLegacyV3(dict, logger, alias, dict.@name.text() == 'OCONTROL')
                        break; 
                    case 4: 
                        ret[i++] = buildSampleDictV4(dict, logger, alias, dict.@name.text() == 'OCONTROL')
                        break; 
                    default: 
                        ret[i++] = buildSampleDict(dict, logger, alias, dict.@name.text() == 'OCONTROL')
                        break; 
                }
            logger.debug "Sample area for " +  dict.@name.text()

        }

        logger.debug "Ret: ${ret}"
        return ret;

    }
    private static IData buildSampleDict(GPathResult dictionary, ExpLogger logger, String alias, boolean isOcontrol){
        try{
            def maxDynAlias = MyInterfaceBrokerProperties.getPropertyValue(alias+'.max.dynarray.elements')
            IData ret = new HDataArea(dictionary.@name.text(),logger);
            dictionary.complexType.all.children().each{  element ->
                logger.info "[${element.@name.text()}]: Data type is  " +  element.@type.text()
                if(isOcontrol && element.@name.text() == 'DALOGLEVEL'){
                    ret.setValue(element.@name.text(),new BigDecimal("0"));
                }else if(element.@type == null || element.@type.text() == null || element.@type.text() == '' ){

                    if(element.complexType.sequence.element.@name.text() == 'item' || element.complexType.sequence.children()[0].@ref.text() == 'date_format'){
                        logger.debug ">>> Building sample: ${element.@name.text()} REF= ${element.complexType.sequence.children()[0].@ref.text()}"

                        def nElem = element.complexType.sequence.element.@maxOccurs.text();
                        if(nElem != null && nElem != 'unbounded'){
                            for(int i=1;i<=Integer.parseInt(nElem);i++){
                                //logger.debug ">Creating array for ${element.@name.text()}"+"[$i]}"
                                if(element.complexType.sequence.children()[0].@ref.text()){
                                    ret.setValue(element.@name.text() + "[$i]", new Date())
                                }else {
                                    ret.setValue(element.@name.text() + "[$i]", getSampleObject(element.complexType.sequence.element.@type.text()))
                                }
                            }
                        }else{
                            if(element.complexType.sequence.children()[0].@ref.text()){
                                ret.setValue(element.@name.text() + "[1]", new Date())
                            }else {
                                ret.setValue(element.@name.text()+"[1]",getSampleObject( element.complexType.sequence.element.@type.text()))
                            }
                        }

                    }else if(element.complexType.sequence.element.@name.text() == 'element'){
                        logger.debug "${element.name()} is a DynamicArray";
						for(int i=1;i<=MAX_DYN;i++) {
                            buildSampleDictDynamicArray(element, logger, element.@name.text() + "[$i].", ret)
                        }
                    }else{
                        logger.debug "${element.@name.text()} is a subnode"
                        buildSampleDictSubnode(element,logger,element.@name.text()+".",ret)
                    }

                }else{
                    //logger.debug  "${isOcontrol} :: ${element.@name.text()}";
                    if(isOcontrol && element.@name.text() == 'ALIAS'){
                        ret.setValue(element.@name.text(),alias);
                    }else if(isOcontrol && element.@name.text() == 'SIGNATURE'){
                        ret.setValue(element.@name.text(),'[Please fill with signature]');
                    }else if(isOcontrol){
                        logger.debug "${element.@name.text()} will not be created"
                    }else{
                        logger.debug "Creating sample element ${element.@name.text()}"
                        ret.setValue(element.@name.text(),getSampleObject(element.@type.text()));
                    }
                }


            }
            if(isOcontrol){
                String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
                logger.debug "applicationIdHeaderField ===> ${applicationIdHeaderField} "
                if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != ""  && applicationIdHeaderField.trim() != "null"){
                    ret.setValue(applicationIdHeaderField,'111111');
                }
            }
            logger.debug "RET: ${ret}"
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            logger.error "${th.getStackTrace()}"
            return null;
        }


    }

    private static IData buildSampleDictLegacy(GPathResult dictionary, ExpLogger logger, String alias, boolean isOcontrol){
        try{
            IData ret = new DataArea(dictionary.@name.text(),logger);
            dictionary.complexType.all.children().each{  element ->
                //logger.debug "Data type is  " +  element.@type.text()
                if(isOcontrol && element.@name.text() == 'DALOGLEVEL'){
                    ret.setValue(element.@name.text(),new BigDecimal("0"));
                }else if(element.@type == null || element.@type.text() == null || element.@type.text() == ''){

                    //logger.debug "${element.complexType.all.children()[0].@name.text()}"
                    if(element.complexType.all.children()[0].@name.text() == 'I1'){
                        //An array
                        //logger.debug element.@name.text();
                        element.complexType.all.children().each { arrayItem ->
                            if(arrayItem.@name.text() != "data_type"){
                                //logger.debug "ArrayItem : " +  arrayItem.@name.text() + " _ " +  arrayItem.@type.text()
                                ret.setValue(element.@name.text()+"["+ arrayItem.@name.text().substring(1) + "]",getSampleObject(arrayItem.@type.text()))
                            }
                        }
                    }else{
                        //logger.debug "${element.@name.text()} is a subnode"
                        buildSampleDictSubnodeLegacy(element,logger,element.@name.text()+".",ret)
                    }

                }else{
                    //logger.debug  "${isOcontrol} :: ${element.@name.text()}";
                    if(isOcontrol && element.@name.text() == 'ALIAS'){
                        ret.setValue(element.@name.text(),alias);
                    }else if(isOcontrol && element.@name.text() == 'SIGNATURE'){
                        ret.setValue(element.@name.text(),'[Please fill with signature]');
                    }else if(isOcontrol){
                        logger.debug "${element.@name.text()} will not be created"
                    }else{
                        ret.setValue(element.@name.text(),getSampleObject(element.@type.text()));
                    }
                }


            }
            if(isOcontrol){
                String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
                logger.debug "applicationIdHeaderField ===> ${applicationIdHeaderField} "
                if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != ""  && applicationIdHeaderField.trim() != "null"){
                    ret.setValue(applicationIdHeaderField,'111111');
                }
            }
            logger.debug "RET: ${ret}"
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            return null;
        }


    }


    private static IData buildSampleDictSubnode(GPathResult node, ExpLogger logger, String prefix, IData ret){
        try{
            node.complexType.all.children().each{  element ->
                //logger.debug "Data type is  " +  element.@type.text()
                if(element.@type == null || element.@type.text() == null || element.@type.text() == ''){
                    //logger.debug "${element.complexType.all.children()[0].@name.text()}"
                    if(element.complexType.sequence.element.@name.text() == 'item' || element.complexType.sequence.children()[0].@ref.text() == 'date_format'){
                        //An array
                        logger.debug ">>> Building sample: ${element.@name.text()} REF= ${element.complexType.sequence.children()[0].@ref.text()}"
                        //logger.debug element.@name.text();
                        def nElem = element.complexType.sequence.element.@maxOccurs.text();
                        if(nElem != null && nElem != 'unbounded'){
                            for(int i=1;i<=Integer.parseInt(nElem);i++){
                                //logger.debug ">Creating array for ${prefix+element.@name.text()}"+"[$i]}"
                                if(element.complexType.sequence.children()[0].@ref.text()){
                                    ret.setValue(prefix + element.@name.text() + "[$i]", new Date())
                                }else {
                                    ret.setValue(prefix + element.@name.text() + "[$i]", getSampleObject(element.complexType.sequence.element.@type.text()))
                                }
                            }
                        }else{
                            if(element.complexType.sequence.children()[0].@ref.text()){
                                ret.setValue(prefix + element.@name.text() + "[1]", new Date())
                            }else {
                                ret.setValue(prefix + element.@name.text() + "[1]", getSampleObject(element.complexType.sequence.element.@type.text()))
                            }
                        }
                    }else if(element.complexType.sequence.element.@name.text() == 'element'){
                        logger.debug "${element.name()} is a DynamicArray";
						for(int i=1;i<=MAX_DYN;i++) {
                            buildSampleDictDynamicArray(element, logger, prefix + element.@name.text() + "[$i].", ret)
                        }
                    }else{
                        //logger.debug "${prefix+element.@name.text()} is a subnode"
                        buildSampleDictSubnode(element,logger,prefix+element.@name.text()+".",ret)
                    }

                }else{
                    ret.setValue(prefix+element.@name.text(),getSampleObject(element.@type.text()));

                }


            }
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            logger.error "${th.getStackTrace()}"
            return null;
        }


    }

    private static IData buildSampleDictDynamicArray(GPathResult node, ExpLogger logger, String prefix, IData ret){
        try{
            node.complexType.sequence.element.complexType.all.children().each{  element ->
                //logger.debug "Data type is  " +  element.@type.text()
                if(element.@type == null || element.@type.text() == null || element.@type.text() == ''){
                    //logger.debug "${element.complexType.all.children()[0].@name.text()}"
                    if(element.complexType.sequence.element.@name.text() == 'item'){
                        //An array
                        //logger.debug element.@name.text();
                        def nElem = element.complexType.sequence.element.@maxOccurs.text();
                        if(nElem != null && nElem != 'unbounded'){
                            for(int i=1;i<=Integer.parseInt(nElem);i++){
                                //logger.debug ">Creating array for ${prefix+element.@name.text()}"+"[$i]}"
                                ret.setValue(prefix+element.@name.text()+"[$i]",getSampleObject( element.complexType.sequence.element.@type.text()))
                            }
                        }else{
							ret.setValue(prefix+element.@name.text()+"[1]",getSampleObject( element.complexType.sequence.element.@type.text()))

                        }
                    }else if(element.complexType.sequence.element.@name.text() == 'element'){
                        logger.debug "${element.name()} is a DynamicArray";
						for(int i=1;i<=MAX_DYN;i++) {
                            buildSampleDictDynamicArray(element, logger, prefix + element.@name.text() + "[$i].", ret)
                        }
                    }else{
                        //logger.debug "${prefix+element.@name.text()} is a subnode"
                        buildSampleDictSubnode(element,logger,prefix+element.@name.text()+".",ret)
                    }

                }else{
                    ret.setValue(prefix+element.@name.text(),getSampleObject(element.@type.text()));

                }


            }
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            return null;
        }


    }

    private static IData buildSampleDictSubnodeLegacy(GPathResult node, ExpLogger logger, String prefix, IData ret){
        try{
            node.complexType.all.children().each{  element ->
                //logger.debug "Data type is  " +  element.@type.text()
                if(element.@type == null || element.@type.text() == null || element.@type.text() == ''){
                    //logger.debug "${element.complexType.all.children()[0].@name.text()}"
                    if(element.complexType.all.children()[0].@name.text() == 'I1'){
                        //An array
                        //logger.debug element.@name.text();
                        element.complexType.all.children().each { arrayItem ->
                            if(arrayItem.@name.text() != "data_type"){
                                //logger.debug "ArrayItem : " +  arrayItem.@name.text() + " _ " +  arrayItem.@type.text()
                                ret.setValue(prefix+element.@name.text()+"["+ arrayItem.@name.text().substring(1) + "]",getSampleObject(arrayItem.@type.text()))
                            }
                        }
                    }else{
                        //logger.debug "${prefix+element.@name.text()} is a subnode"
                        buildSampleDictSubnodeLegacy(element,logger,prefix+element.@name.text()+".",ret)
                    }

                }else{
                    ret.setValue(prefix+element.@name.text(),getSampleObject(element.@type.text()));

                }


            }
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            return null;
        }


    }
//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!SIS 2.9!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! 
    private static IData buildSampleDictV4(GPathResult dictionary, ExpLogger logger, String alias, boolean isOcontrol){
        try{
            def maxDynAlias = MyInterfaceBrokerProperties.getPropertyValue(alias+'.max.dynarray.elements')
            IData ret = new HDataArea2(dictionary.@name.text(),logger);
            dictionary.complexType.all.children().each{  element ->
                def  elementType = element.@type
                if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                {
                    //If not  xs:date the type is set in <restriction>
                    elementType=element.simpleType.restriction.@base
                }
                logger.debug " buildSampleDict [${element.@name.text()}]: Data type is  " +  elementType.text()
                if(isOcontrol && element.@name.text() == 'DALOGLEVEL'){
                    ret.setValue(element.@name.text(),new BigDecimal("0"));
                }

                //Only if xs:date than element.@type will be set
                else if(elementType == null || elementType.text() == null ||elementType.text() == '' ){
                    elementType=element.complexType.sequence.element.simpleType.restriction.@base
                    if(element.complexType.sequence.element.@name.text() == 'item' || element.complexType.sequence.children()[0].@ref.text() == 'date_format'){
                        elementType = element.complexType.sequence.element.@type
                        if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                        {
                            //If not  xs:date the type is set in <restriction>
                            elementType=element.complexType.sequence.element.simpleType.restriction.@base


                        }
                        logger.debug ">>> Building sample: ${element.@name.text()} REF= ${element.complexType.sequence.children()[0].@ref.text()}"

                        def nElem = element.complexType.sequence.element.@maxOccurs.text();
                        if(nElem != null && nElem != 'unbounded'){
                            for(int i=1;i<=Integer.parseInt(nElem);i++){
                                logger.debug ">Creating array for buildSampleDict ${element.@name.text()}"+"[$i]}"
                                if(element.complexType.sequence.children()[0].@ref.text()){
                                    ret.setValue(element.@name.text() + "[$i]", new Date())
                                }else {
                                    ret.setValue(element.@name.text() + "[$i]", getSampleObject(elementType.text()))
                                }
                            }
                        }else{
                            if(element.complexType.sequence.children()[0].@ref.text()){
                                ret.setValue(element.@name.text() + "[1]", new Date())
                            }else {
                                ret.setValue(element.@name.text()+"[1]",getSampleObject(elementType.text()))
                            }
                        }

                    }else if(element.complexType.sequence.element.@name.text() == 'element'){
                        logger.debug "${element.name()} is a DynamicArray";
                        for(int i=1;i<=MAX_DYN;i++) {
                            buildSampleDictDynamicArrayV4(element, logger, element.@name.text() + "[$i].", ret)
                        }
                    }else{
                        logger.debug "${element.@name.text()} is a subnode"
                        buildSampleDictSubnodeV4(element,logger,element.@name.text()+".",ret)
                    }

                }else{
                    //logger.debug  "${isOcontrol} :: ${element.@name.text()}";
                    if(isOcontrol && element.@name.text() == 'ALIAS'){
                        ret.setValue(element.@name.text(),alias);
                    }else if(isOcontrol && element.@name.text() == 'SIGNATURE'){
                        ret.setValue(element.@name.text(),'[Please fill with signature]');
                    }else if(isOcontrol){
                        logger.debug "${element.@name.text()} will not be created"
                    }else{
                        logger.debug "Creating sample element ${element.@name.text()}"
                        ret.setValue(element.@name.text(),getSampleObject(elementType.text()));
                    }
                }


            }
            if(isOcontrol){
                String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
                logger.debug "applicationIdHeaderField ===> ${applicationIdHeaderField} "
                if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != ""  && applicationIdHeaderField.trim() != "null"){
                    ret.setValue(applicationIdHeaderField,'111111');
                }
            }
            logger.debug "RET: ${ret}"
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            logger.error "${th.getStackTrace()}"
            return null;
        }


    }

    private static IData buildSampleDictLegacyV3(GPathResult dictionary, ExpLogger logger, String alias, boolean isOcontrol){
        try{
            IData ret = new DataArea2(dictionary.@name.text(),logger);
            dictionary.complexType.all.children().each{  element ->
                def  elementType = element.@type
                if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                {
                    //If not  xs:date the type is set in <restriction>
                    elementType=element.simpleType.restriction.@base
                }
                logger.debug "Data type is  " +  elementType.text()
                if(isOcontrol && element.@name.text() == 'DALOGLEVEL'){
                    ret.setValue(element.@name.text(),new BigDecimal("0"));
                }else if(elementType == null || elementType.text() == null ||elementType.text() == ''){

                    logger.debug " Array: ${element.complexType.all.children()[0].@name.text()}"
                    if(element.complexType.all.children()[0].@name.text() == 'I1'){
                        //An array
                        logger.debug element.@name.text();
                        element.complexType.all.children().each { arrayItem ->
                            if(arrayItem.@name.text() != "data_type"){
                                logger.debug "ArrayItem : " +  arrayItem.@name.text() + " _ " +  arrayItem.simpleType.restriction.@base.text()
                                ret.setValue(element.@name.text()+"["+ arrayItem.@name.text().substring(1) + "]",getSampleObject(arrayItem.simpleType.restriction.@base.text()))
                            }
                        }
                    }else{
                        //logger.debug "${element.@name.text()} is a subnode"
                        buildSampleDictSubnodeLegacyV3(element,logger,element.@name.text()+".",ret)
                    }

                }else{
                    //logger.debug  "${isOcontrol} :: ${element.@name.text()}";
                    if(isOcontrol && element.@name.text() == 'ALIAS'){
                        ret.setValue(element.@name.text(),alias);
                    }else if(isOcontrol && element.@name.text() == 'SIGNATURE'){
                        ret.setValue(element.@name.text(),'[Please fill with signature]');
                    }else if(isOcontrol){
                        logger.debug "${element.@name.text()} will not be created"
                    }else{
                        ret.setValue(element.@name.text(),getSampleObject(elementType.text()));
                    }
                }


            }
            if(isOcontrol){
                String applicationIdHeaderField = MyInterfaceBrokerProperties.getPropertyValue('applicationIdHeaderField');
                logger.debug "applicationIdHeaderField ===> ${applicationIdHeaderField} "
                if(applicationIdHeaderField != null && applicationIdHeaderField.trim() != ""  && applicationIdHeaderField.trim() != "null"){
                    ret.setValue(applicationIdHeaderField,'111111');
                }
            }
            logger.debug "RET: ${ret}"
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            return null;
        }


    }


    private static IData buildSampleDictSubnodeV4(GPathResult node, ExpLogger logger, String prefix, IData ret){
        try{
            node.complexType.all.children().each{  element ->
                //will be null if not xs:date
                def  elementType = element.@type
                if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                {
                    //If not  xs:date the type is set in <restriction>
                    elementType=element.simpleType.restriction.@base
                }
                logger.debug "buildSampleDictSubnode name: " +  element.@name.text()
                logger.debug "buildSampleDictSubnode type: " + elementType.text()
                if(elementType  == null || elementType.text() == null || elementType.text()  == '' ){

                    if(element.complexType.sequence.element.@name.text()  == 'item' || element.complexType.sequence.children()[0].@ref.text() == 'date_format'){
                        //An array
                        elementType = element.complexType.sequence.element.@type
                        if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                        {
                            //If not  xs:date the type is set in <restriction>
                            elementType=element.complexType.sequence.element.simpleType.restriction.@base
                        }
                        logger.debug ">>> Building sample: ${element.@name.text()} REF= ${element.complexType.sequence.children()[0].@ref.text()}"
                        logger.debug element.@name.text();
                        def nElem = element.complexType.sequence.element.@maxOccurs.text();
                        if(nElem != null && nElem != 'unbounded'){
                            for(int i=1;i<=Integer.parseInt(nElem);i++){
                                logger.debug("IT:" + i)
                                logger.debug (prefix + element.@name.text() + "[$i]")
                                if(element.complexType.sequence.children()[0].@ref.text()){
                                    ret.setValue(prefix + element.@name.text() + "[$i]", new Date())
                                }else {
                                    logger.debug("#1")
                                    ret.setValue(prefix+element.@name.text()+"[$i]",getSampleObject(elementType.text()))
                                }
                            }
                        }else{
                            if(element.complexType.sequence.children()[0].@ref.text()){
                                logger.debug("#2")
                                ret.setValue(prefix + element.@name.text() + "[1]", new Date())
                            }else {
                                logger.debug("#3")
                                ret.setValue(prefix + element.@name.text() + "[1]", getSampleObject(elementType.text()))
                            }
                        }
                    }else if(element.complexType.sequence.element.@name.text() == 'element'){
                        logger.debug("#4")
                        logger.debug "${element.name()} is a DynamicArray";
                        for(int i=1;i<=MAX_DYN;i++) {
                            logger.debug("Enter !?")
                            buildSampleDictDynamicArrayV4(element, logger, prefix + element.@name.text() + "[$i].", ret)
                        }
                    }else{
                        //logger.debug "${prefix+element.@name.text()} is a subnode"
                        buildSampleDictSubnodeV4(element,logger,prefix+element.@name.text()+".",ret)
                    }

                }else{
                    ret.setValue(prefix+element.@name.text(),getSampleObject(elementType.text()));

                }


            }
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            logger.error "${th.getStackTrace()}"
            return null;
        }


    }

    private static IData buildSampleDictDynamicArrayV4(GPathResult node, ExpLogger logger, String prefix, IData ret){
        logger.debug("Enter buildSampleDictDynamicArray")
        try{
            node.complexType.sequence.element.complexType.all.children().each{  element ->
                logger.debug "DynamicArray Element:  " +  element.@name.text()
                def  elementType = element.@type
                if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                {
                    //If not  xs:date the type is set in <restriction>
                    elementType=element.simpleType.restriction.@base
                }
                logger.debug "buildSampleDictDynamicArray name: " +  element.@name.text()
                logger.debug "buildSampleDictDynamicArray type: " + elementType.text()
                if(elementType== null || elementType.text() == null ||elementType.text() == ''){

                    //logger.debug "${element.complexType.all.children()[0].@name.text()}"
                    if(element.complexType.sequence.element.@name.text() == 'item'){
                        //An array
                        //logger.debug element.@name.text();
                        elementType = element.complexType.sequence.element.@type
                        if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                        {
                            //If not  xs:date the type is set in <restriction>
                            elementType=element.complexType.sequence.element.simpleType.restriction.@base
                        }
                        logger.debug("ComplexType"+elementType)
                        def nElem = element.complexType.sequence.element.@maxOccurs.text();
                        if(nElem != null && nElem != 'unbounded'){
                            for(int i=1;i<=Integer.parseInt(nElem);i++){
                                logger.debug ">Creating array for ${prefix+element.@name.text()}"+"[$i]}"
                                logger.debug("Type"+elementType.text())
                                ret.setValue(prefix+element.@name.text()+"[$i]",getSampleObject( elementType.text()))
                            }
                        }else{
                            ret.setValue(prefix+element.@name.text()+"[1]",getSampleObject( elementType.text()))

                        }
                    }else if(element.complexType.sequence.element.@name.text() == 'element'){
                        logger.debug "${element.name()} is a DynamicArray";
                        for(int i=1;i<=MAX_DYN;i++) {
                            buildSampleDictDynamicArrayV4(element, logger, prefix + element.@name.text() + "[$i].", ret)
                        }
                    }else{
                        logger.debug "${prefix+element.@name.text()} in DynamicArray is a subnode"
                        buildSampleDictSubnodeV4(element,logger,prefix+element.@name.text()+".",ret)
                    }

                }else{

                    ret.setValue(prefix+element.@name.text(),getSampleObject(elementType.text()));

                }


            }
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            return null;
        }


    }

    private static IData buildSampleDictSubnodeLegacyV3(GPathResult node, ExpLogger logger, String prefix, IData ret){
        try{
            node.complexType.all.children().each{  element ->

                def  elementType = element.@type
                if(elementType  == null || elementType.text() == null || elementType.text()  == '' )
                {
                    //If not  xs:date the type is set in <restriction>
                    elementType=element.simpleType.restriction.@base
                }
                logger.debug "Data type is  " +  elementType.text()
                if(elementType == null ||elementType.text() == null ||elementType.text() == ''){
                    //logger.debug "${element.complexType.all.children()[0].@name.text()}"
                    if(element.complexType.all.children()[0].@name.text() == 'I1'){
                        //An array
                        //logger.debug element.@name.text();
                        element.complexType.all.children().each { arrayItem ->
                            if(arrayItem.@name.text() != "data_type"){
                                logger.debug "ArrayItem : " +  arrayItem.@name.text() + " _ " +  arrayItem.simpleType.restriction.@base.text()
                                ret.setValue(prefix+element.@name.text()+"["+ arrayItem.@name.text().substring(1) + "]",getSampleObject(arrayItem.simpleType.restriction.@base.text()))
                            }
                        }
                    }else{
                        //logger.debug "${prefix+element.@name.text()} is a subnode"
                        buildSampleDictSubnodeLegacyV3(element,logger,prefix+element.@name.text()+".",ret)
                    }

                }else{

                    ret.setValue(prefix+element.@name.text(),getSampleObject(elementType.text()));

                }


            }
            return ret;

        }catch(Throwable th){
            logger.error "Error: ${th}"
            return null;
        }


    }

    private boolean isArray(GPathResult xsd){
        //logger_.debug "Checking if ${xsd.name()} is an Array with ${xsd.children()[0].name()}"

        return (xsd.children()[0].name() == 'I1')
    }


    private static Object getSampleObject(String dataType){
        Object ret = null;
        switch(dataType){

            case 'xs:string':
                ret = ""
                break;
            case 'decimal-or-empty':
                ret = new BigDecimal(defaultDecimal);
                break;
            case 'xs:decimal':
                ret = new BigDecimal(defaultDecimal);
                break;
            case 'xs:double':
                ret = new Double(defaultDouble);
                break;
            case 'xs:date':
                ret = new Date();
                break;


        }

    }





}