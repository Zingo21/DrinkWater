#!/usr/bin/env bash
# Installs and starts the DrinkWater server on an Ubuntu or Debian machine, see "Hosting the server" in README.md:
#
#   curl -fsSL https://raw.githubusercontent.com/Zingo21/DrinkWater/main/setup.sh | bash
#
# Run it as your own user, not root; it uses sudo where it must. It sets up ~/drinkwater with compose.yaml, a .env
# and update.sh, then starts the server and the Cloudflare tunnel from the image GitHub builds. Running it again is
# safe: it keeps .env and the database, and fetches the newest compose.yaml.
#
# It asks for what it needs. To run it without questions, set them in the environment instead:
#   TUNNEL_TOKEN        the Cloudflare tunnel's token (required the first time)
#   WEB_PUSH_SUBJECT    mailto: address for web push (optional, notifications are off without it)
#   AUTO_UPDATE         yes or no: install new releases within five minutes
set -euo pipefail

REPO="${DRINKWATER_REPO:-Zingo21/DrinkWater}"
# compose.yaml comes from this branch; another one is only for trying out changes to the setup.
BRANCH="${DRINKWATER_BRANCH:-main}"
DIR="$HOME/drinkwater"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# Questions come from the terminal even when the script itself arrives through a pipe.
ask() {
    local prompt="$1" answer=""
    if [ -r /dev/tty ]; then read -r -p "$prompt" answer </dev/tty || true; fi
    printf '%s' "$answer"
}

if [ "$(id -u)" -eq 0 ]; then
    echo "Run this as your own user, not as root: it puts the server in that user's home directory." >&2
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    say "Installing Docker"
    curl -fsSL https://get.docker.com | sudo sh
fi
# Group membership only applies from the next login, so this script itself uses sudo.
me="$(id -un)"
if ! id -nG "$me" | grep -qw docker; then
    sudo usermod -aG docker "$me"
    echo "Added $me to the docker group; log out and in again to use docker without sudo."
fi

mkdir -p "$DIR"
cd "$DIR"

say "Fetching compose.yaml"
curl -fsSL "https://raw.githubusercontent.com/$REPO/$BRANCH/compose.yaml" -o compose.yaml.new
mv compose.yaml.new compose.yaml

if [ ! -f .env ]; then
    say "Creating .env"
    token="${TUNNEL_TOKEN:-}"
    while [ -z "$token" ]; do
        token="$(ask "Cloudflare tunnel token (Zero Trust -> Networks -> Tunnels): ")"
        if [ -z "$token" ] && [ ! -r /dev/tty ]; then
            echo "Set TUNNEL_TOKEN to run without a terminal." >&2
            exit 1
        fi
    done
    subject="${WEB_PUSH_SUBJECT-$(ask "E-mail address for web push, or empty to leave notifications off: ")}"
    if [ -n "$subject" ] && [[ "$subject" != mailto:* ]]; then subject="mailto:$subject"; fi

    umask 077
    {
        echo "# Keep JWT_SECRET: changing it logs everyone out."
        echo "JWT_SECRET=$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
        echo "TUNNEL_TOKEN=$token"
        if [ -n "$subject" ]; then echo "WEB_PUSH_SUBJECT=$subject"; fi
    } > .env
    umask 022
else
    echo "Keeping the existing .env."
fi

cat > update.sh <<'EOF'
#!/usr/bin/env bash
# Installs the newest DrinkWater image and restarts what changed. Waits until the server passes its health check
# and fails if it doesn't; `docker compose logs server` then says why.
set -euo pipefail
cd "$(dirname "$0")"
docker compose pull --quiet
docker compose up -d --wait --wait-timeout 120 --remove-orphans
# Each update leaves the previous image behind.
docker image prune -f >/dev/null
EOF
chmod +x update.sh

auto="${AUTO_UPDATE:-$(ask "Install new releases automatically, within five minutes of publishing one? [y/N] ")}"
if [[ "$auto" =~ ^([yY]|yes|YES|j|ja)$ ]]; then
    say "Turning on automatic updates"
    sudo tee /etc/systemd/system/drinkwater-update.service >/dev/null <<EOF
[Unit]
Description=Install the newest DrinkWater server
Wants=network-online.target
After=network-online.target docker.service

[Service]
Type=oneshot
WorkingDirectory=$DIR
ExecStart=$DIR/update.sh
EOF
    sudo tee /etc/systemd/system/drinkwater-update.timer >/dev/null <<'EOF'
[Unit]
Description=Check for a new DrinkWater server every five minutes

[Timer]
OnBootSec=2min
OnUnitActiveSec=5min

[Install]
WantedBy=timers.target
EOF
    sudo systemctl daemon-reload
    sudo systemctl enable --now drinkwater-update.timer
elif [ -f /etc/systemd/system/drinkwater-update.timer ]; then
    echo "Automatic updates stay on (sudo systemctl disable --now drinkwater-update.timer turns them off)."
fi

say "Starting the server"
sudo ./update.sh

say "Done"
echo "The server runs in $DIR. Check https://<your hostname>/health; it should say OK."
echo "To update by hand later: $DIR/update.sh"
