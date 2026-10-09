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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.config.crd.CRDProperty;
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
    * The port used for direct file transfers between cluster nodes (e.g. heap dumps).
    */
   public int getFileTransferPort() {
      return fileTransferPort;
   }

   public void setFileTransferPort(int fileTransferPort) {
      this.fileTransferPort = fileTransferPort;
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

   public IpFinderConfig getIpFinder() {
      return ipFinder;
   }

   public void setIpFinder(IpFinderConfig ipFinder) {
      this.ipFinder = ipFinder;
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
    * The minimum number of nodes the cluster will scale down to
    *
    * @return the minimum number of nodes
    */
   public int getMinNodes() {
      return minNodes;
   }

   public void setMinNodes(int minNodes) {
      this.minNodes = minNodes;
   }

   /**
    * A flag that indicates if a server node that finds itself in a minority cluster island
    * (another, larger cluster of the same deployment answers on the discovery addresses) raises
    * a segmentation failure, so that Ignite halts it and the container orchestrator restarts it
    * into the larger cluster. When off (the default), such a node only reports not-ready.
    */
   public boolean isMinorityIslandHalt() {
      return minorityIslandHalt;
   }

   public void setMinorityIslandHalt(boolean minorityIslandHalt) {
      this.minorityIslandHalt = minorityIslandHalt;
   }

   /**
    * The minimum number of seconds that the last instance should be kept up before scaling to zero.
    *
    * @return the minimum uptime in seconds.
    */
   public long getMinSingleInstanceUptime() {
      return minSingleInstanceUptime;
   }

   public void setMinSingleInstanceUptime(long minSingleInstanceUptime) {
      this.minSingleInstanceUptime = minSingleInstanceUptime;
   }

   /**
    * The failure detection timeout, in milliseconds, used by the cluster to decide that an
    * unresponsive node has failed. If not set, the Ignite default (10 seconds) is used.
    * <p>
    * Raising this value lets the cluster ride out longer node pauses, but it also raises the
    * threshold for reporting blocked system workers and lengthens the time that a node that has
    * really died blocks cache operations that wait for all nodes (FULL_SYNC). That time can
    * exceed the cluster health check timeout ({@code health.cluster.timeout}).
    * <p>
    * The value is in milliseconds (for example {@code 20000} for 20 seconds), not seconds. It does
    * not change how long servers wait before failing a client node such as a cloud runner job,
    * which stays at the Ignite default of 30 seconds, and the connection recovery timeout stays at
    * 10 seconds.
    * <p>
    * Every node in the cluster must be set to the same value. Ignite does not check that the
    * value matches when a node joins, so a mismatch is not reported.
    *
    * @return the failure detection timeout in milliseconds or {@code null} if not set.
    */
   public Long getFailureDetectionTimeout() {
      return failureDetectionTimeout;
   }

   public void setFailureDetectionTimeout(Long failureDetectionTimeout) {
      this.failureDetectionTimeout = failureDetectionTimeout;
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
   private int fileTransferPort = 0;
   private boolean multicastEnabled = true;
   private String multicastAddress = "224.2.2.3";
   private int multicastPort = 54327;
   private IpFinderConfig ipFinder;
   private boolean tcpEnabled = false;
   private boolean singleNode = false;
   private String[] tcpMembers;
   private boolean clientMode = false;
   private String caKeyFile = null;
   @CRDProperty(description = "The PEM-encoded CA key used to generate SSL keys for the nodes", secret = true)
   private String caKey = null;
   @CRDProperty(description = "The password for the CA key", secret = true)
   private String caKeyPassword = null;
   private String caCertificateFile = null;
   @CRDProperty(description = "The PEM-encoded CA certificate used to generate SSL keys for the nodes", secret = true)
   private String caCertificate = null;
   @CRDProperty(select = {
      @CRDProperty.Select(field = "labelName", name = "discoveryLabelName", description = "The name of the label that identifies pods that contain a server or scheduler instance"),
      @CRDProperty.Select(field = "labelValue", name = "discoveryLabelValue", description = "The value of the label that identifies the pods that contain a server or scheduler instance")
   })
   private KubernetesConfig k8s;
   private int minNodes = -1;
   private long minSingleInstanceUptime = -1;
   private Long failureDetectionTimeout;
   private boolean minorityIslandHalt = false;
}
