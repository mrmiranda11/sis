package com.experian.util

import com.experian.eda.enterprise.core.api.Message
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger
import com.experian.eda.framework.runtime.dynamic.HDataException
import com.experian.jmx.JMXHelper
import com.experian.jmx.NBSMManager
import com.experian.eda.framework.runtime.dynamic.IHData
import groovy.transform.Synchronized

// Logger
import groovy.util.slurpersupport.GPathResult
import org.apache.commons.lang.StringEscapeUtils
  
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.List;
import java.util.ArrayList;

public class HDataArea2 implements IHData {

    private static Hashtable cache = new Hashtable();
    private static Hashtable dynArrCache = new Hashtable();
    /** The layout name corresponding to a physical data source. */
    String layoutName_;
    /** A HashMap used to provide a name/value pair data table. */
    Map areaContents_;
    List order_;
    List orderPlain_;
    /** A Logger used to provide access to logger */
    ExpLogger logger_
    boolean actTrace;
    //Quitar
    Date d = new Date();

    public Map fieldSize_;
    private List coveredIndex = []

    private NumberFormat formatterNoDec = new DecimalFormat("##");
    private NumberFormat formatterDec = new DecimalFormat("##.##");

    protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
    protected static       String    defaultDecimal = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.decimal.default')
    protected static       String    defaultDouble = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.double.default')
    protected static       String    defaultDate = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.date.default')
	protected       String    dateFormat = MyInterfaceBrokerProperties.getPropertyValue('editiondate.format')!=null? MyInterfaceBrokerProperties.getPropertyValue('editiondate.format').toUpperCase():"D"

    protected Map dynamicArrays = [:];

    private SimpleDateFormatThreadSafe myDateFormatter = new SimpleDateFormatThreadSafe("yyyy-MM-dd");
    private SimpleDateFormatThreadSafe myTimestampFormatter = new SimpleDateFormatThreadSafe("yyyy-MM-dd HH:mm:ss");
    // Put in cache pre-initialised areaContents_
    private static Hashtable<String, Map> initialisedValues = new Hashtable();
    /**
     * Creates a new instance of DataArea.
     * @param layoutName The layout name corresponding to a
     * physical data source.
     */

    public HDataArea2(String layoutName, ExpLogger logger)
    {
	    logger.debug("Entering HDataArea2")
        layoutName_ = layoutName;
        areaContents_ = new HashMap();
        //areaContents_ = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        //areaContents_ = new TreeMap<String, String>(new com.experian.util.AreaContentComparator())
        logger_ = logger;
        if (logger == null) actTrace = false
        order_ = new ArrayList();
        orderPlain_ = new ArrayList();
        fieldSize_ = [:]
    }

    /**
     * Returns the layout name.
     * @return The layout name.
     */

    public String getLayout()
    {
        return layoutName_;
    }



    /**
     * Returns the value of a characteristic.
     * @param name The external name of the characteristic.
     * @return The value as a Number, String or Date object.
     */

    public Object getValue(String name, boolean doTrace = false)
    {
        Object retVal = null;
        if (name.indexOf('[') != -1) name = myNormaliseXMLName(name)
        retVal = areaContents_.get(name);
        //if (actTrace) logger_.debug """Captured value for <${layoutName_}.${name}> = <${(retVal == null ? "null" : retVal)}>""";
        return retVal;
    }

    /**
     * Stores the value of a characteristic.
     * @param name The external name of the characteristic.
     * @param value The value as a Number, String or Date object.
     */

    public void setValue(String name, Object value, boolean doTrace = false)
    {
        //if (actTrace) logger_.debug """Set value of characteristic <${layoutName_}.${name}> to <${(value == null ? "null" : value)}>""";
        areaContents_.put(name, value);
        if(!orderPlain_.contains(name)) {
            orderPlain_.add(name);

        }
    }

    @Override
    int getSize(String name) throws HDataException {
        if(fieldSize_.containsKey(name)) return fieldSize_[name];
        return 0
    }

    @Override
    void setSize(String name, int size) throws HDataException {
        if(size < 0) throw new HDataException("Size for $name is not correct: $size");
        fieldSize_.put(name,size);
    }

    @Override
    void clear(String name) throws HDataException {
        setSize(name,0);
    }

    @Override
    void add(String s, String s1) {

    }

    @Override
    void remove(String s) {

    }

    public String toString(){
        return layoutName_ + ":: " +  areaContents_.toString();
    }

    public Set getValueKeySet(){
        return areaContents_.keySet();
    }

    public List getOrderedKeyList(){
        return order_
    }

    public void setOrderedKeyList(List list)
    {
        this.order_ = list;
    }

    public Object getDicRootElement(GPathResult xsd, String dicName) {
        def root = xsd.complexType.all
        def dicRoot = root;

        //logger_.debug "root " + root.name()
        //def init = System.currentTimeMillis();
        //def dicRoot = root.depthFirst().collect { it }.find { it.name() == "element" && it.@name.text() == dicName }
        root.children().each { element ->
            //logger_.debug element.@name.text();
            if(element.@name.text() == dicName){
                dicRoot = element;
            }
        }
        return dicRoot.complexType.all;
    }

    public void fromXMLEmpty(GPathResult xsd, String dicName, String alias, Message message, boolean doTrace = false) throws Exception {

        def root = xsd.complexType.all
        def dicRoot = root;
        def dicRootElements = getDicRootElement(xsd, dicName);

        Hashtable<String, String> types = null
		
		//logger_.debug "DAVID@@: Entering in synchronized"
		while(types == null){
			types = loadDefinition(dicRootElements, alias, message, dicName);
		}
		//logger_.debug "DAVID@@: Existing syncrhonized"
		

        //logger_.debug "Calling fromXMLEmpty for $alias and $dicName";

        initialiseDefaultValues(alias, dicName, types, doTrace);

        //logger_.debug "Order after empty default values: ${order_}"

        freeDef(types,alias,message,dicName)
        //message.get('timeAgent').addPoint("${layoutName_}_EMPTY");
    }

    public void fromXML(GPathResult xml, GPathResult xsd, String dicName, String alias, Message message, boolean doTrace = false) throws Exception{

        def root = xsd.complexType.all
        def dicRoot = root;
        def dicRootElements = getDicRootElement(xsd, dicName);

        //logger_.debug "root " + root.name()
        //def init = System.currentTimeMillis();
        //def dicRoot = root.depthFirst().collect { it }.find { it.name() == "element" && it.@name.text() == dicName }
        /*root.children().each { element ->
            //logger_.debug element.@name.text();
            if(element.@name.text() == dicName){
              dicRoot = element;
            }
        }*/
        //logger_.debug "collecting the dicRoot " + (System.currentTimeMillis() - init);
        //logger_.debug "dicRoot " + dicRoot.name()

        //def dicRootElements = dicRoot.complexType.all
        //def types = loadDefinition(dicRootElements, alias, message, dicName);
        Hashtable<String, String> types = null
		
		//logger_.debug "DAVID@@: Entering in synchronized"
		while(types == null){
			types = loadDefinition(dicRootElements, alias, message, dicName);
		}
		//logger_.debug "DAVID@@: Existing syncrhonized"
		
		logger_.debug "Types: ${types}"
        //logger_.debug "DynamicArrays found: ${dynamicArrays}"

        //logger_.debug "dicRootElements " + dicRootElements.name()

        //message.get('timeAgent').addPoint("${layoutName_}_LOADTYPES");

        initialiseDefaultValues(alias, dicName, types, doTrace);
        //logger_.debug "Called init default values from fromXML"

        //logger_.debug "After inialice default values ${this.areaContents_}"

        xml.children().each { field ->
            //For each data_type we do different things
            //Search the xsd to look at the data type

            int nChildren = 0;
            if(field.children() != null)
                nChildren = field.children().size();
            //message.get('timeAgent').addPoint("${layoutName_}_BEFDYNA_${field.name()}");
            //logger_.debug "fromXML: Seaching data type for field " + field.name() + " with ${nChildren} children and being ${field.children()[0].name().toUpperCase()} its first child";
            if(dynamicArrays[field.name()]!=null){
                //logger_.debug "fromXML: ${field.name()} is a dynamic array"
                //message.get('timeAgent').addPoint("${layoutName_}_BEFDYNAAFTERIF_${field.name()}");
                fromXMLDynamicArray(field,types,field.name()+".",field.name()+".",doTrace, message)
                //message.get('timeAgent').addPoint("${layoutName_}_DYNA_${field.name()}");
            }else if(nChildren > 0 && field.children()[0].name() != 'item' && field.children()[0].name() != 'date_format'){
                //logger_.debug "fromXML: ${field.name()} is a subnode";
                fromXMLSubnode(field,types,field.name()+".",field.name()+".",doTrace, message)
                //message.get('timeAgent').addPoint("${layoutName_}_SUBN_${field.name()}");
            }else{
				//logger_.debug "DAVID@@: $types | $field | ${field?.name()}"
                String dataType = types[field.name()]
                int arrayLength = 0;
                if(dataType != null){
                    //logger_.debug "data type for field " + field.name() + " " + dataType
                    if(dataType == null || dataType == '') dataType = "array"

                    //if(actTrace) logger_.debug "after: dataType = " + dataType
                    def arrayItemDataType = ''
                    if(dataType.indexOf("#")>=0){
                        arrayItemDataType = dataType.split("#")[1]
                        dataType = "array"//dataType.split("#")[0]

                    }

                    switch (dataType){
                        case 'xs:string':
                            setValue(field.name(),field.text(),doTrace)
                            break;
                        case 'decimal-or-empty':
                            if(field != null && field.text()!=null && field.text().trim()!='')
                                setValue(field.name(),new BigDecimal(field.text().trim()),doTrace)
                            break;
                        case 'xs:decimal':
                            if(field != null && field.text()!=null && field.text().trim()!='')
                                setValue(field.name(),new BigDecimal(field.text().trim()),doTrace)
                            //else
                            //setValue(field.name(),null,doTrace)
                            break;
                        case 'xs:double':
                            setValue(field.name(),new Double(field.text()),doTrace)
                            break;
                        case 'xs:date':
                            if(field != null && field.text()!=null && field.text().trim()!='')
                                setValue(field.name(),myDateFormatter.parse(field.text().trim()))
                            //else
                            // setValue(field.name(),null,doTrace)
                            break;
                        case 'array': //ARRAY
                            //logger_.debug "It's an array ${field.name()}"
                            int i = 1;
                            field.children().each { arrayItem ->
                                //logger_.debug "Child: " + arrayItem.name();
                                //logger_.debug "Child: " + arrayItem.name();
                                if(arrayItemDataType == 'xs:date' && arrayItem.name() == 'date_format'){
                                    //logger_.debug "Skipping date_format"
                                }else {
                                    increaseSize(field.name());
                                    String fieldName = field.name() + "[${i++}]";
                                    switch (arrayItemDataType) {
                                        case 'xs:string':
                                            setValue(fieldName, arrayItem.text(), doTrace)
                                            break;
                                        case 'xs:double':
                                            setValue(fieldName, new Double(arrayItem.text().trim()), doTrace)
                                            break;
                                        case 'xs:decimal':
                                            if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                setValue(fieldName, new BigDecimal(arrayItem.text().trim()), doTrace)
                                            //else
                                            // setValue(fieldName,null,doTrace)
                                            break;
                                        case 'decimal-or-empty':
                                            if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                setValue(fieldName, new BigDecimal(arrayItem.text().trim()), doTrace)
                                            break;
                                        case 'xs:date':
                                            if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                setValue(fieldName, myDateFormatter.parse(arrayItem.text().trim()))
                                            // else
                                            //setValue(fieldName,null,doTrace)
                                            break;
                                    }

                                }
                            }
                            break;
                    }
                }else{
                    //logger_.debug "${field.name()} is not defined or has no datatype"
                }
            }
            //message.get('timeAgent').addPoint("${layoutName_}_TYPES_${field.name()}");
        }
        freeDef(types,alias,message,dicName)
        //logger_.info "DataArea " + layoutName_ +  " :: " +  areaContents_
    }

