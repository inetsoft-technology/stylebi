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
package inetsoft.sree.schedule;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Paths;

/**
 * Class that handles launching the schedule server using a classpath specified in a resource file.
 * This is required because of inconsistent handling of absolute paths in the Class-Path JAR
 * manifest attribute.
 *
 * @since 13.1
 */
public final class ScheduleLauncher {
   private ScheduleLauncher() {
   }

   /**
    * Main entry point of the program.
    *
    * @param args the command line arguments.
    */
   public static void main(String[] args) throws Exception {
      ClassLoader loader = createClassLoader();
      Class<?> serverClass = loader.loadClass("inetsoft.sree.schedule.ScheduleServer");
      Method mainMethod = serverClass.getDeclaredMethod("main", String[].class);
      Thread.currentThread().setContextClassLoader(loader);
      mainMethod.invoke(null, (Object) args);
   }

   /**
    * Creates a class loader from the resource file containing the classpath entries.
    *
    * @return the new class loader.
    */
   private static ClassLoader createClassLoader() throws IOException {
      URL[] urls;

      try(BufferedReader reader = getClasspathReader()) {
         urls = reader.lines()
            .map(ScheduleLauncher::getClasspathElement)
            .toArray(URL[]::new);
      }

      ClassLoader parent = ScheduleLauncher.class.getClassLoader();
      return new URLClassLoader(urls, parent);
   }

   /**
    * Gets the URL for a line from the classpath file.
    *
    * @param element the classpath file entry.
    *
    * @return the classpath element URL.
    */
   private static URL getClasspathElement(String element) {
      try {
         return Paths.get(element).toUri().toURL();
      }
      catch(MalformedURLException e) {
         throw new RuntimeException("Invalid classpath entry: " + element, e);
      }
   }

   private static BufferedReader getClasspathReader() {
      return new BufferedReader(new InputStreamReader(
         ScheduleLauncher.class.getResourceAsStream("ScheduleLauncher.txt")));
   }
}
