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

@InetsoftConfigBean
public class GoogleCloudRunnerConfig implements Serializable {
   /**
    * Google project ID
    */
   public String getProjectId() {
      return projectId;
   }

   public void setProjectId(String projectId) {
      Objects.requireNonNull(projectId, "Google cloud runner project ID is required");
      this.projectId = projectId;
   }

   /**
    * Location
    */
   public String getLocation() {
      return location;
   }

   public void setLocation(String location) {
      Objects.requireNonNull(location, "Google cloud runner location is required");
      this.location = location;
   }

   /**
    * Path to the service account key
    */
   public String getServiceAccountFile() {
      return serviceAccountFile;
   }

   public void setServiceAccountFile(String serviceAccountFile) {
      this.serviceAccountFile = serviceAccountFile;
   }

   /**
    * Base64 encoded service account json
    */
   public String getServiceAccountJson() {
      return serviceAccountJson;
   }

   public void setServiceAccountJson(String serviceAccountJson) {
      this.serviceAccountJson = serviceAccountJson;
   }

   /**
    * The uri to the docker image for the schedule task runner
    */
   public String getDockerImageUri() {
      return dockerImageUri;
   }

   public void setDockerImageUri(String dockerImageUri) {
      Objects.requireNonNull(dockerImageUri, "The Google cloud runner Docker image URI is required");
      this.dockerImageUri = dockerImageUri;
   }

   public String getVpcNetwork() {
      return vpcNetwork;
   }

   public void setVpcNetwork(String vpcNetwork) {
      this.vpcNetwork = vpcNetwork;
   }

   public String getVpcSubnet() {
      return vpcSubnet;
   }

   public void setVpcSubnet(String vpcSubnet) {
      this.vpcSubnet = vpcSubnet;
   }

   public String getCpu() {
      return cpu;
   }

   public void setCpu(String cpu) {
      this.cpu = cpu;
   }

   public String getMemory() {
      return memory;
   }

   public void setMemory(String memory) {
      this.memory = memory;
   }

   private String projectId;
   private String location;
   private String serviceAccountFile;
   private String serviceAccountJson;
   private String dockerImageUri;
   private String vpcNetwork;
   private String vpcSubnet;
   private String cpu = "2";
   private String memory = "2Gi";
}