    /**
     * Set default values to the strategies layouts as defiend in  da.initialise.values and da.initialise.values.<alias> system properties
     * @param alias
     * @param dicName
     * @param types
     * @param doTrace
     */
    synchronized
    private void initialiseDefaultValues(String alias, String dicName, Hashtable<String, String> types, boolean doTrace) {
        logger_.debug("Going to initialize default values for " + dicName + " with arrays " + dynamicArrays)
        if(initialisedValues.containsKey(alias + " - " + dicName)) {
            //logger_.debug "initialisedValues contains ${alias}-${dicName}"
            areaContents_ = initialisedValues.get(alias + " - " + dicName).clone();
            return;
        }

        // Check if this strategy requires initialisation
        String strategiesToInitialise = MyInterfaceBrokerProperties.getPropertyValue("da.initialise.values");
        //logger_.debug "strategiesToInitialise > $strategiesToInitialise"
        if(strategiesToInitialise == null) {
            //logger_.debug "No strategies to initialize"
            return;
        }

        String[] strategiesToInitialiseArray = strategiesToInitialise.trim().split("\\|",-1);
        //logger_.debug "strategiesToInitialiseArray : $strategiesToInitialiseArray"
        boolean found = false;
        for(String strategyName: strategiesToInitialiseArray) {
            //logger_.debug "Verifying $strategyName againg $alias"
            if (strategyName.equalsIgnoreCase(alias)) {
                found = true;
                break;
            }
        }
        //logger_.debug "Found: $found"
        if(! found)
            return;

        // Check if this layout requires initialisation
        String layoutsToInitialise = MyInterfaceBrokerProperties.getPropertyValue("da.initialise.values." + alias);
        //logger_.debug("Layout to initialise for <"+alias+">: " + layoutsToInitialise + " with dicName = " + dicName);
        if(layoutsToInitialise == null)
            return;
        String[] layoutsToInitialiseArr = layoutsToInitialise.split("\\|",-1);
        found = false;
        for(String layoutName: layoutsToInitialiseArr)
            if(layoutName.equalsIgnoreCase(dicName)) {
                found = true;
                break;
            }
        if(! found)
            return;

        //logger_.debug "HD: Initialising values for strategy <" + alias + "> layout <"+dicName+">"

        def defaultDecimalSt = defaultDecimal
        if(MyInterfaceBrokerProperties.getPropertyValue("da.initialise.decimal.default." + alias) != 'null' && MyInterfaceBrokerProperties.getPropertyValue("da.initialise.decimal.default." + alias) != ""){
            defaultDecimalSt = MyInterfaceBrokerProperties.getPropertyValue("da.initialise.decimal.default." + alias);
        }

        def defaultDoubleSt = defaultDouble
        if(MyInterfaceBrokerProperties.getPropertyValue("da.initialise.decimal.default." + alias) != 'null' && MyInterfaceBrokerProperties.getPropertyValue("da.initialise.decimal.default." + alias) != ""){
            defaultDoubleSt = MyInterfaceBrokerProperties.getPropertyValue("da.initialise.double.default." + alias);
        }

        def defaultDateSt = defaultDate
        if(MyInterfaceBrokerProperties.getPropertyValue("da.initialise.date.default." + alias) != 'null' && MyInterfaceBrokerProperties.getPropertyValue("da.initialise.date.default." + alias) != ""){
            defaultDateSt = MyInterfaceBrokerProperties.getPropertyValue("da.initialise.date.default." + alias);
        }

        long startTime = System.currentTimeMillis();
        for(String fieldName: types.keys( ) ) {
            //logger_.debug "Initializing field $fieldName"
            if(partOfDynArray(fieldName)){
                //logger_.debug "$fieldName is part of a dynArray - Removing"
                continue
            }
            String dataType = types[fieldName];
            String arrayItemDataType = null;
            int arrayLength = 0;
            if(dataType.indexOf("#")>=0){
                arrayItemDataType = dataType.split("#")[1]
                dataType = dataType.split("#")[0]
                def iinit = dataType.indexOf("[");
                if(iinit != -1){
                    //logger_.debug ">>> Array Length = " + dataType.substring(iinit+1,dataType.length()-1);
                    def arrayLengthAux = dataType.substring(iinit+1,dataType.length()-1)
                    if(arrayLengthAux == 'unbounded'){
                        arrayLengthAux = "1"
                    }
                    arrayLength = Integer.parseInt(arrayLengthAux)
                    dataType = "array"
                }
            }
            switch (dataType){
                case 'xs:string':
                    setValue(fieldName,"",doTrace)
                    break;
                case 'decimal-or-empty':
                case 'xs:decimal':
                    setValue(fieldName,new BigDecimal(defaultDecimalSt.trim()),doTrace);

                    break;
                case 'xs:double':
                    setValue(fieldName,new Double(defaultDoubleSt.trim()),doTrace)
                    break;
                case 'xs:date':
                    setValue(fieldName,myDateFormatter.parse(defaultDateSt))
                    break;
                case 'array': //ARRAY
                    for(int i = 0; i<arrayLength; i++) {
                        String arrayFieldName = fieldName + "[" + (i+1) + "]";
                        increaseSize(fieldName)
                        switch (arrayItemDataType){
                            case 'xs:string':
                                setValue(arrayFieldName,"",doTrace)
                                break;
                            case 'decimal-or-empty':
                            case 'xs:decimal':
                                setValue(arrayFieldName,new BigDecimal(defaultDecimalSt.trim()),doTrace)
                                break;
                            case 'xs:double':
                                setValue(arrayFieldName,new Double(defaultDoubleSt),doTrace)
                                break;
                            case 'xs:date':
                                setValue(arrayFieldName,myDateFormatter.parse(defaultDateSt))
                                break;
                        }
                    }
                    break;
            }
        }
        logger_.debug "Area initialized: for ${alias + " - " + dicName}:: ${areaContents_}"
        initialisedValues.put(alias + " - " + dicName, areaContents_.clone());
    }


    private boolean partOfDynArray(String fieldName){
        for(String key:dynamicArrays.keySet()){
            //logger_.debug "partOfDynArray: checking '$fieldName' against '$key'"
            if(fieldName.startsWith(key+".")){
                return true
            }
        }
        return false
    }

    public void fromXMLSubnode(GPathResult xml, Hashtable types, String prefix, String prefixWithoutIndex, boolean doTrace = false, Message message) throws Exception{
        xml.children().each { field ->
            //For each data_type we do different things
            //Search the xsd to look at the data type
            String prefixNam = prefix+field.name()
            String prefixNamWithoutIndex = prefixWithoutIndex+field.name()

            int nChildren = 0;
            if(field.children() != null)
                nChildren = field.children().size();

            //logger_.debug "fromXMLSubnode: Seaching data type for field " + field.name() + " with ${nChildren} children and prefix: ${prefix} and prefixNamWithoutIndex ${prefixNamWithoutIndex}";

            if(dynamicArrays[prefixNamWithoutIndex]!=null){
                //logger_.debug "fromXMLSubnode: ${prefixNamWithoutIndex} is a dynamic array"
                fromXMLDynamicArray(field,types,prefixNam+".",prefixNamWithoutIndex+".",doTrace,message)
            }else if(nChildren > 0 && field.children()[0].name() != 'item' && field.children()[0].name() != 'date_format'){
                //logger_.debug "fromXMLSubnode: ${field.name()} is a subnode";
                fromXMLSubnode(field,types,prefixNam+".",prefixNamWithoutIndex+".",doTrace,message)
            }else{

                String dataType = types[prefixNamWithoutIndex]
                if(dataType != null){
                    //logger_.debug "data type for field ${prefixNamWithoutIndex} = ${dataType}"
                    //if(dataType == null || dataType == '') dataType = "array"

                    //if(actTrace) logger_.debug "dataType = " + dataType
                    def arrayItemDataType = ''
                    if(dataType.indexOf("#")>=0){
                        arrayItemDataType = dataType.split("#")[1]
                        dataType = 'array'//dataType.split("#")[0]
                    }
                    //logger_.debug "it's an $dataType and it's $arrayItemDataType"
                    switch (dataType){
                        case 'xs:string':
                            setValue(prefixNam,field.text(),doTrace)
                            break;
                        case 'decimal-or-empty':
                            //if(field != null && field.text()!=null && field.text().trim()!='')
                            if(field.text().trim()!='')
                                setValue(prefixNam,new BigDecimal(field.text().trim()),doTrace)

                            break;
                        case 'xs:decimal':
                            if(field != null && field.text()!=null && field.text().trim()!='')
                                setValue(prefixNam,new BigDecimal(field.text().trim()),doTrace)
                            //else
                            //  setValue(prefixNam,null,doTrace)
                            break;
                        case 'xs:double':
                            setValue(prefixNam,new Double(field.text()),doTrace)
                            break;
                        case 'xs:date':
                            if(field != null && field.text()!=null && field.text().trim()!='')
                            {
                                String val = field.text().trim();
                                if (val.length() > 10)
                                    setValue(prefixNam,myTimestampFormatter.parse(field.text().trim()))
                                else
                                    setValue(prefixNam,myDateFormatter.parse(field.text().trim()))
                            }
                            //else
                            // setValue(prefixNam,null,doTrace)
                            break;
                        case 'array': //ARRAY
                            //logger_.debug "It's an array ${field.name()} with datatype ${arrayItemDataType}"
                            int i = 1;
                            field.children().each { arrayItem ->
                                //logger_.debug "Child: " + arrayItem.name();
                                if(arrayItemDataType == 'xs:date' && arrayItem.name() == 'date_format'){
                                    //logger_.debug "Skipping date_format"
                                }else {
                                    increaseSize(prefixNam);

                                    String fieldName = prefixNam + "[${i++}]";
                                    //logger_.debug "Size increased for $fieldName"
                                    switch (arrayItemDataType) {
                                        case 'xs:string':
                                            setValue(fieldName, arrayItem.text(), doTrace)
                                            break;
                                        case 'xs:double':
                                            setValue(fieldName, new Double(arrayItem.text()), doTrace)
                                            break;
                                        case 'xs:decimal':
                                            if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                setValue(fieldName, new BigDecimal(arrayItem.text().trim()), doTrace)
                                            //else
                                            // setValue(fieldName,null,doTrace)
                                            break;
                                        case 'decimal-or-empty':
                                            if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                setValue(fieldName, new BigDecimal(arrayItem.text().trim()), doTrace)
                                            break;
                                        case 'xs:date':
                                            //logger_.debug "It's a date, so we create the date for ${arrayItem.text().trim()}"
                                            if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                setValue(fieldName, myDateFormatter.parse(arrayItem.text().trim()))
                                            //else
                                            // setValue(fieldName,null,doTrace)
                                            break;
                                    }
                                    //logger_.error "2:${fieldName}:${arrayItemDataType} ${System.currentTimeMillis() - initTime} : ${System.currentTimeMillis() - secondTime} "
                                }

                            }
                            break;
                    }
                }
            }
            //message.get('timeAgent').addPoint("${layoutName_}_TYPES_${field.name()}");
        }

    }

    public void fromXMLDynamicArray(GPathResult xml, Hashtable types, String prefix, String prefixWithoutIndex, boolean doTrace = false, Message message) throws Exception{
        //logger_.debug "fromXMLDynamicArray: Loading dynamic array info for $prefix"
        //message.get('timeAgent').addPoint("${layoutName_}_STARTDYN_${prefixWithoutIndex}")
        //logger_.debug "fromXMLDynamicArray: Dynamic array $prefix has ${xml.children().size()}"
        def inx = 1;
        def n = 1;
        xml.children().each{
            //message.get('timeAgent').addPoint("${layoutName_}_BEFINCREASE${n}_${prefix.substring(0,prefix.length()-1)}")
            increaseSize(prefix.substring(0,prefix.length()-1))
            //message.get('timeAgent').addPoint("${layoutName_}_INCREASE${n}_${prefix.substring(0,prefix.length()-1)}")
            def actPrefix = prefix.substring(0,prefix.length()-1) + "[${inx++}]."

            it.children().each { field ->
                //For each data_type we do different things
                //Search the xsd to look at the data type
                String prefixNam = actPrefix+field.name()
                String prefixNamWithoutIndex = prefixWithoutIndex+field.name()

                int nChildren = 0;
                if(field.children() != null)
                    nChildren = field.children().size();

                //logger_.debug "fromXMLDynamicArray: Seaching data type for field " + field.name() + " with ${nChildren} children and prefix: ${prefix} and prefixNamWithoutIndex: ${prefixNamWithoutIndex}";
                //logger_.debug "Checking ${prefix+field.name()} on $dynamicArrays"
                if(dynamicArrays[prefixNamWithoutIndex]!=null){
                    //logger_.debug "fromXMLDynamicArray: ${prefixNamWithoutIndex} is a dynamic array"
                    fromXMLDynamicArray(field,types,prefixNam+".",prefixNamWithoutIndex+".",doTrace,message)
                    //message.get('timeAgent').addPoint("${layoutName_}_ENDDY_${prefixNamWithoutIndex}")
                }else if(nChildren > 0 && field.children()[0].name() != 'item' && field.children()[0].name() != 'date_format'){
                    //logger_.debug "fromXMLDynamicArray: ${field.name()} is a subnode";
                    fromXMLSubnode(field,types,prefixNam+".",prefixNamWithoutIndex+".",doTrace, message)
                    //message.get('timeAgent').addPoint("${layoutName_}_ENDSB_${prefixNamWithoutIndex}")
                }else{

                    String dataType = types[prefixNamWithoutIndex]
                    //logger_.debug "fromXMLDynamicArray: data type for field ${prefixNamWithoutIndex} = ${dataType}"
                    if(dataType != null){
                        //logger_.debug "data type for field ${prefixNam} = ${dataType}"
                        //if(dataType == null || dataType == '') dataType = "array"

                        //if(actTrace) logger_.debug "dataType = " + dataType
                        def arrayItemDataType = ''
                        if(dataType.indexOf("#")>=0){
                            arrayItemDataType = dataType.split("#")[1]
                            dataType = 'array'//dataType.split("#")[0]
                        }

                        switch (dataType){
                            case 'xs:string':
                                setValue(prefixNam,field.text(),doTrace)
                                break;
                            case 'decimal-or-empty':
                                //if(field != null && field.text()!=null && field.text().trim()!='')
                                if(field.text().trim()!='')
                                    setValue(prefixNam,new BigDecimal(field.text().trim()),doTrace)

                                break;
                            case 'xs:decimal':
                                if(field != null && field.text()!=null && field.text().trim()!='')
                                    setValue(prefixNam,new BigDecimal(field.text().trim()),doTrace)
                                //else
                                //  setValue(prefixNam,null,doTrace)
                                break;
                            case 'xs:double':
                                setValue(prefixNam,new Double(field.text()),doTrace)
                                break;
                            case 'xs:date':
                                if(field != null && field.text()!=null && field.text().trim()!='')
                                {
                                    String val = field.text().trim();
                                    if (val.length() > 10)
                                        setValue(prefixNam,myTimestampFormatter.parse(field.text().trim()))
                                    else
                                        setValue(prefixNam,myDateFormatter.parse(field.text().trim()))
                                }
                                //else
                                // setValue(prefixNam,null,doTrace)
                                break;
                            case 'array': //ARRAY
                                //logger_.debug "It's an array ${field.name()}"
                                int i = 1;
                                field.children().each { arrayItem ->
                                    //logger_.debug "Child: " + arrayItem.name();
                                    if(arrayItemDataType == 'xs:date' && arrayItem.name() == 'date_format'){
                                        //logger_.debug "Skipping date_format"
                                    }else {
                                        //logger_.debug "fromXMLDynamicArray: Child: " + arrayItem.name();
                                        //message.get('timeAgent').addPoint("${layoutName_}_BEFNORMALARRAY_${prefixNam}")
                                        increaseSize(prefixNam);
                                        //message.get('timeAgent').addPoint("${layoutName_}_INCRNORMALARRAY_${prefixNam}")
                                        String fieldName = prefixNam + "[${i++}]";
                                        switch (arrayItemDataType) {
                                            case 'xs:string':
                                                setValue(fieldName, arrayItem.text(), doTrace)
                                                break;
                                            case 'xs:double':
                                                setValue(fieldName, new Double(arrayItem.text()), doTrace)
                                                break;
                                            case 'xs:decimal':
                                                if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                    setValue(fieldName, new BigDecimal(arrayItem.text().trim()), doTrace)
                                                //else
                                                // setValue(fieldName,null,doTrace)
                                                break;
                                            case 'decimal-or-empty':
                                                if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                    setValue(fieldName, new BigDecimal(arrayItem.text().trim()), doTrace)
                                                break;
                                            case 'xs:date':
                                                if (arrayItem != null && arrayItem.text() != null && arrayItem.text().trim() != '')
                                                    setValue(fieldName, myDateFormatter.parse(arrayItem.text().trim()))
                                                //else
                                                // setValue(fieldName,null,doTrace)
                                                break;
                                        }

                                    }
                                }
                                break;
                        }
                    }
                }
                //message.get('timeAgent').addPoint("${layoutName_}_TYPES_${field.name()}");
            }
        }

    }

