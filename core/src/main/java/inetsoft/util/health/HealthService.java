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
package inetsoft.util.health;

import inetsoft.util.ConfigurationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Lazy
public class HealthService {
   @Autowired
   public HealthService(CacheSwapHealthService cacheSwapHealthService,
                        DeadlockHealthService deadlockHealthService,
                        OutOfMemoryHealthService outOfMemoryHealthService,
                        ReportFailureHealthService reportFailureHealthService,
                        SchedulerHealthService schedulerHealthService,
                        SecurityProviderHealthService securityProviderHealthService,
                        FileSystemHealthService fileSystemHealthService)
   {
      this.cacheSwapHealthService = cacheSwapHealthService;
      this.deadlockHealthService = deadlockHealthService;
      this.outOfMemoryHealthService = outOfMemoryHealthService;
      this.reportFailureHealthService = reportFailureHealthService;
      this.schedulerHealthService = schedulerHealthService;
      this.securityProviderHealthService = securityProviderHealthService;
      this.fileSystemHealthService = fileSystemHealthService;
   }

   public static HealthService getInstance() {
      return ConfigurationContext.getContext().getSpringBean(HealthService.class);
   }

   public HealthStatus getStatus() {
      return new HealthStatus(
         cacheSwapHealthService.getStatus(),
         deadlockHealthService.getStatus(),
         outOfMemoryHealthService.getStatus(),
         reportFailureHealthService.getStatus(),
         schedulerHealthService.getStatus(),
         securityProviderHealthService.getStatus(),
         fileSystemHealthService.getStatus());
   }

   private final CacheSwapHealthService cacheSwapHealthService;
   private final DeadlockHealthService deadlockHealthService;
   private final OutOfMemoryHealthService outOfMemoryHealthService;
   private final ReportFailureHealthService reportFailureHealthService;
   private final SchedulerHealthService schedulerHealthService;
   private final SecurityProviderHealthService securityProviderHealthService;
   private final FileSystemHealthService fileSystemHealthService;
}
