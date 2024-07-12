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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * {@code AutoDriverService} is the base class for driver service implementations of user generated
 * driver plugins.
 */
@SuppressWarnings("unused")
public abstract class AutoDriverService extends AbstractDriverService {
   @Override
   public synchronized Set<String> getDrivers() {
      if(drivers == null) {
         drivers = loadFile(".drivers");
      }

      return drivers;
   }

   @Override
   protected synchronized Set<String> getUrls() {
      if(urls == null) {
         urls = loadFile(".urls");
      }

      return urls;
   }

   private Set<String> loadFile(String suffix) {
      String path = getClass().getSimpleName() + suffix;
      Set<String> lines = new HashSet<>();

      try(Scanner scanner =
             new Scanner(Objects.requireNonNull(getClass().getResourceAsStream(path))))
      {
         while(scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();

            if(!line.isEmpty()) {
               lines.add(line);
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to load driver list: " + path, e);
      }

      return lines;
   }

   private Set<String> drivers;
   private Set<String> urls;

   private static final Logger LOG = LoggerFactory.getLogger(AutoDriverService.class);
}
