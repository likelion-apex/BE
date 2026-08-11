# Production deployment

The production service runs the executable JAR from `/srv/mutsa/current` with systemd and exposes it only on port 8082 behind Apache.

## One-time server setup

1. Copy this `deploy` directory to the server and run `sudo ./bootstrap-server.sh`.
2. Replace every `CHANGE_ME` in `/etc/mutsa/mutsa.env` and create the `mutsa` MariaDB database and `mutsa_app` account.
3. Add the GitHub Actions deployment public key to `/home/mutsa-deploy/.ssh/authorized_keys`.
4. Add `PROD_HOST`, `PROD_USER`, `PROD_SSH_PRIVATE_KEY`, and `PROD_SSH_KNOWN_HOSTS` to the GitHub `production` environment.
5. Run the workflow on `main`. After the local 8082 check succeeds, enable the site with `sudo a2ensite mutsa.conf`, validate with `sudo apache2ctl configtest`, and reload Apache.

The server environment file must contain the two exact CORS origins without trailing slashes:

```text
CORS_ALLOWED_ORIGINS=https://likelion-apex-fe.vercel.app,http://localhost:3000
```

Application secrets stay on the server and are not copied into GitHub Actions.
