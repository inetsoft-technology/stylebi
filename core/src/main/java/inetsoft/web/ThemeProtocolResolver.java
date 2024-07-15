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
package inetsoft.web;

import inetsoft.sree.portal.*;
import inetsoft.util.DataSpace;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.*;

import java.io.*;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class ThemeProtocolResolver implements ProtocolResolver {
   @Override
   public Resource resolve(String location, ResourceLoader resourceLoader) {
      if(location.startsWith(PROTOCOL)) {
         String path = location.substring(PROTOCOL.length())
            .replaceAll("^/", "").replaceAll("/$", "");
         int index = path.indexOf('/');
         String themeId;
         String resourceName;

         if(index < 0) {
            themeId = "default";
            resourceName = path;
         }
         else {
            themeId = path.substring(0, index);
            resourceName = path.substring(index + 1);
         }

         return createThemeResource(themeId, resourceName);
      }

      return null;
   }

   private static ThemeResource createThemeResource(String themeId, String resourceName) {
      CustomTheme theme = CustomThemesManager.getManager().getCustomThemes().stream()
         .filter(t -> t.getId().equals(themeId))
         .findAny()
         .orElse(null);
      String folder = null;
      String file = resourceName;
      int index = resourceName.lastIndexOf('/');

      if(index >= 0) {
         folder = resourceName.substring(0, index);
         file = resourceName.substring(index + 1);
      }

      return new ThemeResource(theme, folder, file);
   }

   private static final String PROTOCOL = "theme:";
   private static final Logger LOG = LoggerFactory.getLogger(ThemeProtocolResolver.class);

   public static final class ThemeResource extends AbstractResource {
      ThemeResource(CustomTheme theme, String folder, String file) {
         this.theme = theme;
         this.folder = folder;
         this.file = file;
      }

      @Override
      public String getDescription() {
         StringBuilder description = new StringBuilder();
         description.append("theme resource [");

         if(folder != null) {
            description.append(folder).append('/');
         }

         return description.append(file).append(']').toString();
      }

      @Override
      public boolean exists() {
         try {
            return withJarEntry(this::exists);
         }
         catch(IOException e) {
            LOG.warn("Failed to determine if theme file exists", e);
            return false;
         }
      }

      private boolean exists(JarEntry entry, JarInputStream input) {
         return entry != null || getDefaultResource() != null;
      }

      @Override
      public long contentLength() throws IOException {
         return withJarEntry(this::contentLength);
      }

      private long contentLength(JarEntry entry, JarInputStream input) throws IOException {
         if(entry == null) {
            try(InputStream in = getDefaultInputStream()) {
               long size = 0;
               byte[] buffer = new byte[1024];
               int read;

               while((read = in.read(buffer)) >= 0) {
                  size += read;
               }

               return size;
            }
         }
         else {
            return entry.getSize();
         }
      }

      @Override
      public long lastModified() throws IOException {
         return withJarEntry(this::lastModified);
      }

      private long lastModified(JarEntry entry, JarInputStream input) throws IOException {
         if(entry == null) {
            return 0L;
         }

         if(entry.getLastModifiedTime() == null) {
            return withDataSpaceJar((dataSpace, folder, file) ->
                                       file == null ? 0L : dataSpace.getLastModified(folder, file));
         }

         return entry.getLastModifiedTime().toInstant().toEpochMilli();
      }

      @Override
      public String getFilename() {
         return file;
      }

      @Override
      public InputStream getInputStream() throws IOException {
         return withJarEntry(this::getInputStream);
      }

      private InputStream getInputStream(JarEntry entry, JarInputStream input) throws IOException {
         if(entry == null) {
            return getDefaultInputStream();
         }

         ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         IOUtils.copy(input, buffer);
         return new ByteArrayInputStream(buffer.toByteArray());
      }

      @Override
      public URL getURL() throws IOException {
         StringBuilder url = new StringBuilder("theme:");

         if(theme == null) {
            url.append("default");
         }
         else {
            url.append(theme.getId());
         }

         url.append("/inetsoft/web/resources/");

         if(folder != null) {
            url.append(folder).append('/');
         }

         url.append(file);
         return new URL(null, url.toString(), new ThemeURLStreamHandler());
      }

      @Override
      public Resource createRelative(String relativePath) throws IOException {
         String path;

         if(folder == null) {
            path = file;
         }
         else {
            path = folder + "/" + file;
         }

         return withJarEntry((entry, input) -> {
            String resourcePath;

            if(entry == null || !entry.isDirectory()) {
               int index = path.lastIndexOf('/');

               if(index < 0) {
                  resourcePath = relativePath;
               }
               else {
                  resourcePath = path.substring(0, index) + '/' + relativePath;
               }
            }
            else {
               resourcePath = path + '/' + relativePath;
            }

            int index = resourcePath.lastIndexOf('/');
            String resourceFolder = null;
            String resourceFile = resourcePath;

            if(index >= 0) {
               resourceFolder = resourcePath.substring(0, index);
               resourceFile = resourcePath.substring(index + 1);
            }

            return new ThemeResource(theme, resourceFolder, resourceFile);
         });
      }

      private String getPath() {
         StringBuilder path = new StringBuilder();

         if(folder != null) {
            path.append(folder).append('/');
         }

         return path.append(file).toString();
      }

      private <T> T withDataSpaceJar(DataSpaceJarFunction<T> fn) throws IOException {
         String jarDir = null;
         String jarFile = null;

         if(theme != null) {
            jarFile = theme.getJarPath();
            int index = jarFile.lastIndexOf('/');

            if(index >= 0) {
               jarDir = jarFile.substring(0, index);
               jarFile = jarFile.substring(index + 1);
            }
         }

         DataSpace dataSpace = DataSpace.getDataSpace();
         return fn.apply(dataSpace, jarDir, jarFile);
      }

      private <T> T withJarEntry(JarEntryFunction<T> fn) throws IOException {
         return withDataSpaceJar((ds, d, f) -> withJarEntry(ds, d, f, fn));
      }

      private <T> T withJarEntry(DataSpace dataSpace, String jarDir, String jarFile,
                                 JarEntryFunction<T> fn) throws IOException
      {
         if(jarFile == null || !dataSpace.exists(jarDir, jarFile)) {
            return fn.apply(null, null);
         }

         String path = getPath();

         try(InputStream input = dataSpace.getInputStream(jarDir, jarFile)) {
            JarInputStream jarInput = new JarInputStream(input);
            JarEntry entry;

            while((entry = jarInput.getNextJarEntry()) != null) {
               if(entry.getName().equals(path)) {
                  return fn.apply(entry, jarInput);
               }
            }
         }

         return fn.apply(null, null);
      }

      private InputStream getDefaultInputStream() {
         return getClass().getResourceAsStream("/" + getPath());
      }

      private URL getDefaultResource() {
         return getClass().getResource("/" + getPath());
      }

      @Override
      public String toString() {
         return "ThemeResource{" +
            "theme=" + theme +
            ", folder='" + folder + '\'' +
            ", file='" + file + '\'' +
            '}';
      }

      private final CustomTheme theme;
      private final String folder;
      private final String file;
   }

   @FunctionalInterface
   private interface DataSpaceJarFunction<T> {
      T apply(DataSpace dataSpace, String folder, String file) throws IOException;
   }

   @FunctionalInterface
   private interface JarEntryFunction<T> {
      T apply(JarEntry entry, JarInputStream input) throws IOException;
   }
}
