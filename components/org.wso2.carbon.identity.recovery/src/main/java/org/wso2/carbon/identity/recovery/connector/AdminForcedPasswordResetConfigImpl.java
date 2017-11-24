package org.wso2.carbon.identity.recovery.connector;

import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.governance.common.IdentityConnectorConfig;
import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;

import java.util.*;

public class AdminForcedPasswordResetConfigImpl implements IdentityConnectorConfig {

    private static String connectorName = "admin-forced-password-reset";

    @Override
    public String getName() {
        return connectorName;
    }

    @Override
    public String getFriendlyName() {
        return "Password Reset";
    }

    @Override
    public String getCategory() {
        return "Account Management Policies";
    }

    @Override
    public String getSubCategory() {
        return "DEFAULT";
    }

    @Override
    public int getOrder() { return 0; }

    @Override
    public Map<String, String> getPropertyNameMapping() {
        Map<String, String> nameMapping = new HashMap<>();
        nameMapping.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_RECOVERY_LINK,
                "Enable Password Reset via Recovery Email");
        nameMapping.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_OTP,
                "Enable Password Reset via OTP");
        nameMapping.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_OFFLINE,
                "Enable Password Reset Offline");
        return nameMapping;
    }

    @Override
    public Map<String, String> getPropertyDescriptionMapping() {
        Map<String, String> descriptionMapping = new HashMap<>();
        descriptionMapping.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_RECOVERY_LINK,
                "User gets notified with a link to reset password");
        descriptionMapping.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_OTP,
                "User gets notified with a one time password to try with SSO login");
        descriptionMapping.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_OFFLINE,
                "An OTP generated and stored in users claims");
        return descriptionMapping;
    }

    @Override
    public String[] getPropertyNames() {

        List<String> properties = new ArrayList<>();
        properties.add(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_RECOVERY_LINK);
        properties.add(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_OTP);
        properties.add(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_OFFLINE);

        return properties.toArray(new String[properties.size()]);
    }

    @Override
    public Properties getDefaultPropertyValues(String tenantDomain) throws IdentityGovernanceException {

        String enableAdminPasswordResetWithRecoveryLink = "false";
        String enableAdminPasswordResetWithOTP = "false";
        String enableAdminPasswordResetOffline = "false";

        String adminPasswordRecoveryWithLinkProperty = IdentityUtil.getProperty(
                IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_RECOVERY_LINK);
        String adminPasswordResetWithOTPProperty = IdentityUtil.getProperty(
                IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_OTP);
        String adminPasswordResetOfflineProperty = IdentityUtil.getProperty(
                IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_OFFLINE);

        if (StringUtils.isNotEmpty(adminPasswordRecoveryWithLinkProperty)) {
            enableAdminPasswordResetWithRecoveryLink = adminPasswordRecoveryWithLinkProperty;
        }
        if (StringUtils.isNotEmpty(adminPasswordResetWithOTPProperty)) {
            enableAdminPasswordResetWithOTP = adminPasswordResetWithOTPProperty;
        }
        if (StringUtils.isNotEmpty(adminPasswordResetOfflineProperty)) {
            enableAdminPasswordResetOffline = adminPasswordResetOfflineProperty;
        }

        Map<String, String> defaultProperties = new HashMap<>();
        defaultProperties.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_RECOVERY_LINK,
                enableAdminPasswordResetWithRecoveryLink);
        defaultProperties.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_WITH_OTP,
                enableAdminPasswordResetWithOTP);
        defaultProperties.put(IdentityRecoveryConstants.ConnectorConfig.ENABLE_ADMIN_PASSWORD_RESET_OFFLINE,
                enableAdminPasswordResetOffline);

        Properties properties = new Properties();
        properties.putAll(defaultProperties);
        return properties;
    }

    @Override
    public Map<String, String> getDefaultPropertyValues(String[] propertyNames, String tenantDomain) throws IdentityGovernanceException {
        return null;
    }

    @Override
    public Map<String, String> getPropertyTypeMapping() {

        return Collections.emptyMap();
    }

}
