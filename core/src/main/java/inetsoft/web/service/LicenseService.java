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
package inetsoft.web.service;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
public class LicenseService {
   @PostConstruct
   public void initProperties() {
      // ensure that SreeEnv is initialized prior to checking the license.
      SreeEnv.init();
   }

   @Scheduled(fixedRate = 600000L)
   public void checkLicense() {
      int licensedCPUCount = LicenseManager.getInstance().getLicensedCpuCount();

      if(licensedCPUCount > 0) {
         int realCPUCount = Tool.getAvailableCPUCores();
         cpuUnlicensedMSG = realCPUCount > licensedCPUCount ?
            ("You must have a CPU license for each CPU on this machine." +
               "The server machine has " + realCPUCount + " CPU(s) and only " +
               licensedCPUCount + " are licensed.") : null;

         cpuUnlicensed = realCPUCount > licensedCPUCount;
      }
   }

   public boolean isCpuUnlicensed() {
      return cpuUnlicensed;
   }

   public String getCpuUnlicensedMSG() {
      return cpuUnlicensedMSG;
   }

   private boolean cpuUnlicensed = false;
   private String cpuUnlicensedMSG = null;
}