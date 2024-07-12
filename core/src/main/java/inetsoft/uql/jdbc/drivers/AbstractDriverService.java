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
package inetsoft.uql.jdbc.drivers;

import inetsoft.uql.jdbc.DriverService;
import inetsoft.uql.jdbc.SQLExecutor;

import java.sql.Driver;
import java.util.Set;

/**
 * {@code AbstractDriverService} is a base class for implementations of {@link DriverService}.
 */
public abstract class AbstractDriverService implements DriverService {
   @Override
   public boolean matches(String driver, String url) {
      if(driver != null && getDrivers().contains(driver)) {
         return true;
      }

      if(url != null && !url.isEmpty()) {
         for(String prefix : getUrls()) {
            if(url.startsWith(prefix)) {
               return true;
            }
         }

         ClassLoader loader = getClass().getClassLoader();

         for(String driverClassName : getDrivers()) {
            try {
               Class<?> driverClass = loader.loadClass(driverClassName);
               Driver driverInstance = (Driver) driverClass.getConstructor().newInstance();

               if(driverInstance.acceptsURL(url)) {
                  return true;
               }
            }
            catch(Exception ignore) {
            }
         }
      }

      return false;
   }

   @Override
   public SQLExecutor getSQLExecutor() {
      return null;
   }

   /**
    * Gets the URL prefixes matched by the driver.
    */
   protected abstract Set<String> getUrls();
}
