package com.experian.util;

import com.experian.stratman.datasources.runtime.IData;
import com.experian.eda.enterprise.core.api.Message;
// Logger
import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger;

import groovy.util.slurpersupport.GPathResult;

import org.apache.commons.lang.StringEscapeUtils;

import java.util.*;
import java.text.*;
import java.math.BigDecimal;

import com.experian.jmx.*;

                                                                                      
public class DataArea implements IData {

    private static Hashtable cache = new Hashtable();
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

    private NumberFormat formatterNoDec = new DecimalFormat("##");
    private NumberFormat formatterDec = new DecimalFormat("##.##");

	protected static       boolean    jmx = "Y".equals(MyInterfaceBrokerProperties.getPropertyValue('jmx.active'));
    protected static       String    defaultDecimal = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.decimal.default')
    protected static       String    defaultDouble = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.double.default')
    protected static       String    defaultDate = MyInterfaceBrokerProperties.getPropertyValue('da.initialise.date.default')
	
    private SimpleDateFormatThreadSafe myDateFormatter = new SimpleDateFormatThreadSafe("yyyy-MM-dd");
	private SimpleDateFormatThreadSafe myTimestampFormatter = new SimpleDateFormatThreadSafe("yyyy-MM-dd HH:mm:ss");
	protected       String    dateFormat = MyInterfaceBrokerProperties.getPropertyValue('editiondate.format')!=null? MyInterfaceBrokerProperties.getPropertyValue('editiondate.format').toUpperCase():"D"
	
	// Put in cache pre-initialised areaContents_
	private static Map<String, Map> initialisedValues = new HashMap();
	private static Map<String, Map> initialisedOrder = new HashMap();
    /**
    * Creates a new instance of DataArea.
    * @param layoutName The layout name corresponding to a
    * physical data source.
    */

