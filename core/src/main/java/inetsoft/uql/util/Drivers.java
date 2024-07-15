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
package inetsoft.uql.util;

import inetsoft.uql.jdbc.*;
import inetsoft.uql.tabular.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Drivers {
   public static Drivers getInstance() {
      return SingletonManager.getInstance(Drivers.class);
   }

   /**
    * Get the class loader for a specific JDBC driver.
    */
   public ClassLoader getDriverClassLoader(String classname, String url) {
      return getProvider().getDriverClassLoader(classname, url);
   }

   /**
    * Get the executor for data source.
    */
   public SQLExecutor getSQLExecutor(String classname, String url) {
      return getProvider().getSQLExecutor(classname, url);
   }

   /**
    * Get the driver service for the data source.
    */
   DriverService getDriverService(String classname, String url) {
      for(DriverService service : getDriverServices()) {
         if(service.matches(classname, url)) {
            return service;
         }
      }

      return null;
   }

   private void initDriverServices() {
      driverServicesLock.readLock().lock();

      try {
         if(driverServices != null) {
            return;
         }
      }
      finally {
         driverServicesLock.readLock().unlock();
      }

      driverServicesLock.writeLock().lock();

      try {
         if(driverServices == null) {
            driverServices = new HashMap<>();

            for(Plugin plugin : Plugins.getInstance().getPlugins()) {
               List<DriverService> services = plugin.getServices(DriverService.class);

               if(!services.isEmpty()) {
                  driverServices.put(plugin.getId(), services);
               }
            }
         }
      }
      finally {
         driverServicesLock.writeLock().unlock();
      }
   }

   List<DriverService> getDriverServices() {
      initDriverServices();
      driverServicesLock.readLock().lock();

      try {
         return listDriverServices();
      }
      finally {
         driverServicesLock.readLock().unlock();
      }
   }

   private List<DriverService> listDriverServices() {
      return driverServices.values().stream()
         .flatMap(List::stream)
         .sorted(this::sortDriverServices)
         .collect(Collectors.toList());
   }

   private int sortDriverServices(DriverService a, DriverService b) {
      if(a.getClass().getSimpleName().equals("SparkJDBCDriverService")) {
         return 1;
      }

      if(b.getClass().getSimpleName().equals("SparkJDBCDriverService")) {
         return -1;
      }

      return a.getClass().getSimpleName().compareTo(b.getClass().getSimpleName());
   }

   public void pluginAdded(String pluginId) {
      initDriverServices();
      driverServicesLock.writeLock().lock();

      try {
         List<DriverService> services =
            Plugins.getInstance().getServices(DriverService.class, pluginId);

         if(!services.isEmpty()) {
            driverServices.put(pluginId, services);
         }
      }
      finally {
         driverServicesLock.writeLock().unlock();
      }
   }

   public void pluginRemoved(String pluginId) {
      Objects.requireNonNull(pluginId);
      initDriverServices();
      driverServicesLock.writeLock().lock();

      try {
         List<DriverService> services = driverServices.remove(pluginId);

         if(services != null) {
            Set<String> drivers = services.stream()
               .filter(d -> d.getDrivers() != null)
               .flatMap(d -> d.getDrivers().stream())
               .collect(Collectors.toSet());

            ConnectionPoolFactory factory = JDBCHandler.getConnectionPoolFactory();
            factory.closeConnectionPools(
               ds -> drivers.stream().anyMatch(d -> factory.isDriverUsed(ds, d)));
            Set<Driver> deregistered = DriverManager.drivers()
               .filter(d -> drivers.contains(d.getClass().getName()))
               .collect(Collectors.toSet());

            for(Driver driver : deregistered) {
               try {
                  DriverManager.deregisterDriver(driver);
               }
               catch(Exception e) {
                  LOG.warn("Failed to deregister driver {}", driver.getClass().getName(), e);
               }
            }
         }
      }
      finally {
         driverServicesLock.writeLock().unlock();
      }
   }

   /**
    * Get the executor for data source.
    */
   public TabularExecutor getTabularExecutor(TabularDataSource<?> xds) {
      TabularDriverService service = getDriverService(xds);

      if(service != null) {
         return service.getTabularExecutor();
      }

      return null;
   }

   /**
    * Get the driver service for the data source.
    */
   TabularDriverService getDriverService(TabularDataSource<?> xds) {
      if(tabularServices == null) {
         tabularServices = Plugins.getInstance().getServices(
            inetsoft.uql.tabular.TabularDriverService.class, null);
      }

      for(inetsoft.uql.tabular.TabularDriverService service : tabularServices) {
         if(service.matches(xds)) {
            return service;
         }
      }

      return null;
   }

   /**
    * Loads a class from the driver class loader.
    *
    * @param className the name of the class.
    *
    * @return the class.
    *
    * @throws ClassNotFoundException if the class could not be found.
    */
   public Class<?> getDriverClass(String className) throws ClassNotFoundException {
      return getProvider().getDriverClass(className);
   }

   /**
    * Gets the URL of a resource from the driver class loader.
    *
    * @param name the name of the resource.
    * @return the resource URL or <tt>null</tt> if not found.
    */
   public URL getDriverResource(String name) {
      return getProvider().getDriverResource(name);
   }

   public Set<String> getDrivers() {
      return getProvider().getDrivers();
   }

   public Driver getDriver(String url) throws SQLException {
      return getProvider().getDriver(url);
   }

   /**
    * Check if Hive data model is supported.
    */
   public boolean isHiveEnabled() {
      return Plugins.getInstance().getServices(
         inetsoft.uql.jdbc.DriverService.class, "inetsoft.driver.hive") != null;
   }

   /**
    * Check if query results are cached.
    */
   public boolean isDataCached() {
      return dataCached;
   }

   /**
    * Set if query results are cached.
    */
   public void setDataCached(boolean dataCached) {
      this.dataCached = dataCached;
   }

   /**
    * Check if data caching is only for design-time (sample).
    */
   public boolean isDesignOnly() {
      return designOnly;
   }

   /**
    * Set if data caching is only for design-time (sample).
    */
   public void setDesignOnly(boolean flag) {
      designOnly = flag;
   }

   private synchronized DriverProvider getProvider() {
      if(provider == null) {
         provider = new PluginDriverProvider();
      }

      return provider;
   }

   private DriverProvider provider;
   private final ReadWriteLock driverServicesLock = new ReentrantReadWriteLock();
   private Map<String, List<DriverService>> driverServices = null;
   private List<TabularDriverService> tabularServices = null;
   private boolean dataCached = false;
   private boolean designOnly = false;
   private static final Logger LOG = LoggerFactory.getLogger(Drivers.class);
}
