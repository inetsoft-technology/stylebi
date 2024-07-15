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

import inetsoft.util.DataSpace;
import inetsoft.util.dataspace.DataSpaceURLStreamHandler;
import org.springframework.core.io.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class DataSpaceProtocolResolver implements ProtocolResolver {
   @Override
   public Resource resolve(String location, ResourceLoader resourceLoader) {
      if(location.startsWith(PROTOCOL)) {
         String resourceName = location.substring(PROTOCOL.length());
         return createDataSpaceResource(resourceName, DataSpace.getDataSpace());
      }

      return null;
   }

   private static DataSpaceResource createDataSpaceResource(String resourceName,
                                                            DataSpace dataSpace)
   {
      String dir;
      String file;
      String path = resourceName.replaceAll("^/", "").replaceAll("/$", "");
      int index = path.lastIndexOf('/');

      if(index < 0) {
         dir = null;
         file = path;
      }
      else {
         dir = path.substring(0, index);
         file = path.substring(index + 1);
      }

      if(dataSpace.exists(dir, file)) {
         return new DataSpaceResource(dir, file, dataSpace);
      }

      return null;
   }

   private static final String PROTOCOL = "dataspace:";

   public static final class DataSpaceResource extends AbstractResource {
      DataSpaceResource(String dir, String file, DataSpace dataSpace) {
         this.dir = dir;
         this.file = file;
         this.dataSpace = dataSpace;
      }

      @Override
      public String getDescription() {
         StringBuilder description = new StringBuilder();
         description.append("data space resource [");

         if(dir != null) {
            description.append(dir).append('/');
         }

         description.append(file).append(']');
         return description.toString();
      }

      @Override
      public boolean exists() {
         return dataSpace.exists(dir, file);
      }

      @Override
      public boolean isReadable() {
         return true;
      }

      @Override
      public long contentLength() {
         return dataSpace.getFileLength(dir, file);
      }

      @Override
      public long lastModified() {
         return dataSpace.getLastModified(dir, file);
      }

      @Override
      public String getFilename() {
         return file;
      }

      @Override
      public InputStream getInputStream() throws IOException {
         return dataSpace.getInputStream(dir, file);
      }

      @Override
      public URL getURL() throws IOException {
         String path;

         if(dir == null) {
            path = file;
         }
         else {
            path = dir + '/' + file;
         }

         return new URL(null, "dataspace://" + path, new DataSpaceURLStreamHandler(dataSpace));
      }

      @Override
      public Resource createRelative(String relativePath) {
         String path;

         if(dir == null) {
            path = file;
         }
         else {
            path = dir + '/' + file;
         }

         if(dataSpace.exists(dir, file) && dataSpace.isDirectory(path)) {
            path = path + '/' + relativePath;
         }
         else {
            int index = path.lastIndexOf('/');

            if(index < 0) {
               path = relativePath;
            }
            else {
               path = path.substring(0, index) + '/' + relativePath;
            }
         }

         return createDataSpaceResource(path, dataSpace);
      }

      private final String dir;
      private final String file;
      private final DataSpace dataSpace;
   }
}
