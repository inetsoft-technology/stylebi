# Building the Docker Images

## Building the Base Image

You must create a Buildx builder instance. This is step is only required once:

```shell
docker buildx create --use --name multiplatform
```

To build and push the image, run the following command:

```shell
docker buildx build \
  -t ghcr.io/inetsoft-technology/java:21.0.7_6 \
  --platform linux/amd64,linux/arm64 \
  --push \
  src/main/docker
```

In order to push the image, you must have authenticated to `gchr.io` with the `docker login` command
using a Github user that has write permission on the inetsoft-technology repository. If you don't
have this permission, you can build the base image locally by replace the `--push` option with
`--load` in the command above.

## Building the StyleBI Image

From the StyleBI root directory, run the following command:

```shell
./mvnw clean package jib:dockerBuild -pl docker
```
