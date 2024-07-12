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

import inetsoft.web.admin.upload.UploadedFile;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code DriverPluginGenerator} generates a plugin for one or more JDBC drivers.
 */
public class DriverPluginGenerator {
   /**
    * Generates a driver plugin. The JAR files will be scanned for driver classes.
    *
    * @param pluginId      the plugin identifier.
    * @param pluginVersion the plugin version.
    * @param pluginName    the display name for the plugin.
    * @param jarFiles      the driver JAR files.
    * @param pluginFile    the plugin ZIP file to write.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void generatePlugin(String pluginId, String pluginVersion, String pluginName,
                              File[] jarFiles, File pluginFile) throws IOException
   {
      DriverScanner scanner = new DriverScanner();
      Set<String> drivers = new HashSet<>();

      for(File file : jarFiles) {
         drivers.addAll(scanner.scan(file));
      }

      generatePlugin(
         pluginId, pluginVersion, pluginName, drivers.toArray(new String[0]), jarFiles, pluginFile);
   }

   /**
    * Generates a driver plugin.
    *
    * @param pluginId      the plugin identifier.
    * @param pluginVersion the plugin version.
    * @param pluginName    the display name for the plugin.
    * @param drivers       the fully qualified class names of the JDBC drivers.
    * @param jarFiles      the driver JAR files.
    * @param pluginFile    the plugin ZIP file to write.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void generatePlugin(String pluginId, String pluginVersion, String pluginName,
                              String[] drivers, File[] jarFiles, File pluginFile)
      throws IOException
   {
      UploadedFile[] files = Arrays.stream(jarFiles)
         .map(f -> UploadedFile.builder().file(f).fileName(f.getName()).build())
         .toArray(UploadedFile[]::new);
      generatePlugin(pluginId, pluginVersion, pluginName, drivers, files, pluginFile);
   }

   public void generatePlugin(String pluginId, String pluginVersion, String pluginName,
                              String[] drivers, UploadedFile[] jarFiles, File pluginFile)
      throws IOException
   {
      String packageName = "drivers." + pluginId.replaceAll("[^a-zA-Z0-9.]", "_");
      String driverServiceClassName = packageName + ".DriverService";

      try(ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pluginFile.toPath()))) {
         createFolder("classes/", zip);
         createFolder("classes/META-INF/", zip);
         writeManifest(pluginId, pluginVersion, pluginName, zip);
         createFolder("classes/META-INF/services/", zip);
         writeServiceRegistration(driverServiceClassName, zip);
         createPackageFolder(packageName.split("\\."), zip);
         writeServiceClass(driverServiceClassName, zip);
         writeDrivers(driverServiceClassName, drivers, zip);
         writeUrls(driverServiceClassName, zip);
         writeJarFiles(jarFiles, zip);
      }
   }

   private void createFolder(String path, ZipOutputStream zip) throws IOException {
      ZipEntry entry = new ZipEntry(path);
      zip.putNextEntry(entry);
   }

   private void createPackageFolder(String[] pathElements, ZipOutputStream zip)
      throws IOException
   {
      List<String> paths = Arrays.asList(pathElements);

      for(int i = 0; i < paths.size(); i++) {
         createFolder("classes/" + String.join("/", paths.subList(0, i + 1)) + "/", zip);
      }
   }

   private void writeManifest(String pluginId, String pluginVersion, String pluginName,
                              ZipOutputStream zip) throws IOException
   {
      ZipEntry entry = new ZipEntry("classes/META-INF/MANIFEST.MF");
      zip.putNextEntry(entry);

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
      writer.println("Manifest-Version: 1.0");
      writer.format("Plugin-Id: %s%n", pluginId);
      writer.println("Plugin-Provider: InetSoft Technology");
      writer.format("Plugin-Version: %s%n", pluginVersion);
      writer.format("Plugin-Name: %s%n", pluginName);
      writer.println("Plugin-Classloader-First: true");
      writer.println("Plugin-Merge-Into-Required: false");
      writer.flush();

      zip.closeEntry();
   }

   private void writeServiceRegistration(String className, ZipOutputStream zip) throws IOException {
      ZipEntry entry = new ZipEntry("classes/META-INF/services/inetsoft.uql.jdbc.DriverService");
      zip.putNextEntry(entry);

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
      writer.println(className);
      writer.flush();

      zip.closeEntry();
   }

   private void writeServiceClass(String className, ZipOutputStream zip) throws IOException {
      ZipEntry entry = new ZipEntry(getClassPrefix(className) + ".class");
      zip.putNextEntry(entry);
      zip.write(
         new DriverServiceGenerator().generateDriverServiceClass(className));
      zip.closeEntry();
   }

   private void writeDrivers(String className, String[] drivers, ZipOutputStream zip)
      throws IOException
   {
      ZipEntry entry = new ZipEntry(getClassPrefix(className) + ".drivers");
      zip.putNextEntry(entry);
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));

      for(String driver : drivers) {
         writer.println(driver);
      }

      writer.flush();
      zip.closeEntry();
   }

   private void writeUrls(String className, ZipOutputStream zip) throws IOException {
      ZipEntry entry = new ZipEntry(getClassPrefix(className) + ".urls");
      zip.putNextEntry(entry);
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
      writer.println();
      writer.flush();
      zip.closeEntry();
   }

   private String getClassPrefix(String className) {
      return "classes/" + className.replace('.', '/');
   }

   private void writeJarFiles(UploadedFile[] jarFiles, ZipOutputStream zip) throws IOException {
      createFolder("lib/", zip);

      for(UploadedFile file : jarFiles) {
         ZipEntry entry = new ZipEntry("lib/" + file.fileName());
         zip.putNextEntry(entry);

         try(InputStream input = Files.newInputStream(file.file().toPath())) {
            IOUtils.copy(input, zip);
         }

         zip.closeEntry();
      }
   }
}
