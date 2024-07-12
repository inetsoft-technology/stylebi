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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.config.json.PasswordDeserializer;
import inetsoft.util.config.json.PasswordSerializer;

import java.io.Serializable;
import java.util.Objects;

@InetsoftConfigBean
public class DockerCloudRunnerConfig implements Serializable {
   /**
    * The uri to the docker image for the schedule task runner
    */
   public String getDockerImageUri() {
      return dockerImageUri;
   }

   public void setDockerImageUri(String dockerImageUri) {
      Objects.requireNonNull(dockerImageUri, "The Docker image URI is required");
      this.dockerImageUri = dockerImageUri;
   }

   public String getDockerHost() {
      return dockerHost;
   }

   public void setDockerHost(String dockerHost) {
      Objects.requireNonNull(dockerImageUri, "The Docker host is required");
      this.dockerHost = dockerHost;
   }

   public boolean isTlsVerify() {
      return tlsVerify;
   }

   public void setTlsVerify(boolean tlsVerify) {
      this.tlsVerify = tlsVerify;
   }

   public String getCertificatePath() {
      return certificatePath;
   }

   public void setCertificatePath(String certificatePath) {
      this.certificatePath = certificatePath;
   }

   public String getConfigPath() {
      return configPath;
   }

   public void setConfigPath(String configPath) {
      this.configPath = configPath;
   }

   public String getApiVersion() {
      return apiVersion;
   }

   public void setApiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
   }

   public String getRegistryUrl() {
      return registryUrl;
   }

   public void setRegistryUrl(String registryUrl) {
      this.registryUrl = registryUrl;
   }

   public String getRegistryUsername() {
      return registryUsername;
   }

   public void setRegistryUsername(String registryUsername) {
      this.registryUsername = registryUsername;
   }

   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getRegistryPassword() {
      return registryPassword;
   }

   public void setRegistryPassword(String registryPassword) {
      this.registryPassword = registryPassword;
   }

   public String getRegistryEmail() {
      return registryEmail;
   }

   public void setRegistryEmail(String registryEmail) {
      this.registryEmail = registryEmail;
   }

   /**
    * The memory limit in bytes.
    */
   public long getMemory() {
      return memory;
   }

   public void setMemory(long memory) {
      this.memory = memory;
   }

   /**
    * The memory soft limit in bytes.
    */
   public long getMemoryReservation() {
      return memoryReservation;
   }

   public void setMemoryReservation(long memoryReservation) {
      this.memoryReservation = memoryReservation;
   }

   public boolean isAutoRemove() {
      return autoRemove;
   }

   public void setAutoRemove(boolean autoRemove) {
      this.autoRemove = autoRemove;
   }

   public String getNetwork() {
      return network;
   }

   public void setNetwork(String network) {
      this.network = network;
   }

   public String[] getVolumes() {
      return volumes;
   }

   public void setVolumes(String[] volumes) {
      this.volumes = volumes;
   }

   private String dockerImageUri = "636869400126.dkr.ecr.us-east-2.amazonaws.com/inetsoft/stylebi:14.0";
   private String dockerHost = "unix://var/run/docker.sock";
   private boolean tlsVerify = false;
   private String certificatePath;
   private String configPath;
   private String apiVersion;
   private String registryUrl;
   private String registryUsername;
   private String registryPassword;
   private String registryEmail;
   private long memory = 0L;
   private long memoryReservation = 0L;
   private boolean autoRemove = true;
   private String network;
   private String[] volumes;
}
