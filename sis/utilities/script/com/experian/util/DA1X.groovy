package com.experian.util

/**
 * Link to SMG3-based Decision Agent entry points
 * 
 * @author ekieffer
 *
 */
class DA1X implements IDAProvider {
    private static final String DEPLOY_PREFIX = "alternate.path"
    
    private final objectInterface
    private final byteInterface
    private final managementInterface
    private final jsonInterface
    
    public DA1X() {
        // First need to ensure the PwC DA is not on the classpath, as it will hide the DA 1.X interface entry point by
        // exposing deprecated classes of the same name
        def pwcDaManagement = null
        try {
            pwcDaManagement = com.experian.eda.component.decisionagent.DAManagementInterface
        } catch (all) {}
        if (pwcDaManagement) {
            throw new RuntimeException("It seems like the PowerCurve DA binaries are on the classpath, you will need to remove them to use SIS for SMG3")
        }
        
        objectInterface = com.experian.stratman.decisionagent.business.OS390.Batch.BatchJSEMObjectInterface.instance()
        byteInterface = com.experian.stratman.decisionagent.business.OS390.Batch.BatchJSEMByteInterface.instance()
        managementInterface = com.experian.stratman.decisionagent.business.DAManagementInterface
        jsonInterface = null
    }

    @Override
    public Object getObjectInterface() {
        objectInterface
    }

    @Override
    public Object getByteInterface() {
        byteInterface
    }

    @Override
    public Object getJSONInterface() {
        jsonInterface
    }

    @Override
    public Object getManagementInterface() {
        managementInterface
    }

    @Override
    public String getDeploymentFoldersPrefix() {
        DEPLOY_PREFIX
    }
    
}
