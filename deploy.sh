#!/usr/bin/env bash
# ─── Production deploy script — run this INSIDE the GCP VM ──────────────────
# Usage: bash deploy.sh
set -euo pipefail

DOMAIN=meuagentepix.com.br
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="${APP_DIR}/docker-compose.prod.yml"

# ── 1. Install Docker (idempotent) ───────────────────────────────────────────
if ! command -v docker &>/dev/null; then
  echo "==> Installing Docker..."
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$USER"
  echo "    Docker installed. You may need to log out/in for group to apply."
  echo "    Re-run this script if docker commands fail with permission error."
fi

if ! docker compose version &>/dev/null; then
  echo "==> Installing Docker Compose plugin..."
  sudo apt-get update -y
  sudo apt-get install -y docker-compose-plugin
fi

# ── 2. Install Certbot ───────────────────────────────────────────────────────
if ! command -v certbot &>/dev/null; then
  echo "==> Installing Certbot..."
  sudo apt-get update -y
  sudo apt-get install -y certbot
fi

# ── 3. Validate .env.prod ────────────────────────────────────────────────────
ENV_FILE="${APP_DIR}/.env.prod"
if [[ ! -f "${ENV_FILE}" ]]; then
  echo ""
  echo "ERROR: .env.prod not found."
  echo "Copy .env.prod.example to .env.prod and fill in real values:"
  echo "  cp .env.prod.example .env.prod && nano .env.prod"
  exit 1
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"
if [[ -z "${OPENAI_API_KEY:-}" || "${OPENAI_API_KEY}" == "sk-..." ]]; then
  echo "ERROR: OPENAI_API_KEY is not set in .env.prod"; exit 1
fi
if [[ -z "${DB_PASSWORD:-}" || "${DB_PASSWORD}" == "change_me"* ]]; then
  echo "ERROR: DB_PASSWORD is not set in .env.prod"; exit 1
fi

# ── 4. Build Docker images ───────────────────────────────────────────────────
echo "==> Building Docker images (this may take several minutes)..."
cd "${APP_DIR}"
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" build

# ── 5. Obtain Let's Encrypt certificate ─────────────────────────────────────
CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"
if [[ ! -d "${CERT_DIR}" ]]; then
  echo "==> Obtaining TLS certificate for ${DOMAIN}..."
  echo "    (ports 80 and 443 must be free — make sure no containers are running)"
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" down 2>/dev/null || true

  # Verify DNS points here before asking Let's Encrypt
  RESOLVED=$(dig +short "${DOMAIN}" | tail -1)
  MY_IP=$(curl -s ifconfig.me)
  if [[ "${RESOLVED}" != "${MY_IP}" ]]; then
    echo ""
    echo "WARNING: DNS for ${DOMAIN} resolves to ${RESOLVED}"
    echo "         This server's IP is ${MY_IP}"
    echo "         Certbot will fail if DNS is not pointed here yet."
    read -rp "Continue anyway? (y/N) " yn
    [[ "${yn,,}" == "y" ]] || { echo "Aborted."; exit 1; }
  fi

  sudo certbot certonly --standalone \
    -d "${DOMAIN}" \
    -d "www.${DOMAIN}" \
    --agree-tos \
    --no-eff-email \
    --email "admin@${DOMAIN}"
  echo "    Certificate obtained successfully."
else
  echo "==> TLS certificate already exists, skipping certbot."
fi

# ── 6. Start all services ────────────────────────────────────────────────────
echo "==> Starting all services..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d

echo "==> Waiting for services to become healthy (60s)..."
sleep 60

echo "==> Service status:"
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps

# ── 7. Set up automatic certificate renewal ──────────────────────────────────
CRON_JOB="0 3 * * * certbot renew --quiet --pre-hook 'docker stop payment-frontend' --post-hook 'docker start payment-frontend'"
if ! sudo crontab -l 2>/dev/null | grep -q "certbot renew"; then
  echo "==> Setting up certbot renewal cron (daily at 03:00)..."
  (sudo crontab -l 2>/dev/null || true; echo "${CRON_JOB}") | sudo crontab -
  echo "    Cron job added."
fi

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║  Deployment complete!                                            ║"
echo "╠══════════════════════════════════════════════════════════════════╣"
echo "║  App:      https://${DOMAIN}                  ║"
echo "║  Keycloak: https://${DOMAIN}/kc/admin         ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
