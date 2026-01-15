package com.experian.util

/**
 * Link to PowerCurve-based Decision Agent entry points
 *
 * @author ekieffer
 *
 */
class DA2X implements IDAProvider {
    private static final DEPLOY_PREFIX = "DirectoryBasedStrategyLoader.path"
    
    private final objectInterface
    private final byteInterface
    private final managementInterface
    private final jsonInterface
    
    public DA2X() {
        objectInterface = com.experian.eda.decisionagent.interfaces.os390.BatchJSEMObjectInterface.instance()
        byteInterface = com.experian.eda.decisionagent.interfaces.os390.BatchJSEMByteInterface.instance()
        managementInterface = com.experian.eda.component.decisionagent.DAManagementInterface
        jsonInterface = com.experian.eda.decisionagent.interfaces.nt.NTJSEMJSONInterface.instance()
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
    public Object getManagementInterface() {
        managementInterface
    }

    @Override
    public String getDeploymentFoldersPrefix() {
        DEPLOY_PREFIX
    }

    @Override
    public String getJSONInterface() {
        jsonInterface
    }
}
