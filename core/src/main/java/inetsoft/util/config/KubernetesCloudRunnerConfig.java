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
public class KubernetesCloudRunnerConfig implements Serializable {
   /**
    * Path to the kubernetes config file. If not specified then it falls back to $HOME/.kube/config
    */
   public String getConfigFilePath() {
      return configFilePath;
   }

   public void setConfigFilePath(String configFilePath) {
      this.configFilePath = configFilePath;
   }

   /**
    * Namespace
    */
   public String getNamespace() {
      return namespace;
   }

   public void setNamespace(String namespace) {
      this.namespace = namespace;
   }

   /**
    * The uri to the docker image for the schedule task runner
    */
   public String getDockerImageUri() {
      return dockerImageUri;
   }

   public void setDockerImageUri(String dockerImageUri) {
      Objects.requireNonNull(dockerImageUri, "The Kubernetes cloud runner Docker image URI is required");
      this.dockerImageUri = dockerImageUri;
   }

   public String getImagePullPolicy() {
      return imagePullPolicy;
   }

   public void setImagePullPolicy(String imagePullPolicy) {
      this.imagePullPolicy = imagePullPolicy;
   }

   public String[] getImagePullSecrets() {
      return imagePullSecrets;
   }

   public void setImagePullSecrets(String[] imagePullSecrets) {
      this.imagePullSecrets = imagePullSecrets;
   }

   public int getTtlSecondsAfterFinished() {
      return ttlSecondsAfterFinished;
   }

   public void setTtlSecondsAfterFinished(int ttlSecondsAfterFinished) {
      this.ttlSecondsAfterFinished = ttlSecondsAfterFinished;
   }

   public int getBackoffLimit() {
      return backoffLimit;
   }

   public void setBackoffLimit(int backoffLimit) {
      this.backoffLimit = backoffLimit;
   }

   public String[] getVolumeMounts() {
      return volumeMounts;
   }

   public void setVolumeMounts(String[] volumeMounts) {
      this.volumeMounts = volumeMounts;
   }

   public String[] getTolerations() {
      return tolerations;
   }

   public void setTolerations(String[] tolerations) {
      this.tolerations = tolerations;
   }

   private String configFilePath;
   private String namespace = "default";
   private String dockerImageUri;
   private String imagePullPolicy = "IfNotPresent";
   private String[] imagePullSecrets;
   private int ttlSecondsAfterFinished = 120;
   private int backoffLimit = 4;
   private String[] volumeMounts;
   private String[] tolerations;
}
