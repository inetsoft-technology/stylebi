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
import java.util.Objects;

@InetsoftConfigBean
public class CloudRunnerConfig implements Serializable {
   /**
    * The type of cloud runner config being used.
    */
   public String getType() {
      return type;
   }

   public void setType(String type) {
      Objects.requireNonNull(type, "The cloud runner type is required");
      this.type = type;
   }

   /**
    * The Fargate configuration.
    */
   public FargateCloudRunnerConfig getFargate() {
      return fargate;
   }

   public void setFargate(FargateCloudRunnerConfig fargate) {
      this.fargate = fargate;
   }

   /**
    * The Google configuration.
    */
   public GoogleCloudRunnerConfig getGoogle() {
      return google;
   }

   public void setGoogle(GoogleCloudRunnerConfig google) {
      this.google = google;
   }

   /**
    * The Kubernetes configuration.
    */
   public KubernetesCloudRunnerConfig getKubernetes() {
      return kubernetes;
   }

   public void setKubernetes(KubernetesCloudRunnerConfig kubernetes) {
      this.kubernetes = kubernetes;
   }

   /**
    * The Azure configuration.
    */
   public AzureCloudRunnerConfig getAzure() {
      return azure;
   }

   public void setAzure(AzureCloudRunnerConfig azure) {
      this.azure = azure;
   }

   /**
    * The local docker configuration.
    */
   public DockerCloudRunnerConfig getDocker() {
      return docker;
   }

   public void setDocker(DockerCloudRunnerConfig docker) {
      this.docker = docker;
   }

   private String type;
   private FargateCloudRunnerConfig fargate;
   private GoogleCloudRunnerConfig google;
   private KubernetesCloudRunnerConfig kubernetes;
   private AzureCloudRunnerConfig azure;
   private DockerCloudRunnerConfig docker;
}
