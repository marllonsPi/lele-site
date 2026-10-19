#!/usr/bin/env bash
# Prepara uma VM Ubuntu limpa (OCI) para o Crispim & Campos: Docker, swap e firewall local.
# Uso (na VM, como ubuntu): bash setup-vm.sh
set -euo pipefail

if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# Swap de 4 GB (builds Maven/Node e VMs de pouca RAM).
if ! swapon --show | grep -q swapfile; then
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# Ubuntu da OCI vem com regras iptables que bloqueiam 80/443: liberar e persistir.
sudo iptables -C INPUT -p tcp --dport 80 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 5 -p tcp --dport 80 -j ACCEPT
sudo iptables -C INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 5 -p tcp --dport 443 -j ACCEPT
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq iptables-persistent >/dev/null
sudo netfilter-persistent save >/dev/null

mkdir -p "$HOME/crispim-campos" "$HOME/backups/crispim"
echo "VM pronta. Saia e entre de novo no SSH para o grupo docker valer."
