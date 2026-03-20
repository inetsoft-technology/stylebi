# Testing a Local Deployment

First build the Docker image by running the following commands:

```
mvnw clean package -o -f docker -PdockerImage
```

Before starting, set the required environment variable in your shell:

```shell
export INETSOFT_ADMIN_PASSWORD="YourStr0ng!Password"
```

The password must be at least 8 characters and include uppercase, lowercase, a digit, and a special character.

Then, in the `docker/target/docker-test` folder, start the server and scheduler:

```
docker compose up -d
```

To stop the services, run:

```
docker compose down --rmi local -v
```
