# Testing a Local Deployment

First build the Docker image by running the following commands:

```
mvnw clean package -o -f docker -PdockerImage
```

Then, in the `docker/target/docker-test` folder, start the server and scheduler:

```
docker compose up -d
```

To stop the services, run:

```
docker compose down --rmi local -v
```
