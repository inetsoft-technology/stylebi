# SSO Testing with Keycloak

Start the Docker Compose project in this directory:

```
docker compose up -d
```

Access the Keycloak administration console at http://localhost:8000/admin. Log in as `admin`
with the password `success123`.

Everything is already configured in Keycloak, so nothing more needs to be done there.

## Server Configuration

In the Enterprise Manager:

1. Enable security
2. Enable multi-tenancy
3. Disable self-registration
4. Create an organization with the ID `org1`
5. Create an organization with the ID `org2`
6. Select OpenID Connect for SSO
7. Enter `http://localhost:8000/realms/inetsoft/.well-known/openid-configuration` for the OIDC discovery URL and click Apply
8. Enter `inetsoft` for the Client ID
9. Enter `gL630EoMebCaTocHTok6QfH9QYMzVNmv` for the client secret
10. Enter `openid`, `email`, `roles`, and `profile` for the scopes
11. Enter `preferred_username` for the name claim
12. Enter `resource_access.inetsoft.roles` for the role claim
13. Enter `organization` for the organization claim
