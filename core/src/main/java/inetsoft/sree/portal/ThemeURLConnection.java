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
package inetsoft.sree.portal;

import inetsoft.util.DataSpace;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class ThemeURLConnection extends URLConnection {
   public ThemeURLConnection(URL url) {
      super(url);
   }

   @Override
   public void connect() throws IOException {
      connected = true;
   }

   @Override
   public InputStream getInputStream() throws IOException {
      String path = url.getPath().replaceAll("^/", "").replaceAll("/$", "");
      int index = path.indexOf('/');
      String themeId;

      if(index < 0) {
         themeId = "default";
      }
      else {
         themeId = path.substring(0, index);
         path = path.substring(index + 1);
      }

      if(themeId.equals("default")) {
         return getDefaultInputStream(path);
      }

      CustomTheme theme = CustomThemesManager.getManager().getCustomThemes().stream()
         .filter(t -> t.getId().equals(themeId))
         .findAny()
         .orElse(null);

      if(theme == null) {
         LOG.debug("Theme with ID '{}' not found", themeId);
         return getDefaultInputStream(path);
      }

      String jarDir = null;
      String jarFile = theme.getJarPath();
      index = theme.getJarPath().lastIndexOf('/');

      if(index >= 0) {
         jarDir = jarFile.substring(0, index);
         jarFile = jarFile.substring(index + 1);
      }

      DataSpace dataSpace = DataSpace.getDataSpace();

      if(!dataSpace.exists(jarDir, jarFile)) {
         LOG.debug(
            "Theme JAR file '{}' for theme '{}' not found", theme.getJarPath(), theme.getId());
         return getDefaultInputStream(path);
      }


      try(InputStream input = dataSpace.getInputStream(jarDir, jarFile)) {
         JarInputStream jarInput = new JarInputStream(input);
         JarEntry entry;

         while((entry = jarInput.getNextJarEntry()) != null) {
            if(entry.getName().equals(path)) {
               ByteArrayOutputStream buffer = new ByteArrayOutputStream();
               IOUtils.copy(jarInput, buffer);
               return new ByteArrayInputStream(buffer.toByteArray());
            }
         }
      }

      LOG.debug("Resource '{}' not found in theme '{}'", path, themeId);
      return getDefaultInputStream(path);
   }

   private InputStream getDefaultInputStream(String path) {
      return getClass().getResourceAsStream("/" + path);
   }

   private static final Logger LOG = LoggerFactory.getLogger(ThemeURLConnection.class);
}