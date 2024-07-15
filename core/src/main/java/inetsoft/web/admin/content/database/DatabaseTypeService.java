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
package inetsoft.web.admin.content.database;

import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DatabaseTypeService {
   @Autowired
   public DatabaseTypeService(List<DatabaseType> databaseTypes) {
      this.databaseTypes = databaseTypes;
   }

   /**
    * Gets the database type with the specified identifier.
    *
    * @param type the type identifier.
    *
    * @return the matching type.
    */
   public DatabaseType<?> getDatabaseType(String type) {
      return databaseTypes.stream()
         .filter(dbType -> dbType.getType().equals(type))
         .findFirst().orElse(null);
   }

   /**
    * Finds the database type that supports the specific driver class.
    *
    * @param driverClass the fully-qualified class name of the JDBC driver.
    *
    * @return the matching database type.
    */
   public DatabaseType<?> getDatabaseTypeForDriver(String driverClass) {
      return databaseTypes.stream()
         .filter(dbType -> dbType.supportsDriverClass(driverClass) &&
            !CustomDatabaseType.TYPE.equals(dbType.getType())
         )
         .findFirst().orElse(getDatabaseType(CustomDatabaseType.TYPE));
   }

   /**
    * Gets information about all the database drivers.
    *
    * @return the drivers.
    */
   public DriverAvailability.DriverInfo[] getDrivers() {
      List<DriverAvailability.DriverInfo> result = new ArrayList<>();

      for(DatabaseType<?> type : databaseTypes) {
         DriverAvailability.DriverInfo driver = new DriverAvailability.DriverInfo();
         driver.setType(type.getType());
         driver.setInstalled(type.isDriverInstalled());
         driver.setDefaultPort(type.getDefaultPort());
         result.add(driver);
      }

      return result.toArray(new DriverAvailability.DriverInfo[0]);
   }

   @Autowired
   private final List<DatabaseType> databaseTypes;
}
