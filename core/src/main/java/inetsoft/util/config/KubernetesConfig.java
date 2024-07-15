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
 * {@code KubernetesConfig} contains the configuration used for cluster discovery in Kubernetes.
 */
@InetsoftConfigBean
public class KubernetesConfig implements Serializable {
   /**
    * The hostname and port of the Kubernetes API server. If not set,
    * {@code https://kubernetes.default.svc} will be used.
    */
   public String getApiServer() {
      return apiServer;
   }

   public void setApiServer(String apiServer) {
      this.apiServer = apiServer;
   }

   /**
    * The authentication token for the Kubernetes API server. If not set, it will be obtained
    * from the {@code /var/run/secrets/kubernetes.io/serviceaccount/token} file.
    */
   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   /**
    * The namespace containing the server and scheduler pods. If not set, it will be obtained
    * from the {@code /var/run/secrets/kubernetes.io/serviceaccount/namespace} file.
    */
   public String getNamespace() {
      return namespace;
   }

   public void setNamespace(String namespace) {
      this.namespace = namespace;
   }

   /**
    * The name of the label that identifies pods that contain a server or scheduler instance.
    */
   public String getLabelName() {
      return labelName;
   }

   public void setLabelName(String labelName) {
      Objects.requireNonNull(labelName, "The Kubernetes label name is required");
      this.labelName = labelName;
   }

   /**
    * The value of the label that identifies pods that contain a server or scheduler instance.
    */
   public String getLabelValue() {
      return labelValue;
   }

   public void setLabelValue(String labelValue) {
      Objects.requireNonNull(labelValue, "The Kubernetes label value is required");
      this.labelValue = labelValue;
   }

   /**
    * Port to use for discovery
    */
   public int getDiscoveryPort() {
      return discoveryPort;
   }

   public void setDiscoveryPort(int discoveryPort) {
      this.discoveryPort = discoveryPort;
   }

   private String apiServer = "https://kubernetes.default.svc";
   private String token;
   private String namespace;
   private String labelName;
   private String labelValue;
   private int discoveryPort = 5701;
}
