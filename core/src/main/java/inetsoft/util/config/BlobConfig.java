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
package inetsoft.util.config;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code BlobConfig} contains the configuration for the blob storage.
 */
@InetsoftConfigBean
public class BlobConfig implements Serializable {
   /**
    * The type of blob storage being used.
    */
   public String getType() {
      return type;
   }

   public void setType(String type) {
      Objects.requireNonNull(type, "The blob type is required");
      this.type = type;
   }

   /**
    * The path to the local cache directory. This is not used with local filesystem storage.
    */
   public String getCacheDirectory() {
      return cacheDirectory;
   }

   public void setCacheDirectory(String cacheDirectory) {
      this.cacheDirectory = cacheDirectory;
   }

   /**
    * The Azure configuration.
    */
   public AzureBlobConfig getAzure() {
      return azure;
   }

   public void setAzure(AzureBlobConfig azure) {
      this.azure = azure;
   }

   /**
    * The filesystem configuration.
    */
   public FilesystemConfig getFilesystem() {
      return filesystem;
   }

   public void setFilesystem(FilesystemConfig filesystem) {
      this.filesystem = filesystem;
   }

   /**
    * The Google Cloud Storage configuration.
    */
   public GCSConfig getGcs() {
      return gcs;
   }

   public void setGcs(GCSConfig gcs) {
      this.gcs = gcs;
   }

   /**
    * The S3 configuration.
    */
   public S3Config getS3() {
      return s3;
   }

   public void setS3(S3Config s3) {
      this.s3 = s3;
   }

   private String type;
   private String cacheDirectory;
   private AzureBlobConfig azure;
   private FilesystemConfig filesystem;
   private GCSConfig gcs;
   private S3Config s3;
}
