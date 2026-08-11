#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "run this script as root" >&2
  exit 77
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
service_user="mutsa"
deploy_user="mutsa-deploy"

getent group "$service_user" >/dev/null || groupadd --system "$service_user"

if ! id "$service_user" >/dev/null 2>&1; then
  useradd --system --gid "$service_user" --home-dir /nonexistent --shell /usr/sbin/nologin "$service_user"
fi

if ! id "$deploy_user" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "$deploy_user"
fi
usermod --append --groups "$service_user" "$deploy_user"

install -d -o "$deploy_user" -g "$service_user" -m 2750 \
  /srv/mutsa /srv/mutsa/incoming /srv/mutsa/releases
install -d -o root -g "$service_user" -m 0750 /etc/mutsa

if [[ ! -f /etc/mutsa/mutsa.env ]]; then
  install -o root -g "$service_user" -m 0640 "$script_dir/mutsa.env.example" /etc/mutsa/mutsa.env
  echo "created /etc/mutsa/mutsa.env with placeholder values; replace every CHANGE_ME before deployment" >&2
fi

install -o root -g root -m 0644 "$script_dir/mutsa.service" /etc/systemd/system/mutsa.service
install -o root -g root -m 0644 "$script_dir/apache-mutsa.conf" /etc/apache2/sites-available/mutsa.conf

cat >/etc/sudoers.d/mutsa-deploy <<'SUDOERS'
mutsa-deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart mutsa.service
mutsa-deploy ALL=(root) NOPASSWD: /usr/bin/systemctl stop mutsa.service
mutsa-deploy ALL=(root) NOPASSWD: /usr/bin/systemctl status mutsa.service --no-pager
SUDOERS
chmod 0440 /etc/sudoers.d/mutsa-deploy
visudo --check --file=/etc/sudoers.d/mutsa-deploy

systemctl daemon-reload
systemctl enable mutsa.service

echo "server layout installed"
echo "next: configure /etc/mutsa/mutsa.env, MariaDB, and the deploy user's authorized_keys"
echo "enable the Apache site only after the first local deployment passes its health check"