    public String toXML(String[] manFields, String[] optFields, String applicationIdHeaderField, boolean error = false){
        logger_.debug "toXML: Creating XML output for $layoutName_"

        //order_ = orderList(order_)
        //logger_.debug "toXML: ORDERED LIST >>> $order_ with size ${order_.size()}"
        //logger_.debug "toXML: DynArraySize >>> ${fieldSize_}"

        //logger_.debug "toXML: ARRAYSIZES >>> $fieldSize_"

        order_ = new ArrayList(areaContents_.keySet())
        Collections.sort(order_,new AreaContentComparator())

        StringBuffer sb = new StringBuffer(50000);
        sb.append("<${layoutName_}>")
        if(layoutName_ != 'OCONTROL'){

            int index = 0;
            String element = "";
            while(index < order_.size()){
                element = order_.get(index);
                logger_.debug "toXML: Evaluating index $index that's >> $element";
                if(element.indexOf('.')  == -1){
                    if(element.indexOf('[') == -1){
                        sb.append(getXMLObjectItem(element,element));
                        index++;
                    }else{
                        logger_.debug "toXML: $element is an Array"
                        def arr = element.substring(0,element.indexOf('['))
                        logger_.debug "toXML: array name is  $arr"
                        sb.append("<$arr>")
                        int indexArr = 1;
                        logger_.debug "> Array " + arr+"[${indexArr}]"
                        if(areaContents_[element] instanceof Date){
                            //logger_.debug "$element is a date array, adding date_format"
                            sb.append("<date_format>yyyy-MM-dd</date_format>")
                        }
                        sb.append(getXMLObjectItem(arr+"[${indexArr++}]","item"))
                        index++
                        if(index >= order_.size()){
                            sb.append("</$arr>")
                            break;
                        }
                        element = order_.get(index);
                        while (element.startsWith(arr+"[")) {
                            logger_.debug "> Array " + arr+"[${indexArr}] : >> index: ${index} and total ${order_.size()} and element ${element}"
                            sb.append(getXMLObjectItem(arr + "[${indexArr++}]", "item"))
                            index++
                            if (index < order_.size()) {
                                element = order_.get(index);
                            } else {
                                break;
                            }
                        }
                        sb.append("</$arr>")
                        continue
                    }
                }else{
                    logger_.debug "toXML: $element is complex element";
                    def firstDot = element.indexOf(".")
                    def root = element.substring(0,firstDot);
                    def rem = element.substring(firstDot+1)
                    logger_.debug "toXML: Element: $element; root: $root; rem: $rem"
                    if(root.indexOf("[") == -1){
                        logger_.debug "toXML: it's a subnode"
                        index = toXMLSubnode(root,"",index,sb)
                    }else{
                        logger_.debug "toXML: It's a dynamic array"
                        def rroot = root.substring(0,root.indexOf('['))
                        logger_.debug "toXML: RRoot is $rroot"
                        index = toXMLDynamicArray(rroot,root, "",index,sb);
                        //index++;
                    }

                }


            }

        }else{
            //Es Ocontrol, lo hacemos distinto
            sb.append(getXMLObjectItem('ALIAS','ALIAS'))
            sb.append(getXMLObjectItem('SIGNATURE','SIGNATURE'))
            if(getValue('EDITION') != null)
                sb.append(getXMLObjectItem('EDITION','EDITION'))
            if(getValue('OBJECTIVE') != null)
                sb.append(getXMLObjectItem('OBJECTIVE','OBJECTIVE'))
            if(getValue('EDITIONDATE') != null)
                sb.append(getXMLObjectItem('EDITIONDATE','EDITIONDATE'))
            if(error){
                sb.append("<ERRORCODE>WS20</ERRORCODE>\n")
                def iErrorCount = 0;
                try{
                    iErrorCount = (getValue('ERRORCOUNT')!=null &&   getValue('ERRORCOUNT') != '' && getValue('ERRORCOUNT') != '10.45')?Integer.parseInt(getValue('ERRORCOUNT')):0;
                }catch(Exception e){}
                def sErrorMessage = "Error calling DA: "
                for(int i=0;i<iErrorCount;i++){
                    sErrorMessage += getValue("ERROR[${i+1}]")
                    if(i<iErrorCount-1){
                        sErrorMessage += ", "
                    }
                }
                sb.append("<ERRORMSG>${sErrorMessage}</ERRORMSG>\n")
            } else { // New from v2.4 - Client may want to receive specific value even in case of success
                if ( "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('sis.incSucError') )) {
                    sb.append("<ERRORCODE>" + MyInterfaceBrokerProperties.getPropertyValue('sis.sucErrorCode') + "</ERRORCODE>\n")
                    sb.append("<ERRORMSG>" + MyInterfaceBrokerProperties.getPropertyValue('sis.sucErrorMsg') + "</ERRORMSG>\n")
                }
            }
            ////logger_.debug "Value of ${applicationIdHeaderField}"
            if(applicationIdHeaderField != null && applicationIdHeaderField.trim()!="" && applicationIdHeaderField.trim()!="null"){
                sb.append(getXMLObjectItem(applicationIdHeaderField,applicationIdHeaderField))
            }
            if(manFields != null){
                manFields.each {
                    sb.append(getXMLObjectItem(it,it))
                }
            }
            if(optFields != null){
                optFields.each {
                    if(getValue(it))
                        sb.append(getXMLObjectItem(it,it))
                }
            }
            if(getValue('DALOGLEVEL') != null)
                sb.append(getXMLObjectItem('DALOGLEVEL','DALOGLEVEL'))


        }

        sb.append("</${layoutName_}>");

    }

    public int toXMLDynamicArray(String root, String originalRoot, String offset, int index,StringBuffer sb){
        logger_.debug "toXMLDynamicArray [$root] [$originalRoot] [$offset] [$index]"
        String element = "";
        int nElements = 0;
        boolean endOfDynArr = false;
		logger_.debug "fieldSize = $fieldSize_ && dynamicArrays = $dynamicArrays"
		def max = 1
		def maxSizeDyn = dynamicArrays[offset+root]
        if(maxSizeDyn != null && maxSizeDyn != 'unbounded'){
            max = Integer.parseInt(maxSizeDyn.trim())
		}else{
			max = fieldSize_[offset+root];
			if(max == null) max = 1
        }
		int initIndex = index;
        int savedIndex = 0;
        logger_.debug "toXMLDyn: Max is $max"
        boolean saveIndex = false;

        String auxIndx = originalRoot.substring(root.length()+1,originalRoot.length()-1);
        //logger_.debug "AUXINDX == $auxIndx"
        if(Integer.parseInt(auxIndx) > max) {
            //logger_.debug "This is spare fields for more iterations"
            return (index+1);
        }
        if(coveredIndex.contains(index)){
            return (index +1)
        }
        sb.append("<$root>")
        for(int i=1;i<=max;i++){

            /*if(savedIndex != 0)
                index = savedIndex;
            logger_.debug "endOfDynArr = $endOfDynArr && i = $i and max = $max"
            if(endOfDynArr && i<=max){
                logger_.debug "Hay mÃ¡s elementos pero no estÃ¡n en orden"
                //guardamos el indice
                savedIndex = index;
                saveIndex = true;
                //Buscamos el Ã­ndice de original root
                for(int x=savedIndex;x<order_.size();x++){
                    logger_.debug "Buscando " + root+"[$i] en el indice $x > ${order_.get(x)}"
                    if(order_.get(x).toString().startsWith(root+"[$i]")){
                        logger_.debug "Setting index to $x"
                        index = x
                        coveredIndex.add(index)
                        break
                    }
                }
            }*/


            sb.append("<element>")
            /**        **/
            //index = initIndex;
            originalRoot = root+"[$i]";
            //logger_.debug "Haciendo el bucle para i = $i y [$root] [$originalRoot] [$offset] [$index]"
            while(index < order_.size()){
                element = order_.get(index);
                //Veo si pertenece al dArray
                //logger_.debug "toXMLDyn:Evaluating index $index that's >> $element";
                //logger_.debug "toXMLDyn:>> element is $element and originalRoot is ${offset+originalRoot}"
                if(!element.startsWith(offset+originalRoot+".")){
                    //Ya no es el mismo nodo, asÃ­ que break
                    //logger_.debug "toXMLDyn:>> Termino ese nodo: ${index}"
                    endOfDynArr = true;
                    //index++;
                    break;
                }else{
                    //Si que es un nodo del dArray
                    String remElement = element.substring((offset+originalRoot).length()+1)
                    //logger_.debug "toXMLDyn:Remaining element = $remElement";
                    if(remElement.indexOf('.')  == -1){
                        if(remElement.indexOf('[') == -1){
                            String campo = offset+root+"[$i]."+remElement
                            //logger_.debug "toXMLDyn:Creating element for $campo >> Here: ${getXMLObjectItem(campo,remElement)}"
                            sb.append(getXMLObjectItem(campo,remElement));
                            index++;
                            if(saveIndex)coveredIndex.add(index)
                        }else{
                            //logger_.debug "toXMLDyn: $remElement is an Array"
                            def arr = remElement.substring(0,remElement.indexOf('['))
                            sb.append("<$arr>")
                            int indexArr = 1;
                            if(areaContents_[element] instanceof Date){
                                //logger_.debug "$element is a date array, adding date_format"
                                sb.append("<date_format>yyyy-MM-dd</date_format>")
                            }
                            sb.append(getXMLObjectItem(offset+root+"[$i]."+arr+"[${indexArr++}]","item"))
                            index++
                            if(index >= order_.size()){
                                sb.append("</$arr>")
                                break;
                            }
                            element = order_.get(index);
                            while (element.startsWith(offset + root + "[$i]." + arr + "[")) {
                                //logger_.debug "toXMLDyn: Evaluating index $index that's >> $element";
                                sb.append(getXMLObjectItem(offset + root + "[$i]." + arr + "[${indexArr++}]", "item"))
                                if(saveIndex)coveredIndex.add(index)
                                index++
								if(index>=order_.size()){break;}
                                element = order_.get(index);
                            }

                            sb.append("</$arr>")
                            continue
                        }
                    }else{
                        //logger_.debug "toXMLDyn: Is a complex node under a dynamicArray"
                        def firstDot = remElement.indexOf(".")
                        def subroot = remElement.substring(0,firstDot);
                        def subrem = remElement.substring(firstDot+1)
                        //logger_.debug "toXMLDyn: RemElement: $remElement; root: $subroot; rem: $subrem"
                        if(subroot.indexOf("[") == -1){
                            //logger_.debug "toXML: it's a subnode"
                            //index = toXMLSubnode(root+"[$i]."+subroot,offset,index,sb)
                            index = toXMLSubnode(subroot,offset+root+"[$i].",index,sb)
                        }else{
                            //logger_.debug "toXMLDynArrary: It's a dynamic array"
                            def rroot = subroot.substring(0,subroot.indexOf('['))
                            //logger_.debug "toXMLDynArrary: RRoot is $rroot"
                            //logger_.debug "toXMLDynArrary: root is $root"
                            //logger_.debug "toXMLDynArrary: offset is $offset"
                            index = toXMLDynamicArray(rroot,subroot, offset+originalRoot+".", index,sb);
							
							//Commented as part of GDSE-457
                            //index++;
                        }
                    }
                }
            }
            /**        **/
            //logger_.debug "End>>"
            sb.append("</element>")
        }
        sb.append("</$root>")
        //logger_.debug "Returning $index"
        return index;



    }

