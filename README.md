# StyleBI Community Edition

StyleBI is an easy to use interactive dashboard software application that includes real time reporting capabilities. It focuses on business data monitoring and exploration by combining a data intelligence platform with visualization technology, serving both enterprises and solution providers.

At the core of the data intelligence platform is InetSoft's Data Block&trade; technology which enables data mashup in a Lego-like block fashion. IT creates performance tuned and security-controlled data blocks that can be transformed and assembled by business users for real-time business questions.

Casual business or consumer-type users get maximum self-service via personalizable, intuitive point-and-click visual access to information. Power users and data scientists get the ability to work with whatever data they need without relying on IT.

## Licensing

StyleBI Community Edition is licensed under the Affero GNU General Public License, Version 3. Please refer to the [LICENSE](./LICENSE) file available in this repository for further details.

## Enterprise Edition

> Coming Soon

InetSoft also provides a commercial Enterprise version of StyleBI. It includes advanced security options, cloud optimization, support for Kubernetes, clustering with elastic scaling, professional customer support and more.

## Quickstart

You will need Docker installed with a version 1.29.0 or later of Docker Compose.

Copy the [community-examples/docker-compose.yaml](community-examples/docker-compose.yaml) file from this repository to the Docker host machine.

In the directory containing the Docker Compose file, run the following command:

```shell
docker compose up -d
docker compose logs -f server
```

Once the server has started, you can press `Ctrl-C` to stop tailing the log. Open http://localhost:8080 in your browser to access the application. The Enterprise Manager can be accessed with the initial username "admin" and password "admin".

## Configuration

The server and scheduler services share a common, top-level configuration in `/var/lib/inetsoft/config/inetsoft.yaml`. You can create this file and mount it in the service containers at this path, or you can define the properties in environment variables.

Additionally, the following environment variables are used:

`INETSOFT_MASTER_PASSWORD`
: This environment variable should be set on the storage, server, and scheduler containers. This variable contains a password that is used to create a PBKDF2 encryption key for storing sensitive information.

`INETSOFT_ADMIN_PASSWORD`
: This environment variable may be set on the storage container to set the initial administrator password.

### YAML Configuration

The `/var/lib/inetsoft/config/inetsoft.yaml` file contains the static configuration shared by all containers that is required to start any of the services.

> There are more configuration settings than are documented here, but they are only used for Enterprise features.

An example configuration file can be found at [community-examples/inetsoft.yaml](./community-examples/inetsoft.yaml) in this repository. This contains the configuration equivalent to the environment variables in the [sample Docker compose file](./community-examples/docker-compose.yaml).

#### Top Level Properties

There is one top-level property, `pluginDirectory`, that specifies the path to the local directory where plugins will be unpacked. The location is arbitrary, but it is recommended to be `/var/lib/inetsoft/plugins`. This can be left in the containers' ephemeral storage since it is just a local cache.

```yaml
pluginDirectory: "/var/lib/inetsoft/plugins"
```

#### Cluster Properties

These properties are used to configure the internal Apache Ignite cluster used by the application services. For most cases, the default values are sufficient.

```yaml
cluster:
  portNumber: 5701
  outboundPortNumber: 0
  multicastEnabled: true
  multicastAddress: "224.2.2.3"
  multicastPort: 54327
  tcpEnabled: false
  tcpMembers:
    - "server:5701"
    - "scheduler:5701"
  caKeyFile: "/var/lib/inetsoft/certs/root.key"
  caKey: |
    -----BEGIN ENCRYPTED PRIVATE KEY-----
    ...
    -----END ENCRYPTED PRIVATE KEY-----
  caCertificateFile: "/var/lib/inetsoft/certs/root.crt"
  caKeyPassword: "password"
  caCertificate: |
    -----BEGIN CERTIFICATE-----
    ...
    -----END CERTIFICATE-----
```

Property | Description | Required | Default Value
--- | --- |----------| ---
`portNumber` | The default port number to which the Ignite cluster will be bound. This port should not be exposed. In most cases, this can be left as the default value. | No | `5701`
`outboundPortNumber` | The outbuild port number that the Ignite cluster will be buiild. If set to 0, any available user port will be used. This port should not be exposed. In most cases, this can be left as the default value. | No | `0`
`multicastEnabled` | A flag that indicates if multicast discovery is enabled. In most cases, multicast discovery is sufficient. | No | `true`
`multicastAddress` | The multicast broadcast address used for discovery. | No | `224.2.2.3`
`multicastPort` | The multicast broadcast port number used for discovery. | No | `54327`
`tcpEnabled` | A flag that determines if TCP discovery is enabled. This mode should only be necessary when multicast discovery is not possible. | No | `false`
`tcpMembers` | The hostnames or IP addresses and port numbers of the TCP discovery members. | No | None
`caKeyFile` | The path to the root CA private key file. | No | None
`caKey` | The contents of the root CA private key. This may be used instead of the `caKeyFile` property. If this property is specified using an environment variable, it will be encrypted using the master password PBKDF2 key if saved to disk. | No | None
`caKeyPassword` | The password for the root CA private key. If this property is specifed using an environment variable, it will be encrypted using the master password PBKDF2 key if saved to disk. | No | None
`caCertificateFile` | The path to the root CA certificate file. This is used to generate SSL certificates for the cluster nodes and clients. | No | None
`caCertificate` | The contents of the root CA certificate file. This may be used instead of the `caKeyFile` property. | No | None

To enable SSL in the Ignite cluster, the root CA private key, private key password, and certificate must be supplied to all containers. If not specified, SSL will not be used.

#### Key-Value Store Properties

These properties are used to configure the key-value store. The Community edition only supports using the MapDB implementation.

```yaml
keyValue:
  type: "mapdb"
  mapdb:
    directory: "/var/lib/inetsoft/kv"
```

