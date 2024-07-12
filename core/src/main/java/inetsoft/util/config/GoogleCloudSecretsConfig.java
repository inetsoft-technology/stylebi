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
package inetsoft.util.config;

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

   public List<String> getLocations() {
      return locations;
   }

   public void setLocations(List<String> locations) {
      this.locations = locations;
   }

   public List<String> getKmsKeyNames() {
      return kmsKeyNames;
   }

   public void setKmsKeyNames(List<String> kmsKeyNames) {
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
      clone.setLocations(new ArrayList<>(locations));
      clone.setKmsKeyNames(new ArrayList<>(kmsKeyNames));
      clone.setAutoReplication(autoReplication);

      return clone;
   }

   private String serviceAccountFile;
   private String serviceAccountJson;
   private String projectId;
   private List<String> locations;
   private List<String> kmsKeyNames;
   private boolean autoReplication;
}
