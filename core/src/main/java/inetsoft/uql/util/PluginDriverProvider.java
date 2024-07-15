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
package inetsoft.uql.util;

import inetsoft.uql.jdbc.DriverService;
import inetsoft.uql.jdbc.SQLExecutor;

import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

class PluginDriverProvider implements DriverProvider {
   @Override
   public ClassLoader getDriverClassLoader(String classname, String url) {
      return getDriverService(classname, url).map(s -> s.getClass().getClassLoader()).orElse(null);
   }

   @Override
   public SQLExecutor getSQLExecutor(String classname, String url) {
      return getDriverService(classname, url).map(DriverService::getSQLExecutor).orElse(null);
   }

   @Override
   public Set<String> getDrivers() {
      return getDriverServices().stream()
         .map(DriverService::getDrivers)
         .flatMap(Set::stream)
         .collect(Collectors.toSet());
   }

   @Override
   public Driver getDriver(String url) throws SQLException {
      Optional<DriverService> service = getDriverService(null, url);

      if(service.isPresent()) {
         ClassLoader loader = service.get().getClass().getClassLoader();

         for(String driverClassName : service.get().getDrivers()) {
            try {
               Class<?> driverClass = loader.loadClass(driverClassName);
               Driver driver = (Driver) driverClass.getConstructor().newInstance();

               if(driver.acceptsURL(url)) {
                  return driver;
               }
            }
            catch(SQLException e) {
               throw e;
            }
            catch(Exception ignore) {
            }
         }
      }

      // taken from DriverManager
      throw new SQLException("No suitable driver found for " + url, "08001");
   }

   @Override
   public Class<?> getDriverClass(String className) throws ClassNotFoundException {
      // Force the DriverManager class to be initialized to avoid deadlocks with JDBC Driver
      // class initialization.
      Class.forName(DriverManager.class.getName());
      DRIVER_CLASS_LOADING_LOCK.lock();

      try {
         for(DriverService service : getDriverServices()) {
            ClassLoader loader = service.getClass().getClassLoader();
            // Force the DriverManager class to be initialized to avoid deadlocks with JDBC Driver
            // class initialization.
            Class.forName(DriverManager.class.getName(), true, loader);

            try {
               return Class.forName(className, true, loader);
            }
            catch(ClassNotFoundException | NoClassDefFoundError ignore) {
            }
         }

         return null;
      }
      finally {
         DRIVER_CLASS_LOADING_LOCK.unlock();
      }
   }

   @Override
   public URL getDriverResource(String name) {
      for(DriverService service : getDriverServices()) {
         URL url = service.getClass().getClassLoader().getResource(name);

         if(url != null) {
            return url;
         }
      }

      return getClass().getClassLoader().getResource(name);
   }

   private Optional<DriverService> getDriverService(String classname, String url) {
      DriverService service = Drivers.getInstance().getDriverService(classname, url);

      if(service == null) {
         return Optional.empty();
      }

      return Optional.of(service);
   }

   private List<DriverService> getDriverServices() {
      return new ArrayList<>(Drivers.getInstance().getDriverServices());
   }

   private static final Lock DRIVER_CLASS_LOADING_LOCK = new ReentrantLock(true);
}
