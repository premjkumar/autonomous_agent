#!/usr/bin/env bash
set -e

echo "=== 1. Checking / Installing Node.js ==="
if ! command -v node &> /dev/null; then
    echo "Installing Node.js (LTS version)..."
    sudo dnf install -y nodejs npm
else
    echo "Node.js is already installed: $(node -v)"
fi

echo "=== 2. Installing Flowise Globally ==="
sudo npm install -g flowise

echo "=== 3. Opening Port 3000 in Fedora Firewall ==="
if command -v firewall-cmd &> /dev/null; then
    sudo firewall-cmd --add-port=3000/tcp --permanent || true
    sudo firewall-cmd --reload || true
fi

echo "--------------------------------------------------------"
echo "🎨 Flowise Successfully Installed!"
echo "To start the graphical UI dashboard, run:"
echo "npx flowise start"
echo "Then open your browser at: http://localhost:3000"
echo "--------------------------------------------------------"
