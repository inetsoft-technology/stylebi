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
package inetsoft.web.admin.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;

@Component
@ManagedResource
public class ServerManagerMBean {
   @Autowired()
   public ServerManagerMBean(ServerService serverService) {
      this.serverService = serverService;
   }

   /**
    * Get the count of license.
    */
   @ManagedAttribute
   public int getLicenseCount() {
      return serverService.getLicenseCount();
   }

   /**
    * Get array of LicenseInfo.
    */
   @ManagedAttribute
   public LicenseInfo[] getLicenseInfos() {
      return serverService.getLicenseInfos().toArray(new LicenseInfo[0]);
   }

   /**
    * Administrator add a license key to the server.
    */
   @ManagedOperation
   public void addLicense(String license) throws Exception {
      serverService.addLicense(license);
   }

   /**
    * Administrator remove a license.
    */
   @ManagedOperation
   public void removeLicense(String license) throws Exception {
      serverService.removeLicense(license);
   }

   private final ServerService serverService;
}
