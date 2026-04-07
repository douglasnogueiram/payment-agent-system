#!/usr/bin/env bash
# ─── GCP infrastructure setup for payment-agent-system ──────────────────────
# Run this ONCE from your local machine (Mac) to provision the VM.
# Prerequisites: gcloud CLI installed and authenticated (gcloud auth login)
#
# Usage: bash gcp-setup.sh
set -euo pipefail

PROJECT=agente-pagamentos
REGION=southamerica-east1
ZONE=southamerica-east1-a
VM_NAME=payment-agent-vm
MACHINE_TYPE=e2-standard-2   # 2 vCPU, 8 GB RAM
DISK_SIZE=60GB
IP_NAME=payment-agent-ip

echo "==> Setting active project to ${PROJECT}"
gcloud config set project "${PROJECT}"

echo "==> Reserving static external IP (${IP_NAME})..."
gcloud compute addresses create "${IP_NAME}" \
  --region="${REGION}" \
  --quiet 2>/dev/null || echo "    (IP already exists, continuing)"

EXTERNAL_IP=$(gcloud compute addresses describe "${IP_NAME}" \
  --region="${REGION}" --format='get(address)')
echo "    Static IP: ${EXTERNAL_IP}"

echo "==> Creating VM (${VM_NAME})..."
gcloud compute instances create "${VM_NAME}" \
  --zone="${ZONE}" \
  --machine-type="${MACHINE_TYPE}" \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size="${DISK_SIZE}" \
  --boot-disk-type=pd-ssd \
  --address="${EXTERNAL_IP}" \
  --tags=payment-agent \
  --quiet 2>/dev/null || echo "    (VM already exists, continuing)"

echo "==> Creating firewall rules..."
gcloud compute firewall-rules create allow-payment-web \
  --direction=INGRESS \
  --priority=1000 \
  --network=default \
  --action=ALLOW \
  --rules=tcp:80,tcp:443 \
  --target-tags=payment-agent \
  --quiet 2>/dev/null || echo "    (firewall rule already exists, continuing)"

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║  NEXT STEPS                                                      ║"
echo "╠══════════════════════════════════════════════════════════════════╣"
echo "║                                                                  ║"
echo "║  1. Configure DNS at Registro.br:                                ║"
echo "║     A record  meuagentepix.com.br  →  ${EXTERNAL_IP}            ║"
echo "║     A record  www                  →  ${EXTERNAL_IP}            ║"
echo "║                                                                  ║"
echo "║  2. Wait ~15 min for DNS propagation, then verify:               ║"
echo "║     dig +short meuagentepix.com.br                               ║"
echo "║                                                                  ║"
echo "║  3. Copy code to VM:                                             ║"
echo "║     gcloud compute scp --recurse . ${VM_NAME}:~/payment-agent-system --zone=${ZONE}"
echo "║                                                                  ║"
echo "║  4. SSH into VM and run deploy.sh:                               ║"
echo "║     gcloud compute ssh ${VM_NAME} --zone=${ZONE}                 ║"
echo "║     cd ~/payment-agent-system && bash deploy.sh                  ║"
echo "║                                                                  ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
