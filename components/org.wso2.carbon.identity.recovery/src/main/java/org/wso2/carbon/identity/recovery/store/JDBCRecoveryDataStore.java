/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.recovery.store;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.wso2.carbon.identity.application.common.model.User;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.governance.service.notification.NotificationChannels;
import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;
import org.wso2.carbon.identity.recovery.IdentityRecoveryException;
import org.wso2.carbon.identity.recovery.IdentityRecoveryServerException;
import org.wso2.carbon.identity.recovery.RecoveryScenarios;
import org.wso2.carbon.identity.recovery.RecoverySteps;
import org.wso2.carbon.identity.recovery.model.UserRecoveryData;
import org.wso2.carbon.identity.recovery.util.Utils;

import java.sql.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class JDBCRecoveryDataStore implements UserRecoveryDataStore {

    private static UserRecoveryDataStore jdbcRecoveryDataStore = new JDBCRecoveryDataStore();
    private static final Log log = LogFactory.getLog(JDBCRecoveryDataStore.class);

    private JDBCRecoveryDataStore() {

    }

    public static UserRecoveryDataStore getInstance() {
        return jdbcRecoveryDataStore;
    }


    @Override
    public void store(UserRecoveryData recoveryDataDO) throws IdentityRecoveryException {
        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement prepStmt = null;
        try {
            prepStmt = connection.prepareStatement(IdentityRecoveryConstants.SQLQueries.STORE_RECOVERY_DATA);
            prepStmt.setString(1, recoveryDataDO.getUser().getUserName());
            prepStmt.setString(2, recoveryDataDO.getUser().getUserStoreDomain().toUpperCase());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(recoveryDataDO.getUser().getTenantDomain()));
            prepStmt.setString(4, recoveryDataDO.getSecret());
            prepStmt.setString(5, String.valueOf(recoveryDataDO.getRecoveryScenario()));
            prepStmt.setString(6, String.valueOf(recoveryDataDO.getRecoveryStep()));
            prepStmt.setTimestamp(7, new Timestamp(new Date().getTime()));
            prepStmt.setString(8, recoveryDataDO.getRemainingSetIds());
            prepStmt.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_STORING_RECOVERY_DATA, null, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(prepStmt);
            IdentityDatabaseUtil.closeConnection(connection);
        }
    }

    @Override
    public UserRecoveryData load(User user, Enum recoveryScenario, Enum recoveryStep, String code) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        ResultSet resultSet = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection(false);
        String sql;
        try {
            if (IdentityUtil.isUserStoreCaseSensitive(user.getUserStoreDomain(), IdentityTenantUtil.getTenantId(user.getTenantDomain()))) {
                sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA;
            } else {
                sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA_CASE_INSENSITIVE;
            }

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, user.getUserName());
            prepStmt.setString(2, user.getUserStoreDomain().toUpperCase());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(user.getTenantDomain()));
            prepStmt.setString(4, code);
            prepStmt.setString(5, String.valueOf(recoveryScenario));
            prepStmt.setString(6, String.valueOf(recoveryStep));

            resultSet = prepStmt.executeQuery();

            if (resultSet.next()) {
                UserRecoveryData userRecoveryData = new UserRecoveryData(user, code, recoveryScenario, recoveryStep);
                if (StringUtils.isNotBlank(resultSet.getString("REMAINING_SETS"))) {
                    userRecoveryData.setRemainingSetIds(resultSet.getString("REMAINING_SETS"));
                }
                Timestamp timeCreated = resultSet.getTimestamp("TIME_CREATED");
                long createdTimeStamp = timeCreated.getTime();
                String remainingSets = resultSet.getString("REMAINING_SETS");
                if (isCodeExpired(user.getTenantDomain(), recoveryScenario, recoveryStep, createdTimeStamp,
                        remainingSets)) {
                    throw Utils.handleClientException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_EXPIRED_CODE,
                            code);
                }
                return userRecoveryData;
            }
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, resultSet, prepStmt);
        }
        throw Utils.handleClientException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_INVALID_CODE, code);
    }

    @Override
    public UserRecoveryData load(String code) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        ResultSet resultSet = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection(false);

        try {
            String sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA_FROM_CODE;

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, code);

            resultSet = prepStmt.executeQuery();

            if (resultSet.next()) {
                User user = new User();
                user.setUserName(resultSet.getString("USER_NAME"));
                user.setTenantDomain(IdentityTenantUtil.getTenantDomain(resultSet.getInt("TENANT_ID")));
                user.setUserStoreDomain(resultSet.getString("USER_DOMAIN"));

                Enum recoveryScenario = RecoveryScenarios.valueOf(resultSet.getString("SCENARIO"));
                Enum recoveryStep = RecoverySteps.valueOf(resultSet.getString("STEP"));

                UserRecoveryData userRecoveryData = new UserRecoveryData(user, code, recoveryScenario, recoveryStep);

                if (StringUtils.isNotBlank(resultSet.getString("REMAINING_SETS"))) {
                    userRecoveryData.setRemainingSetIds(resultSet.getString("REMAINING_SETS"));
                }
                Timestamp timeCreated = resultSet.getTimestamp("TIME_CREATED");
                long createdTimeStamp = timeCreated.getTime();
                if (isCodeExpired(user.getTenantDomain(), userRecoveryData.getRecoveryScenario(),
                        userRecoveryData.getRecoveryStep(), createdTimeStamp, userRecoveryData.getRemainingSetIds())) {
                    throw Utils.handleClientException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_EXPIRED_CODE,
                            code);
                }
                return userRecoveryData;
            }
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, resultSet, prepStmt);
        }
        throw Utils.handleClientException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_INVALID_CODE, code);

    }

    @Override
    public void invalidate(String code) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection();
        try {
            String sql = IdentityRecoveryConstants.SQLQueries.INVALIDATE_CODE;

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, code);
            prepStmt.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(prepStmt);
            IdentityDatabaseUtil.closeConnection(connection);
        }
    }


    @Override
    public UserRecoveryData load(User user) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        ResultSet resultSet = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection(false);

        try {
            String sql;
            if (IdentityUtil.isUserStoreCaseSensitive(user.getUserStoreDomain(), IdentityTenantUtil.getTenantId(user.getTenantDomain()))) {
                sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA_OF_USER;
            } else {
                sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA_OF_USER_CASE_INSENSITIVE;
            }

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, user.getUserName());
            prepStmt.setString(2, user.getUserStoreDomain().toUpperCase());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(user.getTenantDomain()));

            resultSet = prepStmt.executeQuery();

            if (resultSet.next()) {
                Timestamp timeCreated = resultSet.getTimestamp("TIME_CREATED");
                RecoveryScenarios scenario = RecoveryScenarios.valueOf(resultSet.getString("SCENARIO"));
                RecoverySteps step = RecoverySteps.valueOf(resultSet.getString("STEP"));
                String code = resultSet.getString("CODE");
                String remainingSets = resultSet.getString("REMAINING_SETS");
                if (isCodeExpired(user.getTenantDomain(), scenario, step, timeCreated.getTime(), remainingSets)) {
                    throw Utils.handleClientException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_EXPIRED_CODE,
                            code);
                }

                UserRecoveryData userRecoveryData =
                        new UserRecoveryData(user, code, scenario, step);
                if (StringUtils.isNotBlank(remainingSets)) {
                    userRecoveryData.setRemainingSetIds(resultSet.getString("REMAINING_SETS"));
                }
                return userRecoveryData;
            }
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, resultSet, prepStmt);
        }
        return null;
    }

    @Override
    public UserRecoveryData loadWithoutCodeExpiryValidation(User user) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        ResultSet resultSet = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection(false);

        try {
            String sql;
            if (IdentityUtil.isUserStoreCaseSensitive(user.getUserStoreDomain(), IdentityTenantUtil.getTenantId(user.getTenantDomain()))) {
                sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA_OF_USER;
            } else {
                sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA_OF_USER_CASE_INSENSITIVE;
            }

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, user.getUserName());
            prepStmt.setString(2, user.getUserStoreDomain().toUpperCase());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(user.getTenantDomain()));

            resultSet = prepStmt.executeQuery();

            if (resultSet.next()) {
                RecoveryScenarios scenario = RecoveryScenarios.valueOf(resultSet.getString("SCENARIO"));
                RecoverySteps step = RecoverySteps.valueOf(resultSet.getString("STEP"));
                String code = resultSet.getString("CODE");

                UserRecoveryData userRecoveryData =
                        new UserRecoveryData(user, code, scenario, step);
                if (StringUtils.isNotBlank(resultSet.getString("REMAINING_SETS"))) {
                    userRecoveryData.setRemainingSetIds(resultSet.getString("REMAINING_SETS"));
                }
                return userRecoveryData;
            }
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, resultSet, prepStmt);
        }
        return null;
    }


    @Override
    public void invalidate(User user) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection();
        try {
            String sql;
            if (IdentityUtil.isUserStoreCaseSensitive(user.getUserStoreDomain(), IdentityTenantUtil.getTenantId(user.getTenantDomain()))) {
                sql = IdentityRecoveryConstants.SQLQueries.INVALIDATE_USER_CODES;
            } else {
                sql = IdentityRecoveryConstants.SQLQueries.INVALIDATE_USER_CODES_CASE_INSENSITIVE;
            }

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, user.getUserName());
            prepStmt.setString(2, user.getUserStoreDomain());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(user.getTenantDomain()));
            prepStmt.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(prepStmt);
            IdentityDatabaseUtil.closeConnection(connection);
        }
    }

    /**
     * Checks whether the code has expired or not.
     *
     * @param tenantDomain     Tenant domain
     * @param recoveryScenario Recovery scenario
     * @param recoveryStep     Recovery step
     * @param createdTimestamp Time stamp
     * @param recoveryData     Additional data for validate the code
     * @return Whether the code has expired or not
     * @throws IdentityRecoveryServerException Error while reading the configs
     */
    private boolean isCodeExpired(String tenantDomain, Enum recoveryScenario, Enum recoveryStep, long createdTimestamp,
            String recoveryData) throws IdentityRecoveryServerException {

        int notificationExpiryTimeInMinutes;
        // Self sign up scenario has two sub scenarios as verification via email or verification via SMS.
        if (RecoveryScenarios.SELF_SIGN_UP.equals(recoveryScenario) && RecoverySteps.CONFIRM_SIGN_UP
                .equals(recoveryStep)) {
            // If the verification channel is email, use verification link timeout configs to validate.
            if (NotificationChannels.EMAIL_CHANNEL.getChannelType().equalsIgnoreCase(recoveryData)) {
                if (log.isDebugEnabled()) {
                    String message = String.format("Verification channel: %s was detected for recovery scenario: %s "
                            + "and recovery step: %s", recoveryData, recoveryScenario, recoveryStep);
                    log.debug(message);
                }
                notificationExpiryTimeInMinutes = Integer.parseInt(Utils.getRecoveryConfigs(
                        IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_VERIFICATION_CODE_EXPIRY_TIME,
                        tenantDomain));
            } else if (NotificationChannels.SMS_CHANNEL.getChannelType().equals(recoveryData)) {
                // If the verification channel is SMS, use SMS OTP timeout configs to validate.
                if (log.isDebugEnabled()) {
                    String message = String.format("Verification channel: %s was detected for recovery scenario: %s "
                            + "and recovery step: %s", recoveryData, recoveryScenario, recoveryStep);
                    log.debug(message);
                }
                notificationExpiryTimeInMinutes = Integer
                        .parseInt(Utils.getRecoveryConfigs(IdentityRecoveryConstants.ConnectorConfig.
                                SELF_REGISTRATION_SMSOTP_VERIFICATION_CODE_EXPIRY_TIME, tenantDomain));
            } else {
                // If the verification channel is not specified, verification will takes place according to default
                // verification link timeout configs.
                if (log.isDebugEnabled()) {
                    String message = String.format("No verification channel for recovery scenario: %s and recovery " +
                                    "step: %s .Therefore, using verification link default timeout configs",
                            recoveryScenario, recoveryStep);
                    log.debug(message);
                }
                notificationExpiryTimeInMinutes = Integer.parseInt(Utils.getRecoveryConfigs(
                        IdentityRecoveryConstants.ConnectorConfig.SELF_REGISTRATION_VERIFICATION_CODE_EXPIRY_TIME,
                        tenantDomain));
            }
        } else if (RecoveryScenarios.ASK_PASSWORD.equals(recoveryScenario)) {
            notificationExpiryTimeInMinutes = Integer.parseInt(Utils.getRecoveryConfigs(IdentityRecoveryConstants
                    .ConnectorConfig.ASK_PASSWORD_EXPIRY_TIME, tenantDomain));
        } else if (RecoveryScenarios.USERNAME_RECOVERY.equals(recoveryScenario)) {

            // Validate the recovery code given at username recovery.
            notificationExpiryTimeInMinutes = getRecoveryCodeExpiryTime();
        } else if (RecoveryScenarios.NOTIFICATION_BASED_PW_RECOVERY.equals(recoveryScenario)) {

            if (RecoverySteps.RESEND_CONFIRMATION_CODE.toString().equals(recoveryStep.toString())) {
                notificationExpiryTimeInMinutes = getResendCodeExpiryTime();
            } else if (RecoverySteps.SEND_RECOVERY_INFORMATION.toString().equals(recoveryStep.toString())) {

                // Validate the recovery code password recovery.
                notificationExpiryTimeInMinutes = getRecoveryCodeExpiryTime();
            } else if (NotificationChannels.SMS_CHANNEL.getChannelType().equals(recoveryData)) {

                // Validate the SMS OTP confirmation code.
                notificationExpiryTimeInMinutes = Integer.parseInt(Utils.getRecoveryConfigs(
                        IdentityRecoveryConstants.ConnectorConfig.PASSWORD_RECOVERY_SMS_OTP_EXPIRY_TIME, tenantDomain));
            } else {
                notificationExpiryTimeInMinutes = Integer.parseInt(
                        Utils.getRecoveryConfigs(IdentityRecoveryConstants.ConnectorConfig.EXPIRY_TIME, tenantDomain));
            }
        } else {
            notificationExpiryTimeInMinutes = Integer.parseInt(Utils.getRecoveryConfigs(IdentityRecoveryConstants
                    .ConnectorConfig.EXPIRY_TIME, tenantDomain));
        }
        if (notificationExpiryTimeInMinutes < 0) {
            // Make the code valid infinitely in case of negative value
            notificationExpiryTimeInMinutes = Integer.MAX_VALUE;
        }
        long expiryTime = createdTimestamp + TimeUnit.MINUTES.toMillis(notificationExpiryTimeInMinutes);
        return System.currentTimeMillis() > expiryTime;
    }

    /**
     * Get the expiry time of the recovery code given at username recovery and password recovery init.
     *
     * @return Expiry time of the recovery code (In minutes)
     */
    private int getRecoveryCodeExpiryTime() {

        String expiryTime = IdentityUtil
                .getProperty(IdentityRecoveryConstants.ConnectorConfig.RECOVERY_CODE_EXPIRY_TIME);
        if (StringUtils.isEmpty(expiryTime)) {
            return IdentityRecoveryConstants.RECOVERY_CODE_DEFAULT_EXPIRY_TIME;
        }
        try {
            return Integer.parseInt(expiryTime);
        } catch (NumberFormatException e) {
            if (log.isDebugEnabled()) {
                String message = String
                        .format("User recovery code expired. Therefore setting DEFAULT expiry time : %s minutes",
                                IdentityRecoveryConstants.RECOVERY_CODE_DEFAULT_EXPIRY_TIME);
                log.debug(message);
            }
            return IdentityRecoveryConstants.RECOVERY_CODE_DEFAULT_EXPIRY_TIME;
        }
    }

    /**
     * Get the expiry time of the recovery code given at username recovery and password recovery init.
     *
     * @return Expiry time of the recovery code (In minutes)
     */
    private int getResendCodeExpiryTime() {

        String expiryTime = IdentityUtil
                .getProperty(IdentityRecoveryConstants.ConnectorConfig.RESEND_CODE_EXPIRY_TIME);
        if (StringUtils.isEmpty(expiryTime)) {
            return IdentityRecoveryConstants.RESEND_CODE_DEFAULT_EXPIRY_TIME;
        }
        try {
            return Integer.parseInt(expiryTime);
        } catch (NumberFormatException e) {
            if (log.isDebugEnabled()) {
                String message = String
                        .format("User recovery code expired. Therefore setting DEFAULT expiry time : %s minutes",
                                IdentityRecoveryConstants.RESEND_CODE_DEFAULT_EXPIRY_TIME);
                log.debug(message);
            }
            return IdentityRecoveryConstants.RESEND_CODE_DEFAULT_EXPIRY_TIME;
        }
    }
}
