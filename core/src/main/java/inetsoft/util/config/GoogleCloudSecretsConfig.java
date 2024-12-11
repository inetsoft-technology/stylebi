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
import java.util.ArrayList;
import java.util.List;

@InetsoftConfigBean
public class GoogleCloudSecretsConfig implements Serializable, Cloneable {
   public String getServiceAccountFile() {
      return serviceAccountFile;
   }

   public void setServiceAccountFile(String serviceAccountFile) {
      this.serviceAccountFile = serviceAccountFile;
   }

   public String getServiceAccountJson() {
      return serviceAccountJson;
   }

   public void setServiceAccountJson(String serviceAccountJson) {
      this.serviceAccountJson = serviceAccountJson;
   }

   public String getProjectId() {
      return projectId;
   }

   public void setProjectId(String projectId) {
      this.projectId = projectId;
   }

   public String[] getLocations() {
      return locations;
   }

   public void setLocations(String[] locations) {
      this.locations = locations;
   }

   public String[] getKmsKeyNames() {
      return kmsKeyNames;
   }

   public void setKmsKeyNames(String[] kmsKeyNames) {
      this.kmsKeyNames = kmsKeyNames;
   }

   public boolean isAutoReplication() {
      return autoReplication;
   }

   public void setAutoReplication(boolean autoReplication) {
      this.autoReplication = autoReplication;
   }

   @Override
   public Object clone()  {
      GoogleCloudSecretsConfig clone = new GoogleCloudSecretsConfig();
      clone.setServiceAccountFile(serviceAccountFile);
      clone.setServiceAccountJson(serviceAccountJson);
      clone.setProjectId(projectId);
      clone.setAutoReplication(autoReplication);

      if(locations != null) {
         clone.locations = new String[locations.length];
         System.arraycopy(locations, 0, clone.locations, 0, locations.length);
      }

      if(kmsKeyNames != null) {
         clone.kmsKeyNames = new String[kmsKeyNames.length];
         System.arraycopy(kmsKeyNames, 0, clone.kmsKeyNames, 0, kmsKeyNames.length);
      }

      return clone;
   }

   private String serviceAccountFile;
   @CRDProperty(description = "The base-64 encoded service account JSON", secret = true)
   private String serviceAccountJson;
   @CRDProperty(description = "The project ID")
   private String projectId;
   @CRDProperty(description = "The canonical IDs of the location to replicate data")
   private String[] locations;
   @CRDProperty(description = "The resource name of the Cloud KMS CryptoKey used to encrypt secret payloads")
   private String[] kmsKeyNames;
   @CRDProperty(description = "A flag that indicates if the secret's replication policy is automatically managed by google or a user-defined location")
   private boolean autoReplication;
}
