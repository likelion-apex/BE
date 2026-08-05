#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <release-sha> <uploaded-jar>" >&2
  exit 64
fi

release_sha="$1"
uploaded_jar="$2"
base_dir="/srv/mutsa"
releases_dir="$base_dir/releases"
current_link="$base_dir/current"
release_dir="$releases_dir/$release_sha"
previous_target="$(readlink -f "$current_link" 2>/dev/null || true)"

if [[ ! "$release_sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "invalid release SHA" >&2
  exit 65
fi

if [[ "$uploaded_jar" != "$base_dir/incoming/$release_sha.jar" || ! -f "$uploaded_jar" ]]; then
  echo "uploaded artifact is missing or outside the incoming directory" >&2
  exit 66
fi

install -d -m 2750 "$release_dir"
install -m 0640 "$uploaded_jar" "$release_dir/app.jar"
ln -sfn "$release_dir" "$base_dir/current.next"
mv -Tf "$base_dir/current.next" "$current_link"

if ! sudo /usr/bin/systemctl restart mutsa.service; then
  echo "systemd restart failed" >&2
  health_ok=false
else
  health_ok=false
  for _ in {1..30}; do
    if curl --silent --show-error --output /dev/null --connect-timeout 2 --max-time 5 \
      http://127.0.0.1:8082/; then
      health_ok=true
      break
    fi
    sleep 2
  done
fi

if [[ "$health_ok" != true ]]; then
  echo "new release failed its local HTTP check; rolling back" >&2
  if [[ -n "$previous_target" && "$previous_target" == "$releases_dir/"* && -d "$previous_target" ]]; then
    ln -sfn "$previous_target" "$base_dir/current.next"
    mv -Tf "$base_dir/current.next" "$current_link"
    sudo /usr/bin/systemctl restart mutsa.service
  else
    sudo /usr/bin/systemctl stop mutsa.service
  fi
  sudo /usr/bin/systemctl status mutsa.service --no-pager >&2 || true
  exit 1
fi

rm -f -- "$uploaded_jar" "$base_dir/incoming/deploy-$release_sha.sh"

mapfile -t old_releases < <(
  find "$releases_dir" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
    | sort -nr \
    | cut -d' ' -f2-
)

for ((index = 5; index < ${#old_releases[@]}; index++)); do
  candidate="${old_releases[$index]}"
  active_target="$(readlink -f "$current_link")"
  if [[ "$candidate" == "$releases_dir/"* && "$candidate" != "$active_target" ]]; then
    rm -rf -- "$candidate"
  fi
done

echo "deployed $release_sha"