    public int toXMLSubnode(String root, String offset, int index, StringBuffer sb){
        //logger_.debug "toXMLSubnode [$root] [$offset] [$index] [${sb.toString()}]"
        String element = "";
        int nElements = 0;
        sb.append("<$root>")
        while(index < order_.size()){
            element = order_.get(index);
            //logger_.debug "toXMLSub: Evaluating index $index that's '$element'";
            //logger_.debug "toXMLSub: Evaluating $element with $root and offset $offset";
            if(!element.startsWith(offset+root+".")){
                //logger_.debug "toXMLSub: No es el mismo nodo"
                //Ya no es el mismo nodo, asÃ­ que break
                //index++;
                //logger_.debug "toXMLSub: >> End this node"
                break;
            }else{
                //Si que es un nodo del Array
                String remElement = element.substring((offset+root).length()+1)
                //logger_.debug "toXMLSub: Remaining element = $remElement";
                if(remElement.indexOf('.')  == -1){
                    if(remElement.indexOf('[') == -1){
                        String campo = offset+root+"."+remElement
                        //logger_.debug "toXMLSub: Creating element for $campo"
                        sb.append(getXMLObjectItem(campo,remElement));
                        index++;
                    }else{
                        def arr = element.substring(0,element.indexOf('['))
                        def arrName = remElement.substring(0,remElement.indexOf("["))
                        //logger_.debug "toXMLSub: $element is an Array and arrayName = $arrName"
                        sb.append("<$arrName>")
                        int indexArr = 1;
                        //logger_.debug "Evaluating if $arrName it's a date ${areaContents_[element].class} :: > ${areaContents_[element].toString().indexOf("-")}"
                        if(areaContents_[element] instanceof  Date){ //toString().length() == 10 && areaContents_[element].toString().indexOf("-") == 4
                            //logger_.debug "$element is a date array, adding date_format"
                            sb.append("<date_format>yyyy-MM-dd</date_format>")
                        }
                        //logger_.debug "toXMLSub: Creating array element for " + offset+root+"."+arrName+"[${indexArr}]"
                        sb.append(getXMLObjectItem(offset+root+"."+arrName+"[${indexArr++}]","item"))
                        index++
                        if(index >= order_.size()){
                            sb.append("</$arrName>")
                            break;
                        }
                        element = order_.get(index);
                        while(element.startsWith(offset+root+"."+arrName+"[")){
                            //logger_.debug "toXMLSub: Evaluating index $index that's >> $element";
                            sb.append(getXMLObjectItem(offset+root+"."+arrName+"[${indexArr++}]","item"))
                            index++;
                            if(index >= order_.size()) element = ""
                            else element = order_.get(index);
                        }
                        sb.append("</$arrName>")
                        continue;

                    }
                }else{
                    //logger_.debug "toXMLSub: $element Is a complex node under a complex node"
                    def firstDot = remElement.indexOf(".")
                    def subroot = remElement.substring(0,firstDot);
                    def subrem = remElement.substring(firstDot+1)
                    //logger_.debug "toXML: RemElement: $remElement; root: $subroot; rem: $subrem"
                    if(subroot.indexOf("[") == -1){
                        ////logger_.debug "toXML: it's a subnode"
                        index = toXMLSubnode(subroot,offset+root+".",index,sb)
                    }else{
                        //logger_.debug "toXMLSub: It's a dynamic array"
                        def rroot = subroot.substring(0,subroot.indexOf('['))
                        //logger_.debug "toXMLSub: RRoot is $rroot"
                        //logger_.debug "toXMLSub: root is $root"
                        //logger_.debug "toXMLSub: offset is $offset"
                        //logger_.debug "toXMLSub: remElement is $remElement"
                        index = toXMLDynamicArray(rroot,subroot, offset+root+".",index,sb);

                    }
                }
            }


        }
        sb.append("</$root>")
        //logger_.debug "Al final ${sb.toString()}"
        return index
    }


