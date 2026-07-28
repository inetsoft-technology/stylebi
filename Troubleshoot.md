# Troubleshooting

This file contains a list of tips to help with building a Docker image and deploying StyleBI.

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

## Storage Container Exits with "exit 1" During `docker compose up`

If the `storage` container fails during initialization with a bare `exit 1` and no other obvious error in the top-level `docker compose` output, check the logs for that specific container:

```shell
docker compose logs storage
```

This error most commonly occurs when the `INETSOFT_ADMIN_PASSWORD` environment variable is missing or does not meet the password requirements. This variable sets the password for the "admin" user and is required — there is no default password. The password must be at least 8 characters and include an uppercase letter, a lowercase letter, a digit, and a special character.

To resolve this, set `INETSOFT_ADMIN_PASSWORD` to a valid password before starting the containers, either in the `docker-compose.yaml` file:

```yaml
storage:
  environment:
    # the password for the "admin" user
    INETSOFT_ADMIN_PASSWORD: "Test@admin1"
```

or as an environment variable in your shell before running `docker compose up`:

```shell
export INETSOFT_ADMIN_PASSWORD="Test@admin1"
```

```powershell
$env:INETSOFT_ADMIN_PASSWORD="Test@admin1"
```

or by uncommenting and setting `INETSOFT_ADMIN_PASSWORD` in the `.env` file included with the community examples.

