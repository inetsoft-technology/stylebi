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
package inetsoft.util.dataspace;

import inetsoft.util.DataSpace;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;

public class DataSpaceURLConnection extends URLConnection {
   public DataSpaceURLConnection(URL url) {
      this(url, DataSpace.getDataSpace());
   }

   public DataSpaceURLConnection(URL url, DataSpace dataSpace) {
      super(url);
      this.dataSpace = dataSpace;
   }

   @Override
   public void connect() throws IOException {
      connected = true;
   }

   @Override
   public InputStream getInputStream() throws IOException {
      return doWithPath(dataSpace::getInputStream);
   }

   @Override
   public OutputStream getOutputStream() throws IOException {
      throw new UnsupportedOperationException();
   }

   private <T> T doWithPath(PathFunction<T> fn) throws IOException {
      String path = url.getPath().replaceAll("^/", "").replaceAll("/$", "");
      String dir;
      String file;
      int index = path.lastIndexOf('/');

      if(index < 0) {
         dir = null;
         file = path;
      }
      else {
         dir = path.substring(index);
         file = path.substring(index + 1);
      }

      return fn.apply(dir, file);
   }

   private final DataSpace dataSpace;

   @FunctionalInterface
   private interface PathFunction<T> {
      T apply(String dir, String file) throws IOException;
   }
}
