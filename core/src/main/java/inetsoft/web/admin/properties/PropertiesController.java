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
package inetsoft.web.admin.properties;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.log.*;
import inetsoft.web.admin.security.PropertyModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.viewsheet.AuditObjectName;
import inetsoft.web.viewsheet.Audited;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@RestController
public class PropertiesController {
   @Autowired
   public PropertiesController(AssetRepository assetRepository,
                               LogManager logManager, SecurityEngine securityEngine)
   {
      this.assetRepository = assetRepository;
      this.logManager = logManager;
      this.securityEngine = securityEngine;
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY,
      defaultOrg = true
   )
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/properties",
         actions = ResourceAction.ACCESS
      )
   )
   @DeleteMapping("/api/admin/properties/delete")
   public void deleteProperty(Principal user,
                              @RequestParam(value = "property", required = true) @AuditObjectName
                                 String property)
      throws IOException
   {
      removeLogLevel(property);
      SreeEnv.remove(property);
      SreeEnv.save();

      if(Tool.equals(property, "security.exposedefaultorgtoall")) {
         assetRepository.fireExposeDefaultOrgPropertyChange();
      }
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY,
      defaultOrg = true
   )
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/properties",
         actions = ResourceAction.ACCESS
      )
   )
   @PutMapping("/api/admin/properties/edit")
   public void editProperty(Principal user,
                            @RequestBody @AuditObjectName("name()") PropertyModel property)
      throws Exception
   {
      String propertyName = property.name();

      if(propertyName != null) {
         propertyName = propertyName.trim();
      }

      String value = property.value();

      if(value != null) {
         value = value.trim();
      }

      if("".equals(value)) {
         value = SreeEnv.getProperty(propertyName);
         value = value == null ? "" : value;
      }

      SreeEnv.setProperty(propertyName, value);
      SreeEnv.save();

      if(Tool.equals(propertyName, "format.number.round") || Tool.equals(propertyName, "format.percent.round")) {
         TableFormat.invalidateTableFormatCache();
      }

      if(Tool.equals(propertyName,"string.compare.casesensitive")) {
         Tool.invalidateCaseSensitive();
      }

      if(Tool.equals(propertyName, "security.exposedefaultorgtoall")) {
         assetRepository.fireExposeDefaultOrgPropertyChange();
      }
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/properties",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/admin/properties")
   public Properties getProperties() {
      Properties properties = SreeEnv.getProperties();

      if(!LicenseManager.isEnterprise()) {
         properties = removeUnuseProperties(properties);
      }

      return properties;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/properties",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/admin/properties/defaults")
   public Properties getDefaultProperties() {
      Properties properties = SreeEnv.getDefaultProperties();

      if(!LicenseManager.isEnterprise()) {
         properties = removeUnuseProperties(properties);
      }

      return properties;
   }

   /**
    * Returns a copy of the given properties with the settings that have no effect on a community
    * build omitted.
    *
    * The input must not be modified: getProperties() hands back the live internalProperties map
    * and getDefaultProperties() hands back a JVM-wide cached instance, so removing keys in place
    * would delete an admin's real configuration from the running server -- and once a removed key
    * is in PropertiesEngine.changedProps, saveToStorage() would persist that deletion.
    */
   private Properties removeUnuseProperties(Properties properties) {
      Properties filtered = new Properties();

      // stringPropertyNames() is the same view that serializes to the client (DefaultProperties
      // delegates keySet()/entrySet()/stringPropertyNames() to its main layer alike), so filtering
      // over it cannot list a key it fails to filter.
      for(String name : properties.stringPropertyNames()) {
         if(isEnterpriseOnlyProperty(name)) {
            continue;
         }

         String value = properties.getProperty(name);

         if(value != null) {
            filtered.setProperty(name, value);
         }
      }

      return filtered;
   }

   /**
    * Determines whether a property has no effect on a community build and should be hidden.
    */
   private boolean isEnterpriseOnlyProperty(String name) {
      // The Fluent Bit/Fluentd forwarder is enterprise-only: the appender and the reset hook are
      // both loaded reflectively from inetsoft.enterprise.log.fluentd (see LogbackUtil), so none
      // of the log.fluentd.* settings can take effect here. Match the whole family by prefix
      // rather than enumerating it -- an enumerated list drifts, and previously only 7 of the 12
      // keys were listed, leaving the shared key, password, username, CA certificate path and log
      // view URL visible without the host, port and flags that gate them. A key added to the
      // family later is now hidden automatically instead of landing on the wrong side.
      // Property names are stored lower-cased (PropertiesEngine.computePropertyNameCase), but the
      // comparison is case-insensitive so an un-normalized defaults spelling is caught too.
      if(name.regionMatches(true, 0, FLUENTD_PREFIX, 0, FLUENTD_PREFIX.length())) {
         return true;
      }

      return UNUSED_LOG_LEVELS.contains(name);
   }

   private void removeLogLevel(String property) {
      String value = SreeEnv.getProperty(property);

      if(Tool.isEmptyString(property) || !property.startsWith("log.") ||
         !property.contains(".level.") || value.equals("off"))
      {
         return;
      }

      String[] propertyParts = property.split("\\.");

      if(propertyParts.length < 4) {
         return;
      }

      List<LogLevelSetting> logLevels = logManager.getContextLevels();

      boolean found = logLevels.stream().anyMatch(logLevel -> {
         String name = logLevel.getName();

         if(logLevel.getOrgName() != null) {
            String orgId = securityEngine
               .getSecurityProvider()
               .getOrgIdFromName(logLevel.getOrgName());
            name = Tool.buildString(name, "^", orgId);
         }

         return property.equals("log." + logLevel.getContext().name() + ".level." + name);
      });

      if(found) {
         String[] parts = property.split("\\.");
         LogContext logContext = LogContext.valueOf(parts[1]);
         String name = parts[parts.length - 1];
         logManager.setContextLevel(logContext, name, null);
      }
   }

   private static final String FLUENTD_PREFIX = "log.fluentd.";
   private static final Set<String> UNUSED_LOG_LEVELS = Set.of(
      // NOTE: "intesoft" is a pre-existing typo in this key, corrected separately in bug-76047 /
      // PR #4662. Left as-is here so this change stays scoped to the log.fluentd.* family.
      "log.level.intesoft.storage.aws.com.amazonaws",
      "log.level.inetsoft.storage.aws.org.apache",
      "log.level.inetsoft.web.portal.controller.ControllerErrorHandler",
      "log.level.inetsoft_audit");

   private final AssetRepository assetRepository;
   private final LogManager logManager;
   private final SecurityEngine securityEngine;
}
