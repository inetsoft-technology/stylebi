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
public class AzureCloudRunnerConfig implements Serializable {
   /**
    * Get the tenant ID
    */
   public String getTenantId() {
      return tenantId;
   }

   public void setTenantId(String tenantId) {
      Objects.requireNonNull(tenantId, "The Azure cloud runner tenant ID is required");
      this.tenantId = tenantId;
   }

   /**
    * Get the subscription ID required for resource management
    */
   public String getSubscriptionId() {
      return subscriptionId;
   }

   public void setSubscriptionId(String subscriptionId) {
      Objects.requireNonNull(subscriptionId, "The Azure cloud runner subscription ID is required");
      this.subscriptionId = subscriptionId;
   }

   /**
    * Get the service principal client ID for authentication
    */
   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   /**
    * Get the service principal client secret for authentication
    */
   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   /**
    * The geo-location where the job resource will be deployed.
    */
   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      Objects.requireNonNull(region, "The Azure cloud runner region is required");
      this.region = region;
   }

   /**
    * The name of the resource group
    */
   public String getResourceGroup() {
      return resourceGroup;
   }

   public void setResourceGroup(String resourceGroup) {
      Objects.requireNonNull(resourceGroup, "The Azure cloud runner resource group is required");
      this.resourceGroup = resourceGroup;
   }

   /**
    * The uri to the docker image for the schedule task runner
    */
   public String getDockerImageUri() {
      return dockerImageUri;
   }

   public void setDockerImageUri(String dockerImageUri) {
      Objects.requireNonNull(dockerImageUri, "The Azure cloud runner Docker image URI is required");
      this.dockerImageUri = dockerImageUri;
   }

   /**
    * Resource ID of container app environment
    */
   public String getContainerAppsEnvironmentId() {
      return containerAppsEnvironmentId;
   }

   public void setContainerAppsEnvironmentId(String containerAppsEnvironmentId) {
      Objects.requireNonNull(containerAppsEnvironmentId, "The Azure cloud runner container apps environment ID is required");
      this.containerAppsEnvironmentId = containerAppsEnvironmentId;
   }

   /**
    * Container registry server
    */
   public String getContainerRegistryServer() {
      return containerRegistryServer;
   }

   public void setContainerRegistryServer(String containerRegistryServer) {
      this.containerRegistryServer = containerRegistryServer;
   }

   /**
    * Container registry username for authentication
    */
   public String getContainerRegistryUsername() {
      return containerRegistryUsername;
   }

   public void setContainerRegistryUsername(String containerRegistryUsername) {
      this.containerRegistryUsername = containerRegistryUsername;
   }

   /**
    * Container registry password for authentication
    */
   public String getContainerRegistryPassword() {
      return containerRegistryPassword;
   }

   public void setContainerRegistryPassword(String containerRegistryPassword) {
      this.containerRegistryPassword = containerRegistryPassword;
   }

   public double getCpu() {
      return cpu;
   }

   public void setCpu(double cpu) {
      this.cpu = cpu;
   }

   public String getMemory() {
      return memory;
   }

   public void setMemory(String memory) {
      this.memory = memory;
   }

   private String tenantId;
   private String subscriptionId;
   private String clientId;
   private String clientSecret;
   private String region;
   private String resourceGroup;
   private String dockerImageUri;
   private String containerAppsEnvironmentId;
   private String containerRegistryServer;
   private String containerRegistryUsername;
   private String containerRegistryPassword;
   private double cpu = 1;
   private String memory = "2Gi";
}
