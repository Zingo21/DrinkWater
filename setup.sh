#!/usr/bin/env bash
# Installs and starts the DrinkWater server on an Ubuntu or Debian machine, see "Hosting the server" in README.md:
#
#   curl -fsSL https://raw.githubusercontent.com/Zingo21/DrinkWater/main/setup.sh | bash
#
# Run it as your own user, not root; it uses sudo where it must. It sets up ~/drinkwater with compose.yaml, a .env
# and update.sh, then starts the server from the image GitHub builds, reachable from the internet through either:
#   - a Cloudflare Tunnel: your own domain, needs a Cloudflare account and a domain managed there;
#   - Tailscale Funnel: an https://<machine>.<tailnet>.ts.net address, needs only a Tailscale account.
# Running it again is safe: it keeps .env (and so the choice above) and the database, and fetches the newest
# compose.yaml.
#
# It asks for what it needs. To run it without questions, set them in the environment instead:
#   ACCESS              cloudflare or funnel (the first time)
#   TUNNEL_TOKEN        the Cloudflare tunnel's token (the first time, with cloudflare)
#   TS_AUTHKEY          a Tailscale auth key to log in with (optional, with funnel; otherwise it shows a login link)
#   WEB_PUSH_SUBJECT    mailto: address for web push (optional, notifications are off without it)
#   AUTO_UPDATE         yes or no: install new releases within five minutes
set -euo pipefail

REPO="${DRINKWATER_REPO:-Zingo21/DrinkWater}"
# compose.yaml comes from this branch; another one is only for trying out changes to the setup.
BRANCH="${DRINKWATER_BRANCH:-main}"
DIR="$HOME/drinkwater"

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# Whether there is a terminal to ask on. /dev/tty exists without one too; only opening it tells.
has_terminal() { (exec </dev/tty) 2>/dev/null; }

# Questions come from the terminal even when the script itself arrives through a pipe.
ask() {
    local prompt="$1" answer=""
    if has_terminal; then read -r -p "$prompt" answer </dev/tty || true; fi
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

# For a question that needs an answer: without a terminal to ask on, the variable named $1 has to give it.
need_answer() {
    if ! has_terminal; then
        echo "Set $1 to run without a terminal." >&2
        exit 1
    fi
}

if [ ! -f .env ]; then
    say "Creating .env"
    access="${ACCESS:-}"
    if [[ -n "$access" && "$access" != cloudflare && "$access" != funnel ]]; then
        echo "ACCESS must be cloudflare or funnel." >&2
        exit 1
    fi
    while [ -z "$access" ]; do
        need_answer ACCESS
        echo "How should your friends reach the server?"
        echo "  1) Cloudflare Tunnel: your own domain (needs a Cloudflare account and a domain managed there)"
        echo "  2) Tailscale Funnel: an address like https://drinkwater.tail1234.ts.net (needs only Tailscale)"
        case "$(ask "Choose 1 or 2: ")" in
            1) access=cloudflare ;;
            2) access=funnel ;;
        esac
    done
    token=""
    if [ "$access" = cloudflare ]; then
        token="${TUNNEL_TOKEN:-}"
        while [ -z "$token" ]; do
            need_answer TUNNEL_TOKEN
            token="$(ask "Cloudflare tunnel token (Zero Trust -> Networks -> Tunnels): ")"
        done
    fi
    subject="${WEB_PUSH_SUBJECT-$(ask "E-mail address for web push, or empty to leave notifications off: ")}"
    if [ -n "$subject" ] && [[ "$subject" != mailto:* ]]; then subject="mailto:$subject"; fi

    umask 077
    {
        echo "# Keep JWT_SECRET: changing it logs everyone out."
        echo "JWT_SECRET=$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
        echo "DRINKWATER_ACCESS=$access"
        if [ "$access" = cloudflare ]; then
            echo "COMPOSE_PROFILES=cloudflare"
            echo "TUNNEL_TOKEN=$token"
            echo "TRUSTED_CLIENT_IP_HEADER=CF-Connecting-IP"
        else
            echo "TRUSTED_CLIENT_IP_HEADER=X-Forwarded-For"
        fi
        if [ -n "$subject" ]; then echo "WEB_PUSH_SUBJECT=$subject"; fi
    } > .env
    umask 022
else
    echo "Keeping the existing .env."
    # A .env from before Funnel was an option is a Cloudflare one.
    if ! grep -q '^DRINKWATER_ACCESS=' .env; then
        printf 'DRINKWATER_ACCESS=cloudflare\nCOMPOSE_PROFILES=cloudflare\nTRUSTED_CLIENT_IP_HEADER=CF-Connecting-IP\n' >> .env
    fi
fi
access="$(sed -n 's/^DRINKWATER_ACCESS=//p' .env | tail -1)"

if [ "$access" = funnel ]; then
    if ! command -v tailscale >/dev/null 2>&1; then
        say "Installing Tailscale"
        curl -fsSL https://tailscale.com/install.sh | sudo sh
    fi
    if ! sudo tailscale status >/dev/null 2>&1; then
        say "Logging in to Tailscale"
        if [ -n "${TS_AUTHKEY:-}" ]; then
            sudo tailscale up --authkey "$TS_AUTHKEY"
        else
            echo "Open the link below and log in with your Tailscale account."
            sudo tailscale up
        fi
    fi
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

address="https://<your hostname>"
if [ "$access" = funnel ]; then
    say "Opening it to the internet with Tailscale Funnel"
    # The first time, Tailscale asks to allow Funnel and HTTPS certificates for the tailnet and shows a link for it.
    sudo tailscale funnel --bg 8080
    name="$(sudo tailscale status --json 2>/dev/null | sed -n 's/.*"DNSName": *"\([^"]*\)\.".*/\1/p' | head -1)"
    if [ -n "$name" ]; then address="https://$name"; fi
fi

say "Done"
echo "The server runs in $DIR. Your friends reach it at $address; $address/health should say OK."
echo "To update by hand later: $DIR/update.sh"
