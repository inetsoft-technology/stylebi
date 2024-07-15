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
package inetsoft.uql.jdbc.drivers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class DriverScanner {
   public Set<String> scan(File file) {
      long start = System.currentTimeMillis();
      Set<String> drivers = new HashSet<>();

      try {
         ClassLoader classLoader =
            new URLClassLoader(new URL[]{ file.toURI().toURL() }, getClass().getClassLoader());
         CachingMetadataReaderFactory readerFactory =
            new CachingMetadataReaderFactory(classLoader);
         AssignableTypeFilter filter = new AssignableTypeFilter(Driver.class);
         List<String> resourceNames;

         try(JarFile jar = new JarFile(file)) {
            resourceNames = jar.stream()
               .filter(e -> !e.isDirectory() && e.getName().endsWith(".class"))
               .map(e -> ResourceLoader.CLASSPATH_URL_PREFIX + e.getName())
               .collect(Collectors.toList());
         }

         for(String resourceName : resourceNames) {
            try {
               if(!"classpath:module-info.class".equals(resourceName)) {
                  Resource resource =
                     readerFactory.getResourceLoader().getResource(resourceName);
                  MetadataReader reader = readerFactory.getMetadataReader(resource);

                  if(filter.match(reader, readerFactory)) {
                     ClassMetadata meta = reader.getClassMetadata();

                     if(meta.isConcrete() && meta.isIndependent()) {
                        LOG.debug("Found driver class {}", meta.getClassName());
                        drivers.add(meta.getClassName());
                     }
                  }
               }
            }
            catch(Exception e) {
               LOG.warn(String.format(
                  "Failed to determine if entry %s in JAR file %s is a driver",
                  resourceName, file), e);
            }
         }

         LOG.debug(
            "Scanned JAR file {} for drivers in {}ms",
            file, System.currentTimeMillis() - start);
      }
      catch(Exception e) {
         LOG.warn("Failed to scan JAR file for drivers: " + file, e);
      }

      return drivers;
   }

   private static final Logger LOG = LoggerFactory.getLogger(DriverScanner.class);
}