    public DataArea(String layoutName, ExpLogger logger)
    {
        layoutName_ = layoutName;
        //areaContents_ = new HashMap();
        //areaContents_ = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        areaContents_ = new TreeMap<String, String>(new com.experian.util.AreaContentComparator())
        logger_ = logger;
        if (logger == null) actTrace = false
        order_ = new ArrayList();
		orderPlain_ = new ArrayList();
        
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
    * @param name The external name of thge characteristic.
    * @param value The value as a Number, String or Date object.
    */

    public void setValue(String name, Object value, boolean doTrace = false)
    {
        //if (actTrace) logger_.debug """Set value of characteristic <${layoutName_}.${name}> to <${(value == null ? "null" : value)}>""";
        if (name.indexOf('[') != -1) name = myNormaliseXMLName(name)
		areaContents_.put(name, value);
        //if(!order_.contains(name))
        //  order_.add(name);
		if(!orderPlain_.contains(name)) {
            orderPlain_.add(name);

        }
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

        Hashtable<String, String> types = loadDefinition(dicRootElements, alias, message, dicName);


        initialiseDefaultValues(alias, dicName, types, doTrace);
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
		Hashtable<String, String> types = loadDefinition(dicRootElements, alias, message, dicName);
        //if (actTrace) logger_.debug "Types: ${types}";
        //logger_.debug "dicRootElements " + dicRootElements.name()
        
        //message.get('timeAgent').addPoint("${layoutName_}_LOADTYPES");
        
		initialiseDefaultValues(alias, dicName, types, doTrace);
        xml.children().each { field ->
            //For each data_type we do different things
            //Search the xsd to look at the data type

            int nChildren = 0;
            if(field.children() != null)
              nChildren = field.children().size();
            
            //if (actTrace) logger_.debug "Seaching data type for field " + field.name() + " with ${nChildren} children and being ${field.children()[0].name().toUpperCase()} its first child";
            if(nChildren > 0 && field.children()[0].name().toUpperCase() != 'I1'){
                //logger_.debug "${field.name()} is a subnode";
                fromXMLSubnode(field,types,field.name()+".",doTrace)
                
            }else{
            
              String dataType = types[field.name()]
                int arrayLength = 0;
              if(dataType != null){
                //logger_.debug "data type for field " + field.name() + " " + dataType
                if(dataType == null || dataType == '') dataType = "array"
                
                //if(actTrace) logger_.debug "dataType = " + dataType 
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
                            String fieldName = field.name()+ "[" + arrayItem.name().substring(1) + "]";
                            switch (arrayItemDataType){
                                case 'xs:string': 
                                     setValue(fieldName,arrayItem.text(),doTrace)
                                     break;
                                case 'xs:double':
                                     setValue(fieldName,new Double(arrayItem.text()),doTrace)
                                     break;
                                case 'xs:decimal':
                                     if(arrayItem != null && arrayItem.text()!=null && arrayItem.text().trim()!='')
                                        setValue(fieldName,new BigDecimal(arrayItem.text().trim()),doTrace)
                                     //else
                                       // setValue(fieldName,null,doTrace)
                                     break;
                                case 'decimal-or-empty':
                                     if(arrayItem != null && arrayItem.text()!=null && arrayItem.text().trim()!='')
                                        setValue(fieldName,new BigDecimal(arrayItem.text().trim()),doTrace)
                                     break;
                                case 'xs:date':
                                     if(arrayItem != null && arrayItem.text()!=null && arrayItem.text().trim()!='')
                                      setValue(fieldName,myDateFormatter.parse(arrayItem.text().trim()))
                                    // else
                                      //setValue(fieldName,null,doTrace)
                                     break;
                                }
                            
                         }
                         break;
                   }
                 }else{
                    logger_.debug "${field.name()} is not defined or has no datatype"
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
		//logger_.debug("Going to initialize default values for " + dicName)
		if(initialisedValues.containsKey(alias + " - " + dicName)) {
			areaContents_ = initialisedValues.get(alias + " - " + dicName).clone();
			orderPlain_ = initialisedOrder.get(alias + " - " + dicName).clone();
			return;
		}
		
		// Check if this strategy requires initialisation
		String strategiesToInitialise = MyInterfaceBrokerProperties.getPropertyValue("da.initialise.values");
		if(strategiesToInitialise == null)
			return;
		
		String[] strategiesToInitialiseArray = strategiesToInitialise.trim().split("\\|",-1);
		boolean found = false;
		for(String strategyName: strategiesToInitialiseArray)
			if(strategyName.equalsIgnoreCase(alias)) {
				found = true;
				break;
			}
		
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

		//logger_.info("Initialising values for strategy <" + alias + "> layout <"+dicName+">");

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
			String dataType = types[fieldName];
			String arrayItemDataType = null;
			int arrayLength = 0;
			if(dataType.indexOf("#")>=0){
				arrayItemDataType = dataType.split("#")[1]
				dataType = dataType.split("#")[0]
                def iinit = dataType.indexOf("[");
                if(iinit != -1){
                    //logger_.debug ">>> Array Length = " + dataType.substring(iinit+1,dataType.length()-1);
                    arrayLength = Integer.parseInt(dataType.substring(iinit+1,dataType.length()-1))
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
					 setValue(fieldName,new Double(defaultDoubleSt),doTrace)
					 break;
				case 'xs:date':
					 setValue(fieldName,myDateFormatter.parse(defaultDateSt))
					 break;
				case 'array': //ARRAY
					 for(int i = 0; i<arrayLength; i++) {
						 String arrayFieldName = fieldName + "[" + (i+1) + "]";
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

		initialisedValues.put(alias + " - " + dicName, areaContents_.clone());
		initialisedOrder.put(alias + " - " + dicName, orderPlain_.clone());
	}
    
    public void fromXMLSubnode(GPathResult xml, Hashtable types, String prefix, boolean doTrace = false) throws Exception{
         xml.children().each { field ->
            //For each data_type we do different things
            //Search the xsd to look at the data type
            String prefixNam = prefix+field.name()
             
            int nChildren = 0;
            if(field.children() != null)
              nChildren = field.children().size();
            
            //if (actTrace) logger_.debug "Seaching data type for field " + field.name() + " with ${nChildren} children and prefix: ${prefix}";
            
            if(nChildren > 0 && field.children()[0].name().toUpperCase() != 'I1'){
                //logger_.debug "${field.name()} is a subnode";
                fromXMLSubnode(field,types,prefixNam+".",doTrace)
            }else{
            
              String dataType = types[prefixNam]
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
                          String fieldName = prefixNam+ "[" + arrayItem.name().substring(1) + "]";
                          switch (arrayItemDataType){
                              case 'xs:string': 
                                   setValue(fieldName,arrayItem.text(),doTrace)
                                   break;
                              case 'xs:double':
                                   setValue(fieldName,new Double(arrayItem.text()),doTrace)
                                   break;
                              case 'xs:decimal':
                                   if(arrayItem != null && arrayItem.text()!=null && arrayItem.text().trim()!='')
                                      setValue(fieldName,new BigDecimal(arrayItem.text().trim()),doTrace)
                                   //else
                                     // setValue(fieldName,null,doTrace)
                                    break;
                              case 'decimal-or-empty':
                                   if(arrayItem != null && arrayItem.text()!=null && arrayItem.text().trim()!='')
                                   setValue(fieldName,new BigDecimal(arrayItem.text().trim()),doTrace)
                                   break;
                              case 'xs:date':
                                   if(arrayItem != null && arrayItem.text()!=null && arrayItem.text().trim()!='')
                                      setValue(fieldName,myDateFormatter.parse(arrayItem.text().trim()))
                                   //else
                                     // setValue(fieldName,null,doTrace)
                                   break;
                              }
                          
                       }
                       break;
                  }      
                }
              }
            //message.get('timeAgent').addPoint("${layoutName_}_TYPES_${field.name()}");
        }
    
    }
    
    
    public String toXML(String[] manFields, String[] optFields, String applicationIdHeaderField, boolean error = false){
        StringBuffer sb = new StringBuffer(50000);
        sb.append("<${layoutName_}>")
        String subnodo = ""
        String currentList = ""
        String[] old = null;
        boolean inSubnodes = false;

        order_ = new ArrayList(areaContents_.keySet())
        Collections.sort(order_,new AreaContentComparator())

        if(layoutName_ != 'OCONTROL')
		{
		  getOrderedKeyList().each
		  {  item ->
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
                  sb.append(getXMLObjectItem(item,"I"+itemIndx))
                  
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
                    sb.append(getXMLObjectItem(item,"I"+itemIndx))
                    
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
                  sb.append(getXMLObjectItem(item,"I"+itemIndx))
                  
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
//logger_.debug "OUT OF THE AREA - WILL NEED TO CLOSE ALL TAGS!!!!"
//logger_.debug "currentList = "+currentList
//logger_.debug "old = "+old
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
		  //logger_.debug "Value of ${applicationIdHeaderField}"
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
        
        //logger_.debug sb.toString();
        return sb.toString()
        
        
    }
   
    private String getXMLObjectItem(String name,String lbl){
      Object o = areaContents_.get(name)
      if(o==null) return "<"+lbl+" xsi:nil=\"true\"/>" ;
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
          //res += ((BigDecimal)o).toPlainString();
		  //if (res.endsWith(".0")) res=res.substring(0,res.length()-2)
          if (o.scale() > 0) {
              res += formatterDec.format(o)
          } else {
              res += formatterNoDec.format(o)
          }

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
    
    
    synchronized private void freeDef(Object o, String alias, Message message, String id){
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
    
    
    synchronized private Hashtable loadDefinition(GPathResult xsd, String alias, Message message, String id){
      String cacheId = alias + "-"+id;
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

        def toRemove = []
		def toRemoveOrder = []
        initialisedValues.keySet().each{
            //logger_.debug "it: $it"
            if(it.startsWith(alias+" -")){
                //logger_.debug "Removing from initialisedValues ${it}"
                toRemove.add(it);
            }
        }
		initialisedOrder.keySet().each{
            //logger_.debug "it: $it"
            if(it.startsWith(alias+" -")){
                //logger_.debug "Removing from initialisedValues ${it}"
                toRemoveOrder.add(it);
            }
        }
		
        toRemove.each{
            initialisedValues.remove(it)
        }
		toRemoveOrdr.each{
            initialisedOrder.remove(it)
        }
          
          JMXHelper.cacheClenDone(alias,NBSMManager.CACHE_BLOCK_DEFINITION)
        }
        //I need to load the full definition for each dictionary
        if(cache[cacheId].empty()){
          //message.get('timeAgent').addPoint("${layoutName_}_STARTNEWTYPES");
          Hashtable ret = new Hashtable();
          xsd.children().each {
           // logger_.debug 'it.@name.text() = ' +  it.@name.text();
            def type = it.@type.text();
            if(type == null || type == ""){
              if(isArray(it)){
                  //logger_.debug "It's an array ${it.@name.text()} with ${it.complexType.all.children().size()} children"
                 type = "array[${it.complexType.all.children().size()}]#"+it.complexType.all.children()[0].@type.text();
              }else{ //If not an array, it's a subnode
                // logger_.debug "${it.@name.text()} its a subnode"
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
          
          return ret;
        }else{
            //message.get('timeAgent').addPoint("${layoutName_}_CACHETYPES");
            return (Hashtable)cache[cacheId].pop();
        
        }
      }catch(Exception e){
        logger_.error e.getMessage() + " on ${alias}";
      }
      
    }
    
    synchronized private Hashtable loadSubnodeDefinition(GPathResult xsd, String nodePrefix){
        Hashtable ret = new Hashtable();
        xsd.complexType.all.children().each() {
            //logger_.debug nodePrefix + ':::it.@name.text() = ' +  it.@name.text();
            def type = it.@type.text();
            //logger_.debug "Type=${type}"
            if(type == null || type == ""){
              if(isArray(it)){
                 //logger_.debug "It's an array"
                  //logger_.debug "It's an array ${it.@name.text()} with ${it.complexType.all.children().size()} children"
                 type = "array[${it.complexType.all.children().size()}]#"+it.complexType.all.children()[0].@type.text();
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
      //logger_.debug "Checking if ${xsd.@name.text()} is an Array with ${xsd.complexType.all.children()[0].@name.text()}"
      
      return (xsd.complexType.all.children()[0].@name.text() == 'I1')
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
	         logger_.debug "Accessing parts ${offset+nIndx} with datatype ${dataType} and name ${fieldName} and value ${parts[offset+nIndx]}"
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
		logger_.debug "This is orderPlain: ${orderPlain_}"
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
		if (name.endsWith(']')) {
		  // A.B.C[X]        ==>     A.B.C.IX
		  int indx = name.lastIndexOf('[')
		  name = name.substring(0,indx)+".I"+name.substring(indx+1)
		}
		// A.B[X].C[Y].D   ==>     A.BX.CY.D
		return name.replaceAll("]", "").replace("[", "");
	 }

}