Property | Description | Required | Default Value
--- | --- | --- | ---
`type` | The type of key-value storage being used. This must be set to `mapdb` in the Community edition. | Yes | None
`mapdb.directory` | The path to the directory in which the MapDB data files are stored. This *must* be mounted as a volume shared between all containers. The path is arbitrary, but it is recommended to use `/var/lib/inetsoft/kv`. | Yes | None

#### Blob Store Properties

These properties are used to configure the blob store. The Community edition only supports the shared filesystem implementation.

```yaml
blob:
  type: "filesystem"
  cacheDirectory: "/var/lib/inetsoft/blob-cache"
  filesystem:
    directory: "/var/lib/inetsoft/blob"
```

Property | Description | Required | Default Value
--- | --- | --- | ---
`type` | The type of blob storage being used. This must be set to `filesystem` in the Community edition. | Yes | None
`cacheDirectory` | The path to the local blob cache directory. It is arbitrary, but it is recommended to use `/var/lib/inetsoft/blob-cache`. This does not need to be mount and can be left in the container's ephemeral storage. | Yes | None
`filesystem.directory` | The path to the directory containing the blob data. This *must* be mounted as a volume shared between all containers. The path is arbitrary, but it is recommended to use `/var/lib/inetsoft/blob`. | Yes | None

#### External Storage Configuration

These properties are used to configure the external storage where backups and files created by scheduled tasks will be saved. The Community edition only supports the shared filesystem implementation.

```yaml
externalStorage:
  type: "filesystem"
  filesystem:
    directory: "/var/lib/inetsoft/files"
```

Property | Description | Required | Default Value
--- | --- | --- | ---
`type` | The type of external storage being used. The Community edition only supports the shard filesystem implementation. | Yes | None
`filesystem.directory` | The path to the directory where the files will be saved. This *must* be mounted as a volume shared between all containers. The path is arbitrary, but it is recommended to use `/var/lib/inetsoft/files`. | Yes | None

### Environment Variable Configuration

Any of the settings from the YAML configuration file may also be defined using environment variables. If a property is set in both an environment variable and the YAML file, the environment variable takes precedence.

The environment variables must be prefixed with `INETSOFTCONFIG_` with each property name in the path in upper case, separated by underscores. List properties should be specified as a single, comma-separated string.

For example, the following properties:

```yaml
pluginDirectory: "/var/lib/inetsoft/plugins"
cluster:
  tcpMembers:
    - "server:5701"
    - "scheduler:5701"
keyValue:
  mapdb:
    directory: "/var/lib/inetsoft/kv"
```

can be set with these environment variables:

| Name                                      | Value                        |
|-------------------------------------------|------------------------------|
| `INETSOFTCONFIG_PLUGINDIRECTORY`          | `/var/lib/inetsoft/plugins`  |
| `INETSOFTCONFIG_KEYVALUE_MAPDB_DIRECTORY` | `/var/lib/inetsoft/kv`       |
| `INETSOFTCONFIG_CLUSTER_TCPMEMBERS`       | `server:5701,scheduler:5701` |

### Application Properties

Application properties that are not required when the services are started are typically configured in the Enterprise Manager. The underlying properties can be seen and edited directly in the Enterprise manager as well.

Initial values for any of these properties can be set using environment variables on the storage container. The environment variables must be prefixed with `INETSOFTENV_`, be in all upper case, and the dots (.) replaced with underscores.

For example, the `mail.smtp.host` property can be set with the `INETSOFTENV_MAIL_SMTP_HOST` environment variable.

## Docker Containers

There is one Docker image used for three containers. Each type of container uses a different command.

### Storage Initialization Container

This container is run once when you are starting the application for the first time. It sets up the underlying storage for use with the application.

The container must be started with a [configuration file](#yaml-configuration) mounted at `/var/lib/inetsoft/config/inetsoft.yaml` or have the equivalent configuration contained in [environment variables](#environment-variable-configuration). It should also have the `INETSOFT_MASTER_PASSWORD` environment variable set to some secret token shared with the other containers.

The storage container also sets the initial administrator password if run with the `INETSOFT_ADMIN_PASSWORD` environment variable.

This container will set the initial value of application properties specified in the environment variables. See the [Application Properties](#application-properties) section for details.

For an example, see the `storage` service in the [example Docker Compose file](./community-examples/docker-compose.yaml).

### Server Container

This container runs the main application server. It should only be started after the [storage initialization container](#storage-initialization-container) has finished running.

The container must be started with a [configuration file](#yaml-configuration) mounted at `/var/lib/inetsoft/config/inetsoft.yaml` or have the equivalent configuration contained in [environment variables](#environment-variable-configuration). It should also have the `INETSOFT_MASTER_PASSWORD` environment variable set to some secret token shared with the other containers.

For an example, see the `server` service in the [example Docker Compose file](./community-examples/docker-compose.yaml).

### Scheduler Container

THis container runs the scheduler that handles the execution of scheduled tasks defined in the application server. It should only be started after the [storage initialization container](#storage-initialization-container) has finished running.

The container must be started with a [configuration file](#yaml-configuration) mounted at `/var/lib/inetsoft/config/inetsoft.yaml` or have the equivalent configuration contained in [environment variables](#environment-variable-configuration). It should also have the `INETSOFT_MASTER_PASSWORD` environment variable set to some secret token shared with the other containers.

For an example, see the `scheduler` service in the [example Docker Compose file](./community-examples/docker-compose.yaml).

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

* [InetSoft Website](http://www.inetsoft.com/)
* [Product Documentation](https://www.inetsoft.com/wiki/bin/view/Main/ProductDocumentation)
* [@InetSoftTech on Twitter](https://x.com/InetSoftTech)
