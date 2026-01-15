package com.experian.util;

import org.apache.commons.io.IOUtils;

import com.experian.eda.framework.errorhandling.interfaces.logging.ExpLogger


/**
 * <p>
 * Enables version-agnostic access to the Decision Agent entry points, thus enabling support for both 
 * the SMG3 Decision Agent (1.3, 1.4) as well as the PowerCurve Decision Agent (2.X).
 * <p>
 * Identification of the Decision Agent to use is performed on first access to the DAAgnostic class by
 * looking at the "strategy.loader.class" property from "decisionagent.properties". Given that SMG3 will use 
 * "AlternateStrategyLoader", while PowerCurve will use "DirectoryBasedStrategyLoader", DAAgnostic will assume
 * PowerCurve is used unless this property is set to "AlternateStrategyLoader"
 * 
 * @author ekieffer
 * @see {@link com.experian.util.DA1X}, {@link com.experian.util.DA2X}
 *
 */
public class DAAgnostic {

    private static final ExpLogger logger = new ExpLogger(DAAgnostic.class)
    
    // Either DA1X for SMG3-based implementation or DA2X for PowerCurve-based implementation
    private static final IDAProvider PROVIDER
    
    static {
        String daVersion = "<Unknown>"
        try {
            Properties props = new Properties()
            InputStream inS = DAAgnostic.class.getClassLoader().getResourceAsStream("decisionagent.properties")
            try {
                props.load(inS)
                inS.close()
            } finally {
                IOUtils.closeQuietly(inS)
            }
           
            if (props.getProperty("strategy.loader.class")?.contains("AlternateStrategyLoader")||props.getProperty("strategy.loader.class")?.contains("AStrategyLoader")) {
                daVersion = "1.X"
                logger.info "Initializing for SMG3 DA 1.X"
                PROVIDER = new DA1X()
            } else {
                daVersion = "2.X"
                logger.info "Initializing for PowerCurve DA 2.X"
                PROVIDER = new DA2X()
            }
        } catch (all) {
            def errMsg = "Unable to initialize version-agnostic DA layer for $daVersion -- $all.message"
            logger.error errMsg
            throw new RuntimeException(errMsg,all)
        }
    }
    
    public static getManagementInterface() {
        return PROVIDER.getManagementInterface()
    }

    public static getObjectInterface() {
        return PROVIDER.getObjectInterface()
    }

    public static getByteInterface() {
        return PROVIDER.getByteInterface()
    }
    
    public static String getDeploymentFoldersPrefix() {
        return PROVIDER.getDeploymentFoldersPrefix()
    }

    public static String getJSONInterface() {
        return PROVIDER.getJSONInterface()
    }
}

interface IDAProvider {
    public getObjectInterface()
    public getByteInterface()
    public getManagementInterface()
    public getJSONInterface()
    public String getDeploymentFoldersPrefix()

/**
 * May be a bit cleaner to implement the ManagementInterface methods directly through the interface?
 * We probably would also need to do the same for <BatchInterface>.execute() for consistency
//    public boolean isStrategyLoaded(alias)
//    public unloadStrategy(alias)
//    public loadStrategy(alias)
 */
}