    public String toXMLOld(String[] manFields, String[] optFields, String applicationIdHeaderField, boolean error = false){
        //logger_.debug "Creating XML output for $layoutName_"
        StringBuffer sb = new StringBuffer(50000);
        sb.append("<${layoutName_}>")
        String subnodo = ""
        String currentList = ""
        String[] old = null;
        boolean inSubnodes = false;
        if(layoutName_ != 'OCONTROL')
        {
            //logger_.debug "$layoutName_ ::> ${getOrderedKeyList()}"
            getOrderedKeyList().each {  item ->
                //for( String item : areaContents_.keySet())
                //{
//logger_.debug "info: "+item
//logger_.debug "currentList: "+currentList
//logger_.debug "old: "+old


                if(item.indexOf(".") != -1){


                    //Subnodo
                    def route = item.split("\\.");
                    //logger_.debug "Nodo: " + route;
                    //logger_.debug "Old: " + old;
                    if(!inSubnodes && currentList != '') {
                        sb.append("</${currentList}>\n")
                        currentList = ""
                    }

                    inSubnodes = true;
                    if(old  == null){

                        //logger_.debug "Creando subnodos"
                        old = route;
                        for(int i=0;i<route.length-1;i++){
                            sb.append("<${route[i]}>")
                        }

                        def itemName = route[route.length-1];
                        if(itemName.indexOf('[') == -1){



                            if(currentList != ''){
                                sb.append("</${currentList}>\n")
                                currentList = ""
                            }

                            sb.append(getXMLObjectItem(item,itemName))

                        } else {

                            if(itemName.indexOf('*') == -1){

                                //It's an array item
                                if(itemName.substring(0,itemName.indexOf('[')) != currentList){
                                    if(currentList !=''){
                                        sb.append("</${currentList}>\n")
                                    }
                                    //It's a new array
                                    currentList = itemName.substring(0,itemName.indexOf('['))
                                    sb.append("<${currentList}>\n")
                                }
                                def itemIndx = itemName.substring(itemName.indexOf('[')+1,itemName.indexOf(']'))
                                sb.append(getXMLObjectItem(item,"item"))
                            }else{
                                //It's a dynamic array
                                if(itemName.substring(0,itemName.indexOf('[')) != currentList){
                                    if(currentList !=''){
                                        sb.append("</${currentList}>\n")
                                    }
                                    //It's a new array
                                    currentList = itemName.substring(0,itemName.indexOf('['))
                                    sb.append("<${currentList}>\n")
                                }

                            }

                        }

                    }else{


                        // Calculate necessary close and open tag in one
                        String openingTags = "";
                        String closingTags = ""
                        int mx = Math.max(old.length-1,route.length-1)
                        for(int i=0;i<mx;i++){
                            if(route[i] != old[i]) {
                                for(int j=old.length-2;j>=i;j--)
                                    closingTags += "</"+old[j]+">";
                                for(int j=i;j<route.length-1;j++)
                                    openingTags += "<"+route[j]+">";
                                if(currentList != '' && closingTags == ''){
                                    closingTags = "</${currentList}>\n"
                                    currentList = ""
                                }
                                break;
                            }
                        }

                        if(currentList != '' && closingTags != ''){
                            sb.append("</${currentList}>\n")
                            currentList = ""
                        }

//logger_.debug "closingTags: "+closingTags
//logger_.debug "openingTags: "+openingTags

                        sb.append(closingTags)
                        sb.append(openingTags)


                        def itemName = route[route.length-1];
                        if(itemName.indexOf('[')!= -1){
                            //It's an array item
                            if(itemName.substring(0,itemName.indexOf('[')) != currentList){
                                if(currentList !=''){
                                    sb.append("</${currentList}>\n")
                                }
                                //It's a new array
                                currentList = itemName.substring(0,itemName.indexOf('['))
                                sb.append("<${currentList}>\n")
                            }
                            def itemIndx = itemName.substring(itemName.indexOf('[')+1,itemName.indexOf(']'))
                            sb.append(getXMLObjectItem(item,"item"))

                        }else{
                            if(currentList != ''){
                                sb.append("</${currentList}>\n")
                                currentList = ""
                            }
                            sb.append(getXMLObjectItem(item,itemName))

                        }
                        old = route;


                    }
                }else{
                    inSubnodes = false;

                    def route = item.split("\\.");
                    // Calculate necessary close and open tag in one
                    String openingTags = "";
                    String closingTags = ""
                    if (old != null) {
                        int mx = Math.max(old.length-1,route.length-1)
                        for(int i=0;i<mx;i++){
                            if(route[i] != old[i]) {
                                for(int j=old.length-2;j>=i;j--)
                                    closingTags += "</"+old[j]+">";
                                for(int j=i;j<route.length-1;j++)
                                    openingTags += "<"+route[j]+">";
                                break;
                            }
                        }
                        old = null
                    }

                    if(item.indexOf('[')!= -1){
                        //It's an array item
                        if(item.substring(0,item.indexOf('[')) != currentList){
                            if(currentList !=''){
                                sb.append("</${currentList}>\n")
                            }
                            //It's a new array
                            currentList = item.substring(0,item.indexOf('['))
                            sb.append("<${currentList}>\n")
                        }
                        sb.append(closingTags)
                        sb.append(openingTags)

                        def itemIndx = item.substring(item.indexOf('[')+1,item.indexOf(']'))
                        sb.append(getXMLObjectItem(item,"item"))

                    }else{
                        if(currentList != ''){
                            sb.append("</${currentList}>\n")
                            currentList = ""
                        }
                        sb.append(closingTags)
                        sb.append(openingTags)

                        sb.append(getXMLObjectItem(item,item))
                    }

                    old = route;
                }
            }
////logger_.debug "OUT OF THE AREA - WILL NEED TO CLOSE ALL TAGS!!!!"
////logger_.debug "currentList = "+currentList
////logger_.debug "old = "+old
            if(currentList != ''){
                sb.append("</${currentList}>\n")
                currentList = ""
            }
            if(old != null) {
                for(int i=old.length-2;i>=0;i--)
                    sb.append("</${old[i]}>")
            }




        }else{
            //Es Ocontrol, lo hacemos distinto
            sb.append(getXMLObjectItem('ALIAS','ALIAS'))
            sb.append(getXMLObjectItem('SIGNATURE','SIGNATURE'))
            if(getValue('EDITION') != null)
                sb.append(getXMLObjectItem('EDITION','EDITION'))
            if(getValue('OBJECTIVE') != null)
                sb.append(getXMLObjectItem('OBJECTIVE','OBJECTIVE'))
            if(getValue('EDITIONDATE') != null)
                sb.append(getXMLObjectItem('EDITIONDATE','EDITIONDATE'))
            if(error){
                sb.append("<ERRORCODE>WS20</ERRORCODE>\n")
                def iErrorCount = 0;
                try{
                    iErrorCount = (getValue('ERRORCOUNT')!=null &&   getValue('ERRORCOUNT') != '' && getValue('ERRORCOUNT') != '10.45')?Integer.parseInt(getValue('ERRORCOUNT')):0;
                }catch(Exception e){}
                def sErrorMessage = "Error calling DA: "
                for(int i=0;i<iErrorCount;i++){
                    sErrorMessage += getValue("ERROR[${i+1}]")
                    if(i<iErrorCount-1){
                        sErrorMessage += ", "
                    }
                }
                sb.append("<ERRORMSG>${sErrorMessage}</ERRORMSG>\n")
            } else { // New from v2.4 - Client may want to receive specific value even in case of success
                if ( "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('sis.incSucError') )) {
                    sb.append("<ERRORCODE>" + MyInterfaceBrokerProperties.getPropertyValue('sis.sucErrorCode') + "</ERRORCODE>\n")
                    sb.append("<ERRORMSG>" + MyInterfaceBrokerProperties.getPropertyValue('sis.sucErrorMsg') + "</ERRORMSG>\n")
                }
            }
            ////logger_.debug "Value of ${applicationIdHeaderField}"
            if(applicationIdHeaderField != null && applicationIdHeaderField.trim()!="" && applicationIdHeaderField.trim()!="null"){
                sb.append(getXMLObjectItem(applicationIdHeaderField,applicationIdHeaderField))
            }
            if(manFields != null){
                manFields.each {
                    sb.append(getXMLObjectItem(it,it))
                }
            }
            if(optFields != null){
                optFields.each {
                    if(getValue(it))
                        sb.append(getXMLObjectItem(it,it))
                }
            }


        }

        sb.append("</${layoutName_}>");

        ////logger_.debug sb.toString();
        return sb.toString()


    }

    private String getXMLObjectItem(String name,String lbl){
        Object o = areaContents_.get(name)
        //logger_.debug "Creating element for $name >> $o"
        if(o==null) return ""//<"+lbl+" xsi:nil=\"true\"/>" ;
        String res = "<"+lbl+">"
        if(o instanceof String) res += StringEscapeUtils.escapeXml((String)o) ;
        else if(o instanceof Integer) res += formatterNoDec.format(((Integer)o).intValue())
        else if(o instanceof Long) res += formatterNoDec.format(((Long)o).longValue())
        else if(o instanceof Double){
            long aux = ((Double) o).longValue()
            double auxDec = ((Double) o).doubleValue()
            if(auxDec-aux != 0){
                res += formatterDec.format(((Double)o).doubleValue()).replaceAll(",",".")
            }else{
                res += formatterNoDec.format(((Double)o).doubleValue())
            }
        } else if(o instanceof Float) {
            long aux = ((Float) o).longValue()
            float auxDec = ((Float) o).doubleValue()
            if(auxDec-aux != 0){
                res += formatterDec.format(((Float)o).floatValue())
            }else{
                String isfloat = getTenantProperties().get("ZEROTOFLOAT");
                if (isfloat != null && isfloat.equals("TRUE"))
                    res += formatterDec.format(((Float)o).floatValue())
                else
                    res += formatterNoDec.format(((Float)o).floatValue())
            }
        } else if(o instanceof BigDecimal) {
            //logger_.info "Value ${((BigDecimal)o).toPlainString()} == Precision ${((BigDecimal)o).precision} == Scale ${((BigDecimal)o).scale()}"
            res += ((BigDecimal)o).toPlainString();
            if (res.endsWith(".0")) res=res.substring(0,res.length()-2)
        } 
		else if(o instanceof Date) {
			if(name.equalsIgnoreCase("EDITIONDATE")){
				if(dateFormat=="T"){
					res += myTimestampFormatter.format((Date)o)
				}
				else{
					res += myDateFormatter.format((Date)o)
					}
			}
			else{
			res += myDateFormatter.format((Date)o)}
		}
        else res += o.toString()
        return (res+"</"+lbl+">\n")
    }


    private String getPlainObjectItem(String name){
        if (name.indexOf('[') != -1) name = myNormaliseXMLName(name)
        Object o = areaContents_.get(name)
        if(o==null) return '';
        String res = ""
        if(o instanceof String) res += (String)o
        else if(o instanceof Integer) res += formatterNoDec.format(((Integer)o).intValue())
        else if(o instanceof Long) res += formatterNoDec.format(((Long)o).longValue())
        else if(o instanceof Double){
            long aux = ((Double) o).longValue()
            double auxDec = ((Double) o).doubleValue()
            if(auxDec-aux != 0){
                res += formatterDec.format(((Double)o).doubleValue()).replaceAll(",",".")
            }else{
                res += formatterNoDec.format(((Double)o).doubleValue())
            }
        } else if(o instanceof Float) {
            long aux = ((Float) o).longValue()
            float auxDec = ((Float) o).doubleValue()
            if(auxDec-aux != 0){
                res += formatterDec.format(((Float)o).floatValue())
            }else{
                res += formatterNoDec.format(((Float)o).floatValue())
            }
        } else if(o instanceof BigDecimal) {
            //logger_.info "Value ${((BigDecimal)o).toPlainString()} == Precision ${((BigDecimal)o).precision} == Scale ${((BigDecimal)o).scale()}"
            res += ((BigDecimal)o).toPlainString();
            if (res.endsWith(".0")) res=res.substring(0,res.length()-2)
        } else if(o instanceof Date) {
			if(name.equalsIgnoreCase("EDITIONDATE")){
				if(dateFormat=="T"){
					res += myTimestampFormatter.format((Date)o)
				}
				else{
					res += myDateFormatter.format((Date)o)
					}
			}
			else{
			res += myDateFormatter.format((Date)o)}
		}
        else res += o.toString()
        return res;
    }

