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

/**
 * {@code ClusterConfig} contains the configuration for the cluster.
 */
@InetsoftConfigBean
public class ClusterConfig implements Serializable {
   /**
    * The default port number to which the cluster will be bound.
    */
   public int getPortNumber() {
      return portNumber;
   }

   public void setPortNumber(int portNumber) {
      this.portNumber = portNumber;
   }

   /**
    * The default outbound port number that the cluster will use.
    */
   public int getOutboundPortNumber() {
      return outboundPortNumber;
   }

   public void setOutboundPortNumber(int outboundPortNumber) {
      this.outboundPortNumber = outboundPortNumber;
   }

   /**
    * A flag that indicates if multicast discovery is enabled.
    */
   public boolean isMulticastEnabled() {
      return multicastEnabled;
   }

   public void setMulticastEnabled(boolean multicastEnabled) {
      this.multicastEnabled = multicastEnabled;
   }

   /**
    * The multicast broadcast address used for discovery.
    */
   public String getMulticastAddress() {
      return multicastAddress;
   }

   public void setMulticastAddress(String multicastAddress) {
      this.multicastAddress = multicastAddress;
   }

   /**
    * The multicast broadcast port number used for discovery.
    */
   public int getMulticastPort() {
      return multicastPort;
   }

   public void setMulticastPort(int multicastPort) {
      this.multicastPort = multicastPort;
   }

   /**
    * A flag that determines if TCP discovery is enabled.
    */
   public boolean isTcpEnabled() {
      return tcpEnabled;
   }

   public void setTcpEnabled(boolean tcpEnabled) {
      this.tcpEnabled = tcpEnabled;
   }

   /**
    * If enabled single node mode.
    */
   public boolean isSingleNode() {
      return singleNode;
   }

   public void setSingleNode(boolean singleNode) {
      this.singleNode = singleNode;
   }

   /**
    * The hostnames or IP addresses and port of the TCP discovery members.
    *
    * @return the member addresses.
    */
   public String[] getTcpMembers() {
      return tcpMembers;
   }

   public void setTcpMembers(String[] tcpMembers) {
      this.tcpMembers = tcpMembers;
   }

   /**
    * Client mode setting
    */
   public boolean isClientMode() {
      return clientMode;
   }

   public void setClientMode(boolean clientMode) {
      this.clientMode = clientMode;
   }

   /**
    * The path to the root CA private key file.
    */
   public String getCaKeyFile() {
      return caKeyFile;
   }

   public void setCaKeyFile(String caKeyFile) {
      this.caKeyFile = caKeyFile;
   }

   /**
    * The contents of the root CA private key.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getCaKey() {
      return caKey;
   }

   public void setCaKey(String caKey) {
      this.caKey = caKey;
   }

   /**
    * The password for the root CA private key.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getCaKeyPassword() {
      return caKeyPassword;
   }

   public void setCaKeyPassword(String caKeyPassword) {
      this.caKeyPassword = caKeyPassword;
   }

   /**
    * The path to the CA certificate file used to generate SSL certificates for cluster nodes and
    * clients.
    */
   public String getCaCertificateFile() {
      return caCertificateFile;
   }

   public void setCaCertificateFile(String caCertificateFile) {
      this.caCertificateFile = caCertificateFile;
   }

   /**
    * The CA certificate used to generate SSL certificates for cluster nodes and clients.
    */
   public String getCaCertificate() {
      return caCertificate;
   }

   public void setCaCertificate(String caCertificate) {
      this.caCertificate = caCertificate;
   }

   /**
    * The Kubernetes discovery settings.
    *
    * @return the Kubernetes discovery settings.
    */
   public KubernetesConfig getK8s() {
      return k8s;
   }

   public void setK8s(KubernetesConfig k8s) {
      this.k8s = k8s;
   }

   /**
    * Creates the default cluster configuration.
    *
    * @return the default cluster configuration.
    */
   static ClusterConfig createDefault() {
      return new ClusterConfig();
   }

   private int portNumber = 5701;
   private int outboundPortNumber = 0;
   private boolean multicastEnabled = true;
   private String multicastAddress = "224.2.2.3";
   private int multicastPort = 54327;
   private boolean tcpEnabled = false;
   private boolean singleNode = false;
   private String[] tcpMembers;
   private boolean clientMode = false;
   private String caKeyFile = null;
   private String caKey = null;
   private String caKeyPassword = null;
   private String caCertificateFile = null;
   private String caCertificate = null;
   private KubernetesConfig k8s;
}
