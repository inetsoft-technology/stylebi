# Enterprise Development Environment Instructions

> This file contains instructions for working on the Enterprise version of StyleBI.
> It is only intended for use by InetSoft employees. Users of the open source
> Community version should follow the instructions in the [README.md](./README.md)
> file.

---

**IMPORTANT** This requires Java 21. Make sure that it is installed and that
you have the `JAVA_HOME` environment variable set correctly.

## Initial Setup

If you are viewing this file directly on the Git server, to check out this project:

* Create a new project from version control in Intellij or run `git clone https://USERNAME@repo.inetsoft.com/stylebi/stylebi --recurse-modules`
* In the project root directory, run `git submodule foreach git switch master`

Make sure you have a [GitHub account](https://github.com/signup). Create a [classic personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens).

Create the `~/.m2/settings.xml` file with the following, replacing `YOUR_GITHUB_UESRNAME` and `YOUR_GITHUB_ACCESS_TOKEN` with your GitHub username and access token, respectively:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<settings>
    <!--
     This should be a path on your computer that is short, otherwise
     you'll get a path length error.
     -->
    <localRepository>D:/m2</localRepository>
    <servers>
        <server>
            <id>gh-stylebi</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_ACCESS_TOKEN</password>
        </server>
    </servers>
</settings>
```

Make sure you set the `localRepository` property to a _short_ path on your
machine. Do not leave this as the default. This is only really necessary on
Windows.

If you did not clone the repository with the `--recurse-submodules` or by creating a project from VCS in Intellij, you'll need to run the following:

```shell
git submodule init
git submodule update --checkout
```

After you have cloned the repository and initialized the submodules, make sure the submodules are tracking the master branch:

```shell
git submodule foreach git switch master
```

### Building

The first time you build the project, run the following:

```
.\mvnw.cmd install -DskipTests "-Pcommunity,enterprise"
```

To perform a clean rebuild, run the following:

```
.\mvnw.cmd clean install -DskipTests "-Pcommunity,enterprise"
```

To perform an incremental compile of just the core Java source for use
in the local development server, run:

```
.\mvnw.cmd compile -pl core -DskipTests
```

To just compile or watch the Angular code, use the NPM commands in the
`web` directory.

To rebuild one of the other modules, such as one under the `plugins` or
`tabular` directories, run:

```
.\mvnw.cmd clean install -pl plugins/inetsoft-xml-formats -am -DskipTests "-Pcommunity,enterprise"
```

Note that this will require a server restart if you currently have it
running.

You can run the build in parallel with the `-T 1C` option. This will allow up
to 1 thread per core. This is mostly useful for the `clean install` goal and
may lead to a significant improvement in build speed.

### Running the Server

To start the server, run the following:

```
.\mvnw.cmd -o spring-boot:run -pl server -DskipTests "-Pcommunity,enterprise"
```

To use AWS storage:

1. Make sure the server is not running.
2. Delete the `server/target/server` directory.
3. Run `docker compose up -d` in the `server/src/test/docker/aws` directory.
4. Run `.\mvnw.cmd -o generate-test-resources -pl server "-PawsStorage,community,enterprise" -DskipTests`

To use Google storage:

1. Make sure the server is not running.
2. Delete the `server/target/server` directory.
3. Run `docker compose up -d` in the `server/src/test/docker/google` directory.
4. Run `.\mvnw.cmd -o generate-test-resources -pl server "-PgoogleStorage,community,enterprise" -DskipTests`

### Shell

To run the shell, use this command on Windows:

```
.\shell\target\shell\bin\inetsoft-shell.cmd
```

or on Linux:

```
shell/target/shell/bin/inetsoft-shell.sh
```

### DSL

To run a DSL script, use this command on Windows:

```
.\shell\target\shell\bin\run-script.cmd PATH_TO_DSL
```

or on Linux:

```
shell/target/shell/bin/run-script.sh PATH_TO_DSL
```

### Cloud runners
#### Kubernetes
1. Launch a kubernetes cluster
   * For quick testing, you can start a kubernetes cluster in docker desktop
   * Download and install Lens ide to connect to the kubernetes cluster and check up on job execution

2. Configure inetsoft.yaml or docker-compose.yaml 
   * ```inetsoft-kubernetes/src/test/resources/cloudrunner/inetsoft.yaml```
   * See ```KubernetesCloudRunnerConfig``` for all settings
   * The example inetsoft.yaml uses localstack for key value and blob storage but a file based storage using a shared volume should work too.
   * When starting docker containers for the server and scheduler, make sure to set the following environment variables:
     * INETSOFT_HOST_IP - set it to the ip of the host machine
     * INETSOFT_HOST_PORT - host port for node discovery. Needs to match the value set in the port mapping, e.g. if port mapping is 5702:5701 then the value of this variable should be 5702
     * INETSOFT_HOST_OUTBOUND_PORT - host port for node communication. Needs to match the value set in the port mapping, e.g. if port mapping is 47101:47100 then the value of this variable should be 47101

3. Start inetsoft server
   * For dev purposes, the inetsoft server can be started outside the cluster.

4. Run the task
   * Open EM -> Schedule and run a schedule task.
   * In the Lens ide, open Workloads -> Jobs. Anytime you run a task a new job will be created here.


#### Fargate (AWS)
1. Set up aws services
   * Create access key. Go to aws console, click on profile -> Security Credentials -> Create access key.
   * Create an ECS cluster.
   * Create an EC2 instance for the inetsoft server and note down its subnet and security group.
   * Create an ECR to store the docker images. Create docker images and push them to the ECR.

2. Configure inetsoft.yaml or docker-compose.yaml
   * ```inetsoft-integration-aws/src/test/resources/cloudrunner/inetsoft.yaml```
   * Set dynamodb for the key value storage. Table is automatically created if it doesn't exist.
   * Set s3 for the blob storage. Bucket is automatically created if it doesn't exist.
   * Set fargate for the cloud runner. Specify executionRoleArn if fargate task is pulling the docker image from a private ECR. Use the same vpcSubnets and vpcSecurityGroups as the EC2 instance that the inetsoft server runs on so that the runner can connect to the cluster and notify of its status. See ```FargateCloudRunnerConfig``` for more settings.
   * When starting docker containers for the server and scheduler, make sure to set the following environment variables:
     * INETSOFT_HOST_IP - set it to the internal ip of the EC2 instance
     * INETSOFT_HOST_PORT - host port for node discovery. Needs to match the value set in the port mapping, e.g. if port mapping is 5702:5701 then the value of this variable should be 5702
     * INETSOFT_HOST_OUTBOUND_PORT - host port for node communication. Needs to match the value set in the port mapping, e.g. if port mapping is 47101:47100 then the value of this variable should be 47101

3. Start inetsoft server on EC2
   * Enable ssh access to EC2. Install docker and aws configure. Login to docker and pull the inetsoft server docker image. https://docs.aws.amazon.com/AmazonECR/latest/userguide/docker-push-ecr-image.html
   * Start the scheduler.

4. Run the task
  * Open EM -> Schedule and run a schedule task.
  * In the aws console, ECS -> inetsoft_runner_cluster -> Tasks, you should see a fargate task be created.

#### Azure
1. Set up Azure services
  * Sign in with Azure CLI to set up some of the services that can't be done with the Azure portal.
  * Create a resource group, blob storage account, cosmos db, and container registry
  * Create a service principal for authentication. https://learn.microsoft.com/en-us/azure/developer/java/sdk/identity-service-principal-auth
    * ``` az ad sp create-for-rbac --name PRINCIPAL_NAME --role Contributor --scopes /subscriptions/SUBCSRIPTION_ID ```
    * Use appId as clientId and password as clientSecret in the runner config
  * Create a virtual network. Add a subnet and in the subnet config make the following changes:
    * Service Endpoints -> Services -> Select Microsoft.ContainerRegistry
    * Subnet Delegation -> Delegate subnet to a service -> Select Microsoft.App/environments
  * Create a Container Apps Environment
    * Get the subnet id created above
      * ```az network vnet subnet show --resource-group RESOURCE_GROUP --vnet-name VIRTUAL_NETWORK_NAME --name SUBNET_NAME --query "id" -o tsv```
    * Run the following command to create the environment
      * az containerapp env create -n APP_ENV_NAME -g RESOURCE_GROUP --location eastus --infrastructure-subnet-resource-id SUBNET_ID
      
2. Configure inetsoft.yaml or docker-compose.yaml
  * ```inetsoft-integration-azure/src/test/resources/cloudrunner/inetsoft.yaml```
  * See ```AzureCloudRunnerConfig``` for all settings
  * When starting docker containers for the server and scheduler, make sure to set the following environment variables:
    * INETSOFT_HOST_IP - set it to the internal ip of the Azure VM
    * INETSOFT_HOST_PORT - host port for node discovery. Needs to match the value set in the port mapping, e.g. if port mapping is 5702:5701 then the value of this variable should be 5702
    * INETSOFT_HOST_OUTBOUND_PORT - host port for node communication. Needs to match the value set in the port mapping, e.g. if port mapping is 47101:47100 then the value of this variable should be 47101

3. Start inetsoft server on a VM in Azure
  * Use the virtual network created earlier for this VM so that the runner shares the same network.
  * Enable ssh access to the VM. Install docker and pull the inetsoft server docker image.
  * Start the scheduler.

4. Run the task
   * Open EM -> Schedule and run a schedule task.
   * In the Azure portal -> Container App Jobs, you should see a job be created.

#### Google
1. Set up google services
  * Create Cloud Storage for the blob storage and Firestore for the key value storage.
  * Create a virtual network and note down the name and the subnet as it will be used for the VM and the runner
  * Create a service account and give it permissions to access google cloud resources
    * Create a key and download the json file
    * Base64 encode the json and set serviceAccountJson in inetsoft.yaml

2. Configure inetsoft.yaml or docker-compose.yaml
   * ```inetsoft-integration-google/src/test/resources/cloudrunner/inetsoft.yaml```
   * ```inetsoft-integration-google/src/test/resources/cloudrunner/docker-compose.yaml```
   * See ```GoogleCloudRunnerConfig``` for all settings
   * When starting docker containers for the server and scheduler, make sure to set the following environment variables (see docker-compose.yaml).
     * INETSOFT_HOST_IP - set it to the internal ip of the Google cloud VM
     * INETSOFT_HOST_PORT - host port for node discovery. Needs to match the value set in the port mapping, e.g. if port mapping is 5702:5701 then the value of this variable should be 5702
     * INETSOFT_HOST_OUTBOUND_PORT - host port for node communication. Needs to match the value set in the port mapping, e.g. if port mapping is 47101:47100 then the value of this variable should be 47101

3. Start inetsoft server on a VM in Google
  * Use the VPC network created in Step 1
  * Enable ssh access to the VM. Install docker and pull the inetsoft server docker image.
  * Configure the firewall so that the server is accessible from your ip
  * Start the scheduler

4. Run the task
  * Open EM -> Schedule and run a schedule task.
  * In the Google cloud portal -> Cloud Run Jobs, you should see a job be created.