	@Synchronized
    private void freeDef(Object o, String alias, Message message, String id){
        String cacheId = alias + "-"+id;

        if(cache[cacheId] != null){
            //logger_.info "Liberando ${cache[cacheId].getClass()}"
            cache[cacheId].push(o)
            //logger_.info "Liberado"
        }else{
            //logger_.info "Esta vacio ${cacheId}"
        }
        //message.get('timeAgent').addPoint("${cacheId}_${layoutName_}_FREECACHETYPES${cache[alias].size()}");
    }


	@Synchronized
    private Hashtable loadDefinition(GPathResult xsd, String alias, Message message, String id){
        String cacheId = alias + "-"+id;
        //logger_.debug "Entering in loadDefinition"
        try{

            def cleanCache = jmx?JMXHelper.needToResetCache(alias,NBSMManager.CACHE_BLOCK_DEFINITION):false;
            //logger_.debug "Clean cache ${cleanCache}"
            //logger_.debug 'Creating definition';
            if(cache[cacheId] == null){
                cache[cacheId] = new Stack();
            }else if(cleanCache){
                cache.keySet().toArray().each{
                    if(it.startsWith(alias+"-")){
                        //logger_.debug "Cleaning cache for ${it}"
                        cache[it] = new Stack();
                    }
                }
                //logger_.debug "Cleaning initialisedValues $initialisedValues"
                def toRemove = []
                initialisedValues.keySet().each{
                    //logger_.debug "it: $it"
                    if(it.startsWith(alias+" -")){
                        //logger_.debug "Removing from initialisedValues ${it}"
                        toRemove.add(it);
                    }
                }
                toRemove.each{
                    initialisedValues.remove(it)
                }
                //logger_.debug "initialisedValues after removing $initialisedValues"

                JMXHelper.cacheClenDone(alias,NBSMManager.CACHE_BLOCK_DEFINITION)
            }
            //I need to load the full definition for each dictionary
            if(cache[cacheId].empty()){
                //message.get('timeAgent').addPoint("${layoutName_}_STARTNEWTYPES");
                //logger_.debug "Creating empty"
                Hashtable ret = new Hashtable();
                xsd.children().each {
                    //logger_.debug '>>>>> it.@name.text() = ' +  it.@name.text();
                    def type = it.@type.text();
                    if(type == null || type == ""){
                        if(isArray(it)){
                            //logger_.debug "It's an array ${it.@name.text()} with ${it.complexType.sequence.element.@maxOccurs.text()} children and ref ${it.complexType.sequence.children()[0].@ref.text()}"
                            type = "array[${it.complexType.sequence.element.@maxOccurs.text()}]#" + (it.complexType.sequence.children()[0].@ref.text()=='date_format'?'xs:date':it.complexType.sequence.element.@type.text());
                        }else if(isDynamicArray(it)){
                            //logger_.debug "It's an dynamic array (David2) ${it.@name.text()}"
                            dynamicArrays.put(it.@name.text(),it.complexType.sequence.element.@maxOccurs.text());
							//logger_.debug "dynamicArrays $dynamicArrays"
                            def subNodePrefix = it.@name.text() + ".";
                            def subnodeDefinition = loadDynamicArrayDefinition(it,subNodePrefix)
                            ret.putAll(subnodeDefinition)
                            type=''
                        }else{ //If not an array, it's a subnode
                            //logger_.debug "${it.@name.text()} its a subnode"
                            def nodePrefix = it.@name.text() + ".";
                            def subnodeDefinition = loadSubnodeDefinition(it,nodePrefix)
                            ret.putAll(subnodeDefinition)
                            type=''
                        }
                    }
                    if(type!='')
                        ret[it.@name.text()] = type;
                }
                //logger_.debug "CACHE[${cacheId}]"+ret
                //message.get('timeAgent').addPoint("${layoutName_}_ENDNEWTYPES");
                dynArrCache.put(cacheId,dynamicArrays)
                //logger_.debug "Returning ret"
                return ret;
            }else{
                //message.get('timeAgent').addPoint("${layoutName_}_CACHETYPES");
                dynamicArrays = getDynArrayCache(alias,message,id)
                logger_.debug "Reusing cached"
                return (Hashtable)cache[cacheId].pop();

            }
        }catch(Exception e){
            logger_.info e.getMessage() + " on ${alias}: ${e.getStackTrace()}";
        }

    }

    synchronized private Map getDynArrayCache(String alias, Message message, String id){
        String cacheId = alias + "-"+id;
        return dynArrCache[cacheId]
    }


    synchronized private Hashtable loadSubnodeDefinition(GPathResult xsd, String nodePrefix){
        Hashtable ret = new Hashtable();
        xsd.complexType.all.children().each() {
            //logger_.debug nodePrefix + ':::it.@name.text() = ' +  it.@name.text();
            def type = it.@type.text();
            //logger_.debug "Type=${type}"
            if(type == null || type == ""){
                if(isArray(it)){
                    //logger_.debug "It's an array ${it.@name.text()} with ${it.complexType.sequence.element.@maxOccurs.text()} children and ref ${it.complexType.sequence.children()[0].@ref.text()}"
                    type = "array[${it.complexType.sequence.element.@maxOccurs.text()}]#" + (it.complexType.sequence.children()[0].@ref.text()=='date_format'?'xs:date':it.complexType.sequence.element.@type.text());
                }else if(isDynamicArray(it)){
                    //logger_.debug "It's an dynamic array ${nodePrefix + (it.@name.text())}"
                    dynamicArrays.put(nodePrefix + (it.@name.text()),it.complexType.sequence.element.@maxOccurs.text());
                    def subNodePrefix = nodePrefix + (it.@name.text()) + ".";
                    def subnodeDefinition = loadDynamicArrayDefinition(it,subNodePrefix)
                    ret.putAll(subnodeDefinition)
                    type=''
                }else{ //If not an array, it's a subnode
                    //logger_.debug "${it.@name.text()} its a subnode"
                    def subNodePrefix = nodePrefix + (it.@name.text()) + ".";
                    def subnodeDefinition = loadSubnodeDefinition(it,subNodePrefix)
                    ret.putAll(subnodeDefinition)
                    type=''
                }
            }
            if(type!='')
                ret[nodePrefix + it.@name.text()] = type;
        }
        return ret;
    }

    synchronized private Hashtable loadDynamicArrayDefinition(GPathResult xsd, String nodePrefix){
        Hashtable ret = new Hashtable();
        xsd.complexType.sequence.element.complexType.all.children().each() {
            //logger_.debug nodePrefix + ':::it.@name.text() = ' +  it.@name.text();
            def type = it.@type.text();
            //logger_.debug "Type=${type}"
            if(type == null || type == ""){
                if(isArray(it)){
                    type = "array[${it.complexType.sequence.element.@maxOccurs.text()}]#" + it.complexType.sequence.element.@type.text();
                }else if(isDynamicArray(it)){
                    //logger_.debug "It's an dynamic array ${nodePrefix + (it.@name.text())}"
                    //dynamicArrays.put(nodePrefix + (it.@name.text()),'');
                    dynamicArrays.put(nodePrefix + (it.@name.text()),it.complexType.sequence.element.@maxOccurs.text());
                    def subNodePrefix = nodePrefix + (it.@name.text()) + ".";
                    def subnodeDefinition = loadDynamicArrayDefinition(it,subNodePrefix)
                    ret.putAll(subnodeDefinition)
                    type=''
                }else{ //If not an array, it's a subnode
                    //logger_.debug "${it.@name.text()} its a subnode"
                    def subNodePrefix = nodePrefix + (it.@name.text()) + ".";
                    def subnodeDefinition = loadSubnodeDefinition(it,subNodePrefix)
                    ret.putAll(subnodeDefinition)
                    type=''
                }
            }
            if(type!='')
                ret[nodePrefix + it.@name.text()] = type;
        }
        return ret;
    }

    private boolean isArray(GPathResult xsd){
        //logger_.debug "Checking if ${xsd.@name.text()} is an Array with ${xsd.complexType.sequence.children()[0].@ref.text()}"
        //logger_.debug "Result of isArray ${(xsd.complexType.sequence.element.@name.text() == 'item' || xsd.complexType.sequence.children()[0].@ref.text() == 'date_format' )}"
        return (xsd.complexType.sequence.element.@name.text() == 'item' || xsd.complexType.sequence.children()[0].@ref.text() == 'date_format' )
    }

