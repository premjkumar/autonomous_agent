#!/usr/bin/env bash
set -e

PORT_FLOWISE=3000

echo "=== 1. Checking Docker Installation ==="
if ! command -v docker &> /dev/null; then
    echo "Docker not found. Installing Docker Engine on Fedora..."
    
    # Install dnf plugin core if needed
    sudo dnf install -y dnf-plugins-core || true
    
    # Add official Docker repository
    sudo dnf config-manager addrepo --from-repofile https://download.docker.com/linux/fedora/docker-ce.repo
    
    # Install Docker packages
    sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

echo "=== 2. Ensuring Docker Service is Running ==="
sudo systemctl enable --now docker

echo "=== 3. Cleaning up old Flowise containers/directories ==="
docker rm -f flowise 2>/dev/null || true
if [ -d "Flowise" ]; then
    rm -rf Flowise
fi

echo "=== 4. Launching Flowise via Docker ==="
docker run -d \
  --name flowise \
  -p ${PORT_FLOWISE}:3000 \
  -v ~/.flowise:/root/.flowise \
  --restart unless-stopped \
  flowiseai/flowise

echo "=== 5. Opening Firewall Port ${PORT_FLOWISE} ==="
if command -v firewall-cmd &> /dev/null; then
    sudo firewall-cmd --add-port=${PORT_FLOWISE}/tcp --permanent >/dev/null 2>&1 || true
    sudo firewall-cmd --reload >/dev/null 2>&1 || true
fi

echo "--------------------------------------------------------"
echo "🎨 Flowise Docker Container Active!"
echo "Visual Dashboard: http://localhost:${PORT_FLOWISE}"
echo "--------------------------------------------------------"
