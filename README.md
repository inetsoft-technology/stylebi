# StyleBI Open Source

StyleBI is a cloud-native, small-footprint business intelligence web application, powered by data transformation pipeline and visualization microservices. These microservices can be deployed as standalone Docker containers or seamlessly integrated into a microservice architecture, providing embedded visualization, analytics, and reporting capabilities.

<div style="display: flex;">
<img src="https://test.inetsoft.com/images/website/products/pipeline/vizTranform5.png" alt="Image 1" xwidth="300" style="width:48%;margin-right: 10px;">
<img src="https://test.inetsoft.com/images/website/products/visualization/vizWizard5.png" alt="Image 2" xwidth="300" style="width: 48%">
</div>

_Screenshots of [StyleBI Cloud](https://www.inetsoft.com/company/bi_dashboard_pricing)_.

## Resources & Scaliblity

StyleBI Open Source can be deployed on any Docker engine, including Docker Desktop. For cloud deployments, a minimum capacity of 2 cores and 4GB of RAM is recommended. [StyleBI Enterprise](https://www.inetsoft.com) offers elastic scaling through native cloud servies from AWA, GCP and Azure, built on top of StyleBI Open Source.

## Security

StyleBI Open Source has robust and fine granular security on both data and visual layers. InetSoft also provides a commercial Enterprise version of StyleBI. [StyleBI Enterprise](https://www.inetsoft.com) takes security further with multi-tenant, aduting and more

## Quickstart

You will need Docker installed with a version 1.29.0 or later of Docker Compose. If you are using Docker Desktop, Docker Compose is already included.

Copy the [community-examples/docker-compose.yaml](community-examples/docker-compose.yaml) file from this repository to the Docker host machine.

For Docker Desktop, start it first then open a Command Prompt window. In the directory containing the .yaml file, run the following command:

```shell
docker compose up -d
docker compose logs -f server
```

Once the server has started, you can press `Ctrl-C` to stop tailing the log. Open http://localhost:8080 in your browser to access the application. The Enterprise Manager can be accessed with the initial username "admin" and password "admin". 

### Import Example Datasets
To import example datasets that are useful for learning StyleBI, download the [examples.zip](community-examples/examples.zip) file, and import the assets into your environment.  See [Import Assets](https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/administration/ImportExportAssets.html#ImportAssets)  for instructions on how to do this. See the [StyleBI Documentation](https://www.inetsoft.com/docs/stylebi) for complete information on how to manage the server and how to create Dashboards.

## Build from Source

### Prerequisites

You will need a GitHub account and a [classic personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) in order to access the Maven repository. When creating the classic personal access token, the `read:packages` scope will need to be enabled. You do not need any special permissions to access the Maven repository, but GitHub [requires](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#installing-a-package) an account to access public Package repositories.

Create or update the `~/.m2/settings.xml` file (`C:\Users\USERNAME\.m2\settings.xml` on Windows) with the following contents, replacing `YOUR_GITHUB_USERNAME` and `YOUR_GITHUB_PASSWORD` with your GitHub username and personal access token, respectively:

```xml
<settings>
  <servers>
    <server>
      <id>gh-stylebi</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_ACCESS_TOKEN</password>
    </server>
  </servers>
</settings>
```

You must have the Java 21 SDK installed and the `JAVA_HOME` environment variable pointing to the installation. You may use a Java distribution from any vendor.

You must also have Docker installed.

**_NOTE:_**  Before cloning this repository into a Windows machine, it may be necessary to set the `core.longpaths` property to true `true`. This can be done via the following command in an Administrator command prompt:

```console
git config --system core.longpaths true
```

### Building the Java Libraries

Build the software by running the following command in the directory where you cloned this repository:

```shell
./mvnw clean install
```

or on Windows:

```powershell
.\mvnw.cmd clean install
```

### Building the Docker Image

If you have already built the Java libraries, you can then run the following command:

```shell
./mvnw clean package -o -f docker -PdockerImage
```

or on Windows:

```powershell
.\mvnw.cmd clean package -o -f docker -PdockerImage
```

This will build the Docker image and install it on your machine. You can now use this locally. You can also tag the image and push it to your own repository for use elsewhere, for example:

```shell
docker tag \
  ghcr.io/inetsoft-technology/stylebi-community:latest \
  your.dockerregistry.com/yourname/stylebi:latest
```
```shell
docker push \
  your.dockerregistry.com/yourname/stylebi:latest
```

> **NOTE** You can build the Java libraries and Docker images in a single command using `mvnw clean install -PdockerImage`.

### Running the Local Deployment
To start the server, run the following command in the `docker/target/docker-test` folder:

```docker compose up -d```

Open http://localhost:8080 in your browser to access the application. To stop the server and remove the containers, run:

```docker compose down --rmi local -v```

## Troubleshooting
For assistance with any issues, please see the [Troubleshooting](./Troubleshoot.md) file  in this repository. 

## Contributing

We welcome feedback from our users. We value your bug reports and feature requests.

Before reporting an issue, please check first if a similar issue already exists. Bug reports should be as complete as possible. Please try and include the following:

* Complete steps to reproduce the issue.
* Any information about platform and environment that could be specific to the bug.
* Specific version of the product you are using.

## Additional Resources

* [Advanced Configuration](./community-examples/advanced-configuration.md)
* [InetSoft Website](http://www.inetsoft.com/)
* [@InetSoftTech on Twitter](https://x.com/InetSoftTech)
