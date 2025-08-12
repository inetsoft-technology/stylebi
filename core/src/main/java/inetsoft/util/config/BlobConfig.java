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

import inetsoft.util.config.crd.CRDProperty;

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
    * The maximum size of the local cache in megabytes. This is not used with local filesystem
    * storage. If not set, the local cache will be unbounded.
    */
   public Long getCacheMaxSize() {
      return cacheMaxSize;
   }

   public void setCacheMaxSize(Long cacheMaxSize) {
      this.cacheMaxSize = cacheMaxSize;
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

   @CRDProperty(description = "The type of blob store", allowedValues = { "azure", "filesystem", "gcs", "s3" })
   private String type;
   private String cacheDirectory;
   @CRDProperty(description = "The maximum size of the local blob cache in MB")
   private Long cacheMaxSize;
   @CRDProperty(description = "The Azure Blob storage configuration")
   private AzureBlobConfig azure;
   @CRDProperty(description = "The shared filesystem storage configuration")
   private FilesystemConfig filesystem;
   @CRDProperty(description = "The GCS blob storage configuration")
   private GCSConfig gcs;
   @CRDProperty(description = "The S3 blob storage configuration")
   private S3Config s3;
}
