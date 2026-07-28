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

import inetsoft.report.internal.table.TableFormat;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.log.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Follow-up hooks that must run after a StyleBI server property is changed. Shared
 * by {@link PropertiesController} (EM UI path) and {@code AdminChangeService}
 * (admin-chat path) so both stay in lockstep instead of drifting.
 */
@Component
public class PropertyChangeSideEffects {
   @Autowired
   public PropertyChangeSideEffects(AssetRepository assetRepository, LogManager logManager,
                                    SecurityEngine securityEngine)
   {
      this.assetRepository = assetRepository;
      this.logManager = logManager;
      this.securityEngine = securityEngine;
   }

   /**
    * Replicates the follow-up hooks fired by {@code PropertiesController.editProperty}
    * after a property has been set and saved.
    */
   public void applyEditSideEffects(String propertyName) {
      if(Tool.equals(propertyName, "format.number.round") ||
         Tool.equals(propertyName, "format.percent.round"))
      {
         TableFormat.invalidateTableFormatCache();
      }

      if(Tool.equals(propertyName, "string.compare.casesensitive")) {
         Tool.invalidateCaseSensitive();
      }

      if(Tool.equals(propertyName, "security.exposedefaultorgtoall")) {
         assetRepository.fireExposeDefaultOrgPropertyChange();
      }
   }

   /**
    * Replicates the hook {@code PropertiesController.deleteProperty} fires BEFORE removing
    * the property: {@code removeLogLevel} reads the property's pre-removal value, so it must
    * run before {@code SreeEnv.remove()} -- never bundle this with {@link
    * #applyPostRemoveSideEffects}, whose call site must stay after {@code SreeEnv.save()} to
    * match the original ordering exactly.
    */
   public void applyPreRemoveSideEffects(String propertyName) {
      removeLogLevel(propertyName);
   }

   /**
    * Replicates the hook {@code PropertiesController.deleteProperty} fires AFTER removing
    * and saving the property.
    */
   public void applyPostRemoveSideEffects(String propertyName) {
      if(Tool.equals(propertyName, "security.exposedefaultorgtoall")) {
         assetRepository.fireExposeDefaultOrgPropertyChange();
      }
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

   private final AssetRepository assetRepository;
   private final LogManager logManager;
   private final SecurityEngine securityEngine;
}
