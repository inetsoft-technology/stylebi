/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.logviewer;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.log.*;
import inetsoft.util.log.logback.LogbackUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LogSettingService {
   @Autowired
   public LogSettingService(SecurityEngine securityEngine, LogManager logManager)
   {
      this.securityEngine = securityEngine;
      this.logManager = logManager;
   }

   public LogSettingsModel getConfiguration() {
      try {
         // isFluentdEnabled(), not a bare comparison against the stored value: on a build that
         // cannot forward, the file appender is the one in use, so that is what this page must
         // report. Reporting the stored value instead would show the fluentd form while the
         // provider selector is hidden, leaving no way back to the file provider. The stored
         // property is not rewritten.
         boolean fluentd = LogbackUtil.isFluentdEnabled();
         String provider = fluentd ?
            LogbackUtil.FLUENTD_PROVIDER : LogbackUtil.FILE_PROVIDER;

         boolean outputToStd = "true".equals(SreeEnv.getProperty("log.output.stderr"));
         String str = SreeEnv.getProperty("log.detail.level");
         String detailLevel = LogManager.parseLevel(str).level();

         List<LogLevelDTO> logLevelDTOList = logManager.getContextLevels().stream()
            .sorted()
            .map(LogLevelDTO.builder()::from)
            .collect(Collectors.toList());

         LogSettingsModel.Builder builder = LogSettingsModel.builder()
            .provider(provider)
            .fileSettings(getFileSettings())
            .outputToStd(outputToStd)
            .detailLevel(detailLevel)
            .logLevels(logLevelDTOList);

         if(fluentd) {
            builder.fluentdSettings(getFluentdSettings());
         }

         return builder.build();
      }
      catch(Exception exc) {
         LOG.error("Failed to encode log configuration", exc);
         return null;
      }
   }

   private FileLogSettingsModel getFileSettings() {
      String file = logManager.getBaseLogFile(false);
      long maxLogSize = Long.parseLong(SreeEnv.getProperty("report.log.max"));
      int count = Integer.parseInt(SreeEnv.getProperty("report.log.count"));

      return FileLogSettingsModel.builder()
         .file(file)
         .maxLogSize(maxLogSize)
         .count(count)
         .build();
   }

   private FluentdLogSettingsModel getFluentdSettings() {
      int port = Integer.parseInt(SreeEnv.getProperty("log.fluentd.port", "24224"));
      String host = SreeEnv.getProperty("log.fluentd.host", "localhost");
      int connectTimeout =
         Integer.parseInt(SreeEnv.getProperty("log.fluentd.connectTimeout", "10000"));
      boolean securityEnabled = "true".equals(SreeEnv.getProperty("log.fluentd.securityEnabled"));
      String sharedKey = SreeEnv.getProperty("log.fluentd.security.sharedKey");
      boolean userAuthenticationEnabled =
         "true".equals(SreeEnv.getProperty("log.fluentd.security.userAuthenticationEnabled"));
      String username = SreeEnv.getProperty("log.fluentd.security.username");
      String password = SreeEnv.getProperty("log.fluentd.security.password");
      boolean tlsEnabled = "true".equals(SreeEnv.getProperty("log.fluentd.tlsEnabled"));
      String caCertificateFile = SreeEnv.getProperty("log.fluentd.tls.caCertificateFile");
      String logViewUrl = SreeEnv.getProperty("log.fluentd.logViewUrl");
      boolean canOrgAdminAccess = Boolean.parseBoolean(SreeEnv.getProperty("log.fluentd.orgAdminAccess"));

      if(sharedKey != null) {
         sharedKey = Tool.decryptPassword(sharedKey);
      }

      if(password != null) {
         password = Tool.decryptPassword(password);
      }

      return FluentdLogSettingsModel.builder()
         .port(port)
         .host(host)
         .connectTimeout(connectTimeout)
         .securityEnabled(securityEnabled)
         .sharedKey(sharedKey)
         .userAuthenticationEnabled(userAuthenticationEnabled)
         .username(username)
         .password(password)
         .tlsEnabled(tlsEnabled)
         .caCertificateFile(caCertificateFile)
         .logViewUrl(logViewUrl)
         .orgAdminAccess(canOrgAdminAccess)
         .build();
   }

   public void setConfiguration(LogSettingsModel model, Principal principal) {
      // reject before the ActionRecord and the try below: the catch there rewraps everything as
      // "Failed to save log configuration", which would hide the reason. The forwarder is loaded
      // reflectively from the enterprise module (see LogbackUtil.isFluentdEnabled), so without it
      // the setting would save, read back, and silently log to file -- the operator has to learn
      // this at the point of the change.
      if(LogbackUtil.FLUENTD_PROVIDER.equals(model.provider()) &&
         !LicenseManager.isEnterprise())
      {
         throw new MessageException(
            Catalog.getCatalog(principal).getString("em.common.log.fluentd.enterpriseOnly"),
            LogLevel.INFO, false);
      }

      ActionRecord actionRecord =
         SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT,
                               "Logging-Log Configuration", ActionRecord.OBJECT_TYPE_EMPROPERTY);
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();

      try {
         // A build that cannot forward does not manage the forwarding configuration from this
         // page, so it must not write any of it. getConfiguration() reports the file provider
         // there whatever log.provider says, and the view hides the fluentd group when the
         // provider is file, so model.fluentdSettings() arrives null even when all twelve
         // log.fluentd.* properties are populated -- setFluentdSettings(null) would then clear
         // the host, port, shared key, username, password and CA path on the next unrelated save
         // (a detail-level change, say), with no way to get them back. Skipping the log.provider
         // write for the same reason: the value reported to the page is the effective one, not
         // the stored one, so writing it back would silently discard the stored selection.
         if(LicenseManager.isEnterprise()) {
            SreeEnv.setProperty("log.provider", model.provider());
            setFluentdSettings(model.fluentdSettings());
         }

         setFileSettings(model.fileSettings());

         String stderr = String.valueOf(model.outputToStd());
         SreeEnv.setProperty("log.output.stderr", stderr);

         String detailLevel = model.detailLevel();
         SreeEnv.setProperty("log.detail.level", detailLevel);
         logManager.setLevel(LogManager.parseLevel(detailLevel));

         LogbackUtil.resetLog();

         SreeEnv.reloadLoggingFramework();

         List<LogLevelSetting> oldLogLevels =
            logManager.getContextLevels();
         List<LogLevelDTO> logLevels = model.logLevels();

         for(LogLevelSetting level : oldLogLevels) {
            boolean found = false;

            if(logLevels != null) {
               for(LogLevelDTO logLevel : logLevels) {
                  if(logLevel.context().equals(level.getContext().name()) &&
                     logLevel.name().equals(level.getName()) &&
                     Tool.equals(logLevel.orgName(), level.getOrgName()))
                  {
                     found = true;
                     break;
                  }
               }
            }

            if(!found) {
               String name = fixLogName(level.getName(), level.getOrgName(),
                                        level.getContext(), securityProvider);
               SreeEnv.setLogLevel(level.getContext(), name, LogLevel.OFF);
            }
         }

         if(logLevels != null) {
            for(LogLevelDTO logLevel : logLevels) {
               LogContext context = LogContext.valueOf(logLevel.context());
               String name = fixLogName(logLevel.name(), logLevel.orgName(),
                                        LogContext.valueOf(logLevel.context()), securityProvider);
               LogLevel level = LogManager.parseLevel(logLevel.level());
               SreeEnv.setLogLevel(context, name, level);
            }
         }

         SreeEnv.save();
      }
      catch(Exception e) {
         actionRecord.setActionStatus(
            ActionRecord.ACTION_STATUS_FAILURE);
         LOG.error("Failed to save log configuration", e);
         throw new MessageException("Failed to save log configuration, see log file for details.");
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   private String fixLogName(String name, String orgName, LogContext context, SecurityProvider provider) {
      if(!LicenseManager.isEnterprise()) {
         return name;
      }

      String orgId = null;

      if(!Tool.isEmptyString(orgName)) {
         orgId = provider.getOrgIdFromName(orgName);
      }

      if(context == LogContext.ORGANIZATION) {
         return provider.getOrgIdFromName(name);
      }

      if(Tool.isEmptyString(orgId) && context != LogContext.CATEGORY) {
         orgId = Organization.getDefaultOrganizationID();
      }

      return orgId == null ? name : Tool.buildString(name, "^", orgId);
   }

   private void setFileSettings(FileLogSettingsModel model) {
      if(model == null) {
         SreeEnv.setProperty("report.log.max", null);
         SreeEnv.setProperty("report.log.count", null);
      }
      else {
         long maxLogSize = model.maxLogSize();
         SreeEnv.setProperty("report.log.max", maxLogSize + "");
         String count = String.valueOf(model.count());
         SreeEnv.setProperty("report.log.count", count);
      }
   }

   private void setFluentdSettings(FluentdLogSettingsModel model) {
      if(model == null) {
         SreeEnv.setProperty("log.fluentd.port", null);
         SreeEnv.setProperty("log.fluentd.host", null);
         SreeEnv.setProperty("log.fluentd.connectTimeout", null);
         SreeEnv.setProperty("log.fluentd.securityEnabled", null);
         SreeEnv.setProperty("log.fluentd.security.sharedKey", null);
         SreeEnv.setProperty("log.fluentd.security.userAuthenticationEnabled", null);
         SreeEnv.setProperty("log.fluentd.security.username", null);
         SreeEnv.setProperty("log.fluentd.security.password", null);
         SreeEnv.setProperty("log.fluentd.tlsEnabled", null);
         SreeEnv.setProperty("log.fluentd.tls.caCertificateFile", null);
         SreeEnv.setProperty("log.fluentd.logViewUrl", null);
         SreeEnv.setProperty("log.fluentd.orgAdminAccess", null);
      }
      else {
         SreeEnv.setProperty("log.fluentd.port", Integer.toString(model.port()));
         SreeEnv.setProperty("log.fluentd.host", sanitizeProperty(model.host()));
         SreeEnv.setProperty(
            "log.fluentd.connectTimeout", Integer.toString(model.connectTimeout()));
         SreeEnv.setProperty(
            "log.fluentd.securityEnabled", Boolean.toString(model.securityEnabled()));
         SreeEnv.setProperty("log.fluentd.security.sharedKey", toPassword(model.sharedKey()));
         SreeEnv.setProperty(
            "log.fluentd.security.userAuthenticationEnabled",
            Boolean.toString(model.userAuthenticationEnabled()));
         SreeEnv.setProperty("log.fluentd.security.username", sanitizeProperty(model.username()));
         SreeEnv.setProperty("log.fluentd.security.password", toPassword(model.password()));
         SreeEnv.setProperty("log.fluentd.tlsEnabled", Boolean.toString(model.tlsEnabled()));
         SreeEnv.setProperty(
            "log.fluentd.tls.caCertificateFile", sanitizeProperty(model.caCertificateFile()));
         SreeEnv.setProperty("log.fluentd.logViewUrl", sanitizeProperty(model.logViewUrl()));
         SreeEnv.setProperty("log.fluentd.orgAdminAccess", Boolean.toString(model.orgAdminAccess()));
      }
   }

   private String sanitizeProperty(String value) {
      if(value != null) {
         if(value.trim().isEmpty()) {
            return null;
         }

         return value.trim();
      }

      return null;
   }

   private String toPassword(String value) {
      String sanitized = sanitizeProperty(value);

      if(sanitized != null) {
         return Tool.encryptPassword(sanitized);
      }

      return null;
   }

   private final SecurityEngine securityEngine;
   private final LogManager logManager;
   private static final Logger LOG = LoggerFactory.getLogger(LogSettingService.class);
}
