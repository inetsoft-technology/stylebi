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
package inetsoft.web.admin.cache;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.util.Tool;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class CacheManagerController {
   @Autowired
   public CacheManagerController(CacheService cacheService) {
      this.cacheService = cacheService;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/dataCacheSize")
   public CacheProperty getDataCacheSize(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("dataCacheSize")
         .longValue(cacheService.getDataCacheSize())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/dataCacheSize")
   public void setDataCacheSize(@RequestBody CacheProperty property,
                                @PermissionUser Principal principal) throws Exception
   {
      cacheService.setDataCacheSize(Tool.defaultIfNull(property.longValue(), 0L));
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/dataCacheTimeout")
   public CacheProperty getDataCacheTimeout(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("dataCacheTimeout")
         .longValue(cacheService.getDataCacheTimeout())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/dataCacheTimeout")
   public void setDataCacheTimeout(@RequestBody CacheProperty property,
                                   @PermissionUser Principal principal) throws Exception
   {
      cacheService.setDataCacheTimeout(Tool.defaultIfNull(property.longValue(),0L));
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/maxReportsPerSession")
   public CacheProperty getMaxReportsPerSession(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("maxReportsPerSession")
         .intValue(cacheService.getMaxReportsPerSession())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/maxReportsPerSession")
   public void setMaxReportsPerSession(@RequestBody CacheProperty property,
                                       @PermissionUser Principal principal) throws Exception
   {
      cacheService.setMaxReportsPerSession(Tool.defaultIfNull(property.intValue(), 0));
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/reportCacheFileSize")
   public CacheProperty getReportCacheFileSize(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("reportCacheFileSize")
         .longValue(cacheService.getReportCacheFileSize())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/reportCacheFileSize")
   public void setReportCacheFileSize(@RequestBody CacheProperty property,
                                      @PermissionUser Principal principal) throws Exception
   {
      cacheService.setReportCacheFileSize(Tool.defaultIfNull(property.longValue(), 0L));
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/dataCacheFileSize")
   public CacheProperty getDataCacheFileSize(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("dataCacheFileSize")
         .longValue(cacheService.getDataCacheFileSize())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/dataCacheFileSize")
   public void setDataCacheFileSize(@RequestBody CacheProperty property,
                                    @PermissionUser Principal principal) throws Exception
   {
      cacheService.setDataCacheFileSize(Tool.defaultIfNull(property.longValue(), 0L));
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/worksetSize")
   public CacheProperty getWorksetSize(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("worksetSize")
         .intValue(cacheService.getWorksetSize())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/worksetSize")
   public void setWorksetSize(@RequestBody CacheProperty property,
                              @PermissionUser Principal principal) throws Exception
   {
      cacheService.setWorksetSize(Tool.defaultIfNull(property.intValue(), 0));
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/dataSetCachingEnabled")
   public CacheProperty isDataSetCachingEnabled(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("dataSetCachingEnabled")
         .booleanValue(cacheService.isDataSetCachingEnabled())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/dataSetCachingEnabled")
   public void setDataSetCachingEnabled(@RequestBody CacheProperty property,
                                        @PermissionUser Principal principal) throws Exception
   {
      cacheService.setDataSetCachingEnabled(
         Tool.defaultIfNull(property.booleanValue(), true));
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/cache/properties/securityCachingEnabled")
   public CacheProperty isSecurityCachingEnabled(@PermissionUser Principal principal)
   {
      return CacheProperty.builder()
         .name("securityCachingEnabled")
         .booleanValue(cacheService.isSecurityCachingEnabled())
         .build();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/general",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/cache/properties/securityCachingEnabled")
   public void setSecurityCachingEnabled(@RequestBody CacheProperty property,
                                         @PermissionUser Principal principal) throws Exception
   {
      cacheService.setSecurityCachingEnabled(
         Tool.defaultIfNull(property.booleanValue(), true));
   }

   private final CacheService cacheService;
}
