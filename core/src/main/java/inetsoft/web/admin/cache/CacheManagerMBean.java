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
package inetsoft.web.admin.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource
public class CacheManagerMBean {
   @Autowired
   public CacheManagerMBean(CacheService cacheService) {
      this.cacheService = cacheService;
   }

   /**
    * Get Data Cache Size of the manager.
    * @return the size of Data Cache Size.
    */
   @ManagedAttribute
   public long getDataCacheSize() {
      return cacheService.getDataCacheSize();
   }

   /**
    * Set Data Cache Size to the manager.
    * @param size the Data Cache Size.
    */
   @ManagedAttribute
   public void setDataCacheSize(long size) throws Exception {
      cacheService.setDataCacheSize(size);
   }

   /**
    * Get Data Cache Timeout of the manager.
    * @return the timeout of Data Cache Timeout.
    */
   @ManagedAttribute
   public long getDataCacheTimeout() {
      return cacheService.getDataCacheTimeout();
   }

   /**
    * Set Data Cache Timeout to the manager.
    * @param timeout the Data Cache Timeout.
    */
   @ManagedAttribute
   public void setDataCacheTimeout(long timeout) throws Exception {
      cacheService.setDataCacheTimeout(timeout);
   }

   /**
    * Get Max Reports Per Session of the manager.
    * @return the max value of Max Reports Per Session.
    */
   @ManagedAttribute
   public int getMaxReportsPerSession() {
      return cacheService.getMaxReportsPerSession();
   }

   /**
    * Set Max Reports Per Session to the manager.
    * @param max the Max Reports Per Session.
    */
   @ManagedAttribute
   public void setMaxReportsPerSession(int max) throws Exception {
      cacheService.setMaxReportsPerSession(max);
   }

   /**
    * Get Report Cache File Size of the manager.
    * @return the cacheSize value of Report Cache File Size.
    */
   @ManagedAttribute
   public long getReportCacheFileSize() {
      return cacheService.getReportCacheFileSize();
   }

   /**
    * Set Report Cache File Size to the manager.
    * @param cacheSize the Report Cache File Size.
    */
   @ManagedAttribute
   public void setReportCacheFileSize(long cacheSize) throws Exception {
      cacheService.setReportCacheFileSize(cacheSize);
   }

   /**
    * Get Data Cache File Size of the manager.
    * @return the dataCacheSize value of Data Cache File Size.
    */
   @ManagedAttribute
   public long getDataCacheFileSize() {
      return cacheService.getDataCacheFileSize();
   }

   /**
    * Set Data Cache File Size to the manager.
    * @param dataCacheSize the Data Cache File Size.
    */
   @ManagedAttribute
   public void setDataCacheFileSize(long dataCacheSize) throws Exception {
      cacheService.setDataCacheFileSize(dataCacheSize);
   }

   /**
    * Get Workset Size of the manager.
    * @return the worksetSize value of Workset Size.
    */
   @ManagedAttribute
   public int getWorksetSize() {
      return cacheService.getWorksetSize();
   }

   /**
    * Set Workset Size to the manager.
    * @param worksetSize the Workset Size.
    */
   @ManagedAttribute
   public void setWorksetSize(int worksetSize) throws Exception {
      cacheService.setWorksetSize(worksetSize);
   }

   /**
    * Check if is Data Set Caching Enabled.
    * @return <tt>true</tt> if not enabled.
    */
   @ManagedAttribute
   public boolean isDataSetCachingEnabled() {
      return cacheService.isDataSetCachingEnabled();
   }

   /**
    * Set the enabled status of this Data Set Caching.
    * @param enabled false to enable the Data Set Caching.
    */
   @ManagedAttribute
   public void setDataSetCachingEnabled(boolean enabled) throws Exception{
      cacheService.setDataSetCachingEnabled(enabled);
   }

   /**
    * Check if is Security Caching Enabled.
    * @return <tt>true</tt> if not enabled.
    */
   @ManagedAttribute
   public boolean isSecurityCachingEnabled() {
      return cacheService.isSecurityCachingEnabled();
   }

   /**
    * Set the enabled status of this Security Caching.
    * @param enabled false to enable the Security Caching.
    */
   @ManagedAttribute
   public void setSecurityCachingEnabled(boolean enabled) throws Exception{
      cacheService.setSecurityCachingEnabled(enabled);
   }
   
   private final CacheService cacheService;
}