    private boolean isDynamicArray(GPathResult xsd){
        //logger_.debug "Checking if ${xsd.@name.text()} is an dynamicArray with ${xsd.complexType.sequence.element.@name.text()}"

        return (xsd.complexType.sequence.element.@name.text() == 'element')
    }

 
     public int fromPlain(String[] parts, int offset, List<TypeElement> types, boolean doTrace = false) throws Exception
    {
        String fieldName = "";
        int nIndx = 0;

        try{
            int dataType = 0;
            for(int i=0;i<types.size();i++){
                dataType= types.get(i).type;
                fieldName= types.get(i).name;
                if (fieldName.indexOf('[') != -1) fieldName = myNormaliseXMLName(fieldName)
                //logger_.debug "Accessing parts ${offset+nIndx} with datatype ${dataType} and name ${fieldName} and value ${parts[offset+nIndx]}"
                switch (dataType){
                    case 1:
                        //f.append("setValue(\"${fieldName}\",\"${parts[offset+nIndx]}\")");
                        //setValue(fieldName,parts[offset+nIndx],doTrace);
                        areaContents_.put(fieldName,parts[offset+nIndx])
                        //if(!order_.contains(fieldName)) order_.add(fieldName);
                        nIndx++;
                        break;
                    case 2:
                        if(parts[offset+nIndx] != null && parts[offset+nIndx]!=null && parts[offset+nIndx].trim()!=''){
                            areaContents_.put(fieldName,new BigDecimal(parts[offset+nIndx].trim()))

                        }else
                        //setValue(fieldName,null,doTrace)
                            areaContents_.put(fieldName,null)

                        //if(!order_.contains(fieldName)) order_.add(fieldName);
                        nIndx++;
                        break;
                    case 3:
                        if(parts[offset+nIndx] != null && parts[offset+nIndx]!=null && parts[offset+nIndx].trim()!=''){
                            //f.append("setValue(\"${fieldName}\",new BigDecimal(\"${parts[offset+nIndx]}\"))");
                            //setValue(fieldName,new BigDecimal(parts[offset+nIndx]),doTrace)
                            areaContents_.put(fieldName,new BigDecimal(parts[offset+nIndx].trim()))

                        }
                        else
                        //setValue(fieldName,null,doTrace)
                            areaContents_.put(fieldName,null)
                        //if(!order_.contains(fieldName)) order_.add(fieldName);
                        nIndx++;
                        break;
                    case 4:
                        //f.append("setValue(\"${fieldName}\",myDateFormatter.parse(\"${parts[offset+nIndx]}\"))");
                        //setValue(fieldName,myDateFormatter.parse(parts[offset+nIndx]))
                        areaContents_.put(fieldName,myDateFormatter.parse(parts[offset+nIndx]))
                        //if(!order_.contains(fieldName)) order_.add(fieldName);
                        //areaContents_.put(fieldName,new Date())
                        nIndx++;
                        break;
                    default:
                        //f.append("setValue(\"${fieldName}\",\"${parts[offset+nIndx]}\")");
                        setValue(fieldName,parts[offset+nIndx],doTrace);
                        //if(!order_.contains(fieldName)) order_.add(fieldName);
                        nIndx++;
                        break;
                }

            }
            //f.close();
            return nIndx;
        }catch(NullPointerException npe){
            throw npe;
        }catch(NumberFormatException nfe){
            throw new NumberFormatException("Field ${layoutName_}:${fieldName} with value ${parts[offset+nIndx]} is not valid.")
        }catch(Exception e){
            throw e;
        }


    }
    public String toPlain(String separator, int loglevel, String appIdHeaderField, boolean error =false)
    {
        StringBuffer sb = new StringBuffer(3000);
        if(layoutName_ == 'OCONTROL')
        {
            //Es Ocontrol, lo hacemos distinto
            sb.append(getPlainObjectItem('ALIAS'))
            sb.append(separator)
            sb.append(getPlainObjectItem('SIGNATURE'))
            sb.append(separator)
            if(appIdHeaderField != null && appIdHeaderField.trim() != "" && appIdHeaderField.trim() != "null")
            {
                sb.append(getPlainObjectItem(appIdHeaderField))
                sb.append(separator)
            }
            sb.append(loglevel)
            sb.append(separator)
            if(getValue('EDITION') != null)
                sb.append(getPlainObjectItem('EDITION'))
            sb.append(separator)
            if(getValue('OBJECTIVE') != null)
                sb.append(getPlainObjectItem('OBJECTIVE'))
            sb.append(separator)
            if(getValue('EDITIONDATE') != null)
                sb.append(getPlainObjectItem('EDITIONDATE'))
            sb.append(separator)
            if(error)
            {
                sb.append("WS20")
                sb.append(separator)
                def iErrorCount = 0;
                try{
                    iErrorCount = (getValue('ERRORCOUNT')!=null &&   getValue('ERRORCOUNT') != '' && getValue('ERRORCOUNT') != '10.45')?Integer.parseInt(getValue('ERRORCOUNT')):0;
                }catch(Exception e){}
                def sErrorMessage = "Error calling DA: "
                for(int i=0;i<iErrorCount;i++)
                {
                    sErrorMessage += getValue("ERROR[${i+1}]")
                    if(i<iErrorCount-1){
                        sErrorMessage += ", "
                    }
                }
                sb.append(sErrorMessage)
                //sb.append(separator)
            }else{
                sb.append(separator)
                //sb.append(separator)
            }

        }
        else
        {
            orderPlain_.each
                    {item ->
                        //logger_.debug "${item} --> ${getPlainObjectItem(item)}"
                        //sb.append("${item}:");
                        sb.append(separator);
                        sb.append(getPlainObjectItem(item));
                    }
        }

        return sb.toString();
    }

    public String toPlainWithouOrder(String separator, List<TypeElement> types, int loglevel, String appIdHeaderField, boolean error = false){
        StringBuffer sb = new StringBuffer(3000);
        if(layoutName_ == 'OCONTROL'){
            //Es Ocontrol, lo hacemos distinto
            sb.append(getPlainObjectItem('ALIAS'))
            sb.append(separator)
            sb.append(getPlainObjectItem('SIGNATURE'))
            sb.append(separator)
            if(appIdHeaderField != null && appIdHeaderField.trim() != "" && appIdHeaderField.trim() != "null"){
                //logger_.debug "El applicationIdHeaderField es ${appIdHeaderField} y tiene valor ${getPlainObjectItem(appIdHeaderField)}"
                sb.append(getPlainObjectItem(appIdHeaderField))
                sb.append(separator)
            }
            sb.append(getPlainObjectItem('DALOGLEVEL'))
            sb.append(separator)
            if(getValue('EDITION') != null)
                sb.append(getPlainObjectItem('EDITION'))
            sb.append(separator)
            if(getValue('OBJECTIVE') != null)
                sb.append(getPlainObjectItem('OBJECTIVE'))
            sb.append(separator)
            if(getValue('EDITIONDATE') != null)
                sb.append(getPlainObjectItem('EDITIONDATE'))
            sb.append(separator)
            if(error){
                sb.append("WS20")
                sb.append(separator)
                def iErrorCount = 0;
                try{
                    iErrorCount = (getValue('ERRORCOUNT')!=null &&   getValue('ERRORCOUNT') != '' && getValue('ERRORCOUNT') != '10.45')?Integer.parseInt(getValue('ERRORCOUNT')):0;
                }catch(Exception e){}
                def sErrorMessage = "Error calling DA: "
                for(int i=0;i<iErrorCount;i++){
                    sErrorMessage += getValue("ERROR[${i+1}]")
                    if(i<iErrorCount-1){
                        sErrorMessage += ", "
                    }
                }
                sb.append(sErrorMessage)
                sb.append(separator)
            }else{
                sb.append(separator)
                sb.append(separator)
            }

        }else{
            for(int i=0;i< types.size();i++){
                //logger_.debug "${item} --> ${getPlainObjectItem(item)}"
                //sb.append("${item}:");
                sb.append(getPlainObjectItem(types.get(i).name));
                sb.append(separator);
            }
        }
        String ret = sb.toString();
        //logger_.debug "${layoutName_} ::: " + ret;
        return ret.substring(0,ret.length()-1);
    }

    public String toPlainHeader(String separator, String appIdHeaderField, boolean error = false)
    {
        StringBuffer sb = new StringBuffer(3000);
        if(layoutName_ == 'OCONTROL')
        {
            //Es Ocontrol, lo hacemos distinto
            sb.append('ALIAS')
            sb.append(separator)
            sb.append('SIGNATURE')
            sb.append(separator)
            if(appIdHeaderField != null && appIdHeaderField.trim() != "" && appIdHeaderField.trim() != "null"){
                sb.append(appIdHeaderField)
                sb.append(separator)
            }
            sb.append('DALOGLEVEL')
            sb.append(separator)
            sb.append('EDITION')
            sb.append(separator)
            sb.append('OBJECTIVE')
            sb.append(separator)
            sb.append('EDITIONDATE')
            sb.append(separator)
            sb.append('ERRORCODE')
            sb.append(separator)
            sb.append('ERRORMSG')
            //sb.append(separator)
        }
        else
        {
            order_ = new ArrayList(areaContents_.keySet())
            Collections.sort(order_,new AreaContentComparator())

            orderPlain_.each
                    {  item ->
                        //logger_.debug "${item} --> ${item)}"
                        sb.append(separator);
                        sb.append(item);
                    }
        }

        return sb.toString();
    }

    public static String myNormaliseXMLName(String name) {
        return name;
        /*
        if (name.endsWith(']')) {
		  // A.B.C[X]        ==>     A.B.C.IX
		    int indx = name.lastIndexOf('[')
		    name = name.substring(0,indx)+".I"+name.substring(indx+1)

		}
		// A.B[X].C[Y].D   ==>     A.BX.CY.D
		*/
        //return name.replaceAll("]", "").replace("[", "");
    }

    public void increaseSize(String name){
        //logger_.debug "INCREASING THE SIZE OF THE DYN ARRAY $name"
        fieldSize_[name]=1+(fieldSize_[name]?:0)
        /*if(fieldSize_.containsKey(name)){
            fieldSize_[name]++;
        }else{
            fieldSize_[name] = 1;
        }*/
        //logger_.debug "$fieldSize_"
    }


    private List orderList(List unordered){
        def ordered = []
        unordered.each{ item ->
            if(!ordered.contains(item)){
                if(item.endsWith("]")){
                    //Es un array, vamos a ordenarlo
                    int arrayIndex = item.lastIndexOf("[")
                    if(arrayIndex != -1) {
                        def arrayName = item.substring(0,arrayIndex)
                        int index = 1;
                        def arrayItemName = arrayName + "[${index++}]"
                        while(unordered.contains(arrayItemName)){
                            ordered.add(arrayItemName)
                            arrayItemName = arrayName + "[${index++}]"
                        }
                    }
                }else{
                    ordered.add(item)
                }

            }
        }
        return ordered;
    }

}