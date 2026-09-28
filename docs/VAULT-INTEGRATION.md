# Printer Inventory Vault integration

Printer Inventory can start in either Vault mode or the existing environment-password rollback mode. The two Compose files are standalone configurations and must not be combined.

## Requirements

- Vault is initialized, unsealed, and reachable as `central-vault` on the external Docker network `central-vault-applications`.
- KV v2 is mounted at `applications/`.
- `applications/printer-inventory/database` contains the property `spring.datasource.password`.
- The `printer-inventory` AppRole has only the `printer-inventory-read` policy.
- The application server's ignored `.env` contains `VAULT_ROLE_ID` and `VAULT_SECRET_ID`. Restrict this file to the deployment operator, for example with mode `600`.

The backend and Vault share only `central-vault-applications`. The frontend remains on the Printer Inventory Compose network and does not join the Vault network. Vault port 8200 does not need to be exposed to the LAN.

## Verify the shared network

Confirm both containers are attached without printing their environment:

```bash
docker network inspect central-vault-applications
```

Before starting Vault mode, verify Docker DNS and HTTP connectivity using the backend image:

```bash
docker run --rm \
  --network central-vault-applications \
  --entrypoint wget \
  "printer-inventory-backend:${APP_VERSION}" \
  -q -O - http://central-vault:8200/v1/sys/health
```

This endpoint does not require an application credential. A healthy, unsealed Vault returns its health document.

## Deploy in Vault mode

Set a new `APP_VERSION`, build, and validate the standalone configuration. `config --quiet` avoids printing resolved credentials.

```bash
docker compose -f docker-compose.app-server-vault.yml config --quiet
docker compose -f docker-compose.app-server-vault.yml build --pull
docker compose -f docker-compose.app-server-vault.yml up -d --no-build --wait --wait-timeout 180
docker compose -f docker-compose.app-server-vault.yml ps
```

Vault mode activates the `vault` Spring profile and does not provide `SPRING_DATASOURCE_PASSWORD` or `POSTGRES_PASSWORD` to the backend. Spring Cloud Vault authenticates through AppRole and imports only `applications/printer-inventory/database`.

Verify the application without printing container environment variables:

```bash
curl --fail http://10.10.30.182/api/health
docker compose -f docker-compose.app-server-vault.yml logs --tail=100 backend
```

Then verify login, Printer Inventory, User Management, and Audit Trail in the browser. Logs must not contain a Role ID, Secret ID, Vault token, or database password.

## Vault failure behavior

The Vault import is mandatory and fail-fast. A new backend instance will not start if Vault is sealed or unavailable, AppRole authentication fails, the policy denies the configured path, or the secret is missing. A running backend may continue using existing database connections during a temporary Vault outage, but restart recovery depends on Vault being available again.

Do not add an automatic password fallback to the Vault profile. Use the explicit rollback procedure instead.

## Roll back to the environment password

Keep the existing `POSTGRES_PASSWORD` in the protected `.env`. The rollback configuration remains `docker-compose.app-server.yml` and supplies it as `SPRING_DATASOURCE_PASSWORD`.

```bash
docker compose -f docker-compose.app-server.yml config --quiet
docker compose -f docker-compose.app-server.yml up -d --no-build --force-recreate --wait --wait-timeout 180
docker compose -f docker-compose.app-server.yml ps
curl --fail http://10.10.30.182/api/health
```

Recheck login, Printer Inventory, User Management, and Audit Trail after rollback. Do not remove the existing password from `.env` until Vault mode has completed its acceptance period.

## Credential handling

- Never commit `.env`, AppRole credentials, Vault tokens, root tokens, unseal keys, or database passwords.
- Never place credentials in Compose files, Spring properties, Docker images, commands, screenshots, or logs.
- Use the application-specific AppRole and read-only policy; never use a Vault root token in the application.
- Rotate a disclosed Secret ID and revoke any tokens created from it.
