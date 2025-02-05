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
import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.security.DeniedMultiTenancyOrgUser;
import inetsoft.web.viewsheet.AuditObjectName;
import inetsoft.web.viewsheet.Audited;

import java.io.IOException;
import java.security.Principal;
import java.util.Properties;

import org.springframework.web.bind.annotation.*;

@RestController
@DeniedMultiTenancyOrgUser
public class PropertiesController {
   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   @DeleteMapping("/api/admin/properties/delete")
   public void deleteProperty(Principal user,
                              @RequestParam(value = "property", required = true) @AuditObjectName
                                 String property)
      throws IOException
   {
      SreeEnv.remove(property);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   @PutMapping("/api/admin/properties/edit")
   public void editProperty(Principal user,
                            @RequestParam(value = "property", required = true) @AuditObjectName
                               String property,
                            @RequestParam(value = "value", required = true) String value)
      throws Exception
   {
      property = property.trim();
      value = value.trim();

      if("".equals(value)) {
         value = SreeEnv.getProperty(property);
         value = value == null ? "" : value;
      }

      SreeEnv.setProperty(property, value);
      SreeEnv.save();
   }

   @GetMapping("/api/admin/properties")
   public Properties getProperties() {
      Properties properties = SreeEnv.getProperties();

      if(!LicenseManager.getInstance().isEnterprise()) {
         removeUnuseProperties(properties);
      }

      return properties;
   }

   @GetMapping("/api/admin/properties/defaults")
   public Properties getDefaultProperties() {
      Properties properties = SreeEnv.getDefaultProperties();

      if(!LicenseManager.getInstance().isEnterprise()) {
         removeUnuseProperties(properties);
      }

      return properties;
   }

   private void removeUnuseProperties(Properties properties) {
      properties.remove("log.fluentd.host");
      properties.remove("log.fluentd.orgadminaccess");
      properties.remove("log.fluentd.port");
      properties.remove("log.fluentd.security.userauthenticationenabled");
      properties.remove("log.fluentd.connecttimeout");
      properties.remove("log.fluentd.securityenabled");
      properties.remove("log.fluentd.tlsenabled");
      properties.remove("log.level.intesoft.storage.aws.com.amazonaws");
      properties.remove("log.level.inetsoft.storage.aws.org.apache");
      properties.remove("log.level.inetsoft.web.portal.controller.ControllerErrorHandler");
      properties.remove("log.level.inetsoft_audit");
   }
}
