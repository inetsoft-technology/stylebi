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
public class FargateCloudRunnerConfig implements Serializable {
   /**
    * The name of the default AWS region.
    */
   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      this.region = region;
   }

   /**
    * Sets the access key ID for the IAM account.
    */
   public String getAccessKeyId() {
      return accessKeyId;
   }

   public void setAccessKeyId(String accessKeyId) {
      this.accessKeyId = accessKeyId;
   }

   /**
    * The secure access key for the IAM account.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getSecretAccessKey() {
      return secretAccessKey;
   }

   public void setSecretAccessKey(String secretAccessKey) {
      this.secretAccessKey = secretAccessKey;
   }

   /**
    * The uri to the docker image for the schedule task runner
    */
   public String getDockerImageUri() {
      return dockerImageUri;
   }

   public void setDockerImageUri(String dockerImageUri) {
      Objects.requireNonNull(dockerImageUri, "The Fargate Docker image URI is required");
      this.dockerImageUri = dockerImageUri;
   }

   /**
    * Family for a task definition. The family is used as a name for a task definition.
    * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ecs/model/RegisterTaskDefinitionRequest.html#family()
    */
   public String getFamily() {
      return family;
   }

   public void setFamily(String family) {
      this.family = family;
   }

   /**
    * Specify the ECS cluster on which the fargate tasks will be launched. If not specified then
    * the tasks will run on the default ECS cluster.
    */
   public String getCluster() {
      return cluster;
   }

   public void setCluster(String cluster) {
      this.cluster = cluster;
   }

   /**
    * Required when using ECR images.
    * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ecs/model/RegisterTaskDefinitionRequest.html#executionRoleArn()
    */
   public String getExecutionRoleArn() {
      return executionRoleArn;
   }

   public void setExecutionRoleArn(String executionRoleArn) {
      this.executionRoleArn = executionRoleArn;
   }

   /**
    * The name of a container.
    */
   public String getContainerName() {
      return containerName;
   }

   public void setContainerName(String containerName) {
      this.containerName = containerName;
   }

   /**
    * See cpu()
    * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ecs/model/RegisterTaskDefinitionRequest.html#cpu()
    */
   public String getCpu() {
      return cpu;
   }

   public void setCpu(String cpu) {
      this.cpu = cpu;
   }

   /**
    * See memory()
    * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ecs/model/RegisterTaskDefinitionRequest.html#memory()
    */
   public String getMemory() {
      return memory;
   }

   public void setMemory(String memory) {
      this.memory = memory;
   }

   /**
    * The IDs of the subnets associated with the task or service. There's a limit of 16 subnets
    * that can be specified. All specified subnets must be from the same VPC.
    * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ecs/model/AwsVpcConfiguration.html#subnets()
    */
   public String[] getVpcSubnets() {
      return vpcSubnets;
   }

   public void setVpcSubnets(String[] vpcSubnets) {
      Objects.requireNonNull(vpcSubnets, "The Fargate VPC subnets are required");
      this.vpcSubnets = vpcSubnets;
   }

   /**
    * The IDs of the security groups associated with the task or service.
    * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ecs/model/AwsVpcConfiguration.html#securityGroups()
    */
   public String[] getVpcSecurityGroups() {
      return vpcSecurityGroups;
   }

   public void setVpcSecurityGroups(String[] vpcSecurityGroups) {
      this.vpcSecurityGroups = vpcSecurityGroups;
   }

   private String region;
   private String accessKeyId;
   private String secretAccessKey;
   private String dockerImageUri;
   private String family = "inetsoft";
   private String cluster;
   private String executionRoleArn;
   private String containerName = "inetsoft-cloud-runner";
   private String cpu = ".5 vcpu";
   private String memory = "1GB";
   private String[] vpcSubnets;
   private String[] vpcSecurityGroups;
}
