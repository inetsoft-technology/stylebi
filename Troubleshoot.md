# Troubleshooting

This file contains a list of tips to help with building a Docker image and deploying StyleBI.

## First-Time Build of Docker Image 

The first time a docker image is built it may require online access to download necessary dependencies. Remove the `-o` command line option in this case.

```.\mvnw clean package -f docker -PdockerImage```

## Clone the StyleBI repository

You can clone the StyleBI Repository  by downloading a zip of the repository, or by running the following command if Git (https://git-scm.com/) is installed.

```git clone https://github.com/inetsoft-technology/stylebi```

See https://docs.github.com/en/repositories/creating-and-managing-repositories/cloning-a-repository for more information.

## Skip Verification Tests

Use the `-DskipTests` command line option  when building the Java libraries to skip the verification tests.  This can speed up the build process.

```./mvnw clean install -DskipTests```

## Force Dependency Download
Use the `-U` command line option  when building the Java libraries to force the download of remote dependencies instead of using cached files.

```./mvnw clean install -U```

