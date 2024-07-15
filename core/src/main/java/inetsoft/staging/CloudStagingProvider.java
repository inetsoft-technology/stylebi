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
package inetsoft.staging;

public abstract class CloudStagingProvider extends AbstractStagingProvider {

   protected UrlInfo parseUrl(String url, String scheme) {
      if(url == null || !url.startsWith(scheme)) {
         throw new IllegalArgumentException("Invalid URL: " + url);
      }

      String bucket;
      String path;
      String folder;
      String name;

      int index = url.indexOf('/', scheme.length());

      if(index < 0) {
         bucket = url.substring(scheme.length());
         path = "";
      }
      else {
         bucket = url.substring(scheme.length(), index);
         path = url.substring(index + 1);
      }

      if(path.endsWith("/")) {
         path = path.substring(0, path.length() - 1);
      }

      index = path.lastIndexOf('/');

      if(index < 0) {
         folder = "";
         name = path;
      }
      else {
         folder = path.substring(0, index);
         name = path.substring(index + 1);
      }

      return new UrlInfo(bucket, path, folder, name);
   }

   protected static final class UrlInfo {
      public UrlInfo(String bucket, String path, String folder, String name) {
         this.bucket = bucket;
         this.path = path;
         this.folder = folder;
         this.name = name;
      }

      public String getBucket() {
         return bucket;
      }

      public String getPath() {
         return path;
      }

      public String getFolder() {
         return folder;
      }

      public String getName() {
         return name;
      }

      private final String bucket;
      private final String path;
      private final String folder;
      private final String name;
   }
}
