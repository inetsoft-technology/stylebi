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
package inetsoft.util.config;

import java.io.Serializable;
import java.util.Objects;

@InetsoftConfigBean
public class ExternalStorageConfig implements Serializable {
   public String getType() {
      return type;
   }

   public void setType(String type) {
      Objects.requireNonNull(type, "The external storage type is required");
      this.type = type;
   }

   public FilesystemConfig getFilesystem() {
      return filesystem;
   }

   public void setFilesystem(FilesystemConfig filesystem) {
      this.filesystem = filesystem;
   }

   public S3Config getS3() {
      return s3;
   }

   public void setS3(S3Config s3) {
      this.s3 = s3;
   }

   public AzureBlobConfig getAzure() {
      return azure;
   }

   public void setAzure(AzureBlobConfig azure) {
      this.azure = azure;
   }

   public GCSConfig getGcs() {
      return gcs;
   }

   public void setGcs(GCSConfig gcs) {
      this.gcs = gcs;
   }

   private String type;
   private FilesystemConfig filesystem;
   private S3Config s3;
   private AzureBlobConfig azure;
   private GCSConfig gcs;
}
