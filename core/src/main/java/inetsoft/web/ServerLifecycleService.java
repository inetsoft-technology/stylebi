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
package inetsoft.web;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
import inetsoft.web.service.LicenseService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
public class ServerLifecycleService {
   /**
    * Creates a new instance of <tt>ServerLifecycleService</tt>.
    */
   @Autowired
   public ServerLifecycleService(LicenseService licenseService) {
      this.licenseService = licenseService;
   }

   @PostConstruct
   public void serverStartUpTasks() {
      licenseService.checkLicense();

      if(System.getProperty("java.rmi.server.hostname") == null) {
         System.setProperty("java.rmi.server.hostname", Tool.getRmiIP());
      }

      System.setProperty("jgroups.bind_addr", Tool.getRmiIP());

      SUtil.startServerNode();
      TimedQueue.add(new ClearOldCacheFilesRunnable());
   }

   @PostConstruct
   public void clearCacheFiles() {
      if("true".equals(SreeEnv.getProperty("replet.cache.clean"))) {
         FileSystemService.getInstance().clearCacheFiles("viewer");
      }
   }

   private final LicenseService licenseService;
}
