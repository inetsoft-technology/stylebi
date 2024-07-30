# StyleBI 

StyleBI is an easy to use interactive dashboard software application that includes real time reporting capabilities. It focuses on business data monitoring and exploration by combining a data intelligence platform with visualization technology, serving both enterprises and solution providers.

At the core of the data intelligence platform is InetSoft's Data Block&trade; technology which enables data mashup in a Lego-like block fashion. IT creates performance tuned and security-controlled data blocks that can be transformed and assembled by business users for real-time business questions.

Casual business or consumer-type users get maximum self-service via personalizable, intuitive point-and-click visual access to information. Power users and data scientists get the ability to work with whatever data they need without relying on IT.

## Quickstart

You will need Docker installed with a version 1.29.0 or later of Docker Compose. 

If you are using Docker Desktop, Docker Compose is already included. To install Docker Desktop, you can download from [Docker Desktop website](https://www.docker.com/products/docker-desktop/)

Right click this [.yaml file link](community-examples/docker-compose.yaml) and save it to any directory in your mahcine

For Docker Desktop, start it first then open a Command Prompt window. In the directory containing the .yaml file, run the following command:

```shell
docker compose up -d
docker compose logs -f server
```

The last command will start the server. Once the server has started, you can press `Ctrl-C` to stop tailing the log. Open http://localhost:8080 in your browser to access the application. The Enterprise Manager can be accessed with the initial username "admin" and password "admin".  See the [Style BI documentation](https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/) for complete information on how to manage the server and how to create dashboards.

## Build from Source

> **NOTE** This repository contains submodule references that are not publicly available. These are for the commercial Enterprise version of the software and are not required to build the Community version of StyleBI.

### Prerequisites

You will need a GitHub account and a [classic personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) in order to access the Maven repository. You do not need any special permissions to access it, but GitHub [requires](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#installing-a-package) an account to access public Package repositories.

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

### Building the Java Libraries

Build the software by running the following command in the directory where you cloned this repository:

```shell
./mvnw clean install
```

or on Windows:

```powershell
.\mvnw.cmd clean install
```

### Building the Docker Images

In the `docker/community/target/docker` directory, run:

```shell
docker buildx bake
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
