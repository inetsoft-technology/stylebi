/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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

     if(properties.get("license.key") != null && !LicenseManager.getInstance().isEnterprise()) {
         properties.remove("license.key");
      }

      return properties;
   }

   @GetMapping("/api/admin/properties/defaults")
   public Properties getDefaultProperties() {
      return SreeEnv.getDefaultProperties();
   }
}
