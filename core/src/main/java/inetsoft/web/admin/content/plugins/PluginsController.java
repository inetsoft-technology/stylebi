/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.plugins;

import inetsoft.web.admin.content.plugins.model.*;
import inetsoft.web.security.DeniedMultiTenancyOrgUser;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@DeniedMultiTenancyOrgUser
public class PluginsController {
   @Autowired
   public PluginsController(PluginsService pluginsService) {
      this.pluginsService = pluginsService;
   }

   /**
    * Get available plugins
    *
    * @return PluginsModel List of all available plugins
    */
   @GetMapping("/api/data/plugins")
   public PluginsModel getPluginsModel(Principal principal) throws Exception {
      return this.pluginsService.getModel(principal);
   }

   /**
    * Installs plugins uploaded in request
    *
    * @param request request containing file upload
    */
   @PostMapping("/api/em/settings/content/plugins")
   public PluginsModel installPluginFiles(@RequestBody UploadDriverFileModel request,
                                          Principal principal) throws Exception
   {
      pluginsService.installPlugins(request.files(), principal);
      return getPluginsModel(principal);
   }

   @PostMapping("/api/em/settings/content/plugins/delete")
   public PluginsModel deletePluginFiles(@RequestBody PluginsModel model, Principal principal)
      throws Exception
   {
      this.pluginsService.uninstallPlugins(model, principal);
      return getPluginsModel(principal);
   }

   @GetMapping("/api/em/settings/content/plugins/drivers/scan/{id}")
   public DriverList scanDrivers(@PathVariable("id") String id, Principal principal)
      throws Exception
   {
      return DriverList.builder()
         .drivers(pluginsService.scanDrivers(id, principal))
         .build();
   }

   @PostMapping("/api/em/settings/content/plugins/drivers")
   public void createDriverPlugin(@RequestBody CreateDriverPluginRequest request,
                                  Principal principal) throws Exception
   {
      pluginsService.createDriverPlugin(request, principal);
   }

   private final PluginsService pluginsService;
}
