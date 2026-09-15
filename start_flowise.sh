#!/usr/bin/env bash
set -e

PORT_FLOWISE=3000

echo "=== 1. Checking Node.js & pnpm ==="
if ! command -v node &> /dev/null; then
    echo "Installing Node.js..."
    sudo dnf install -y nodejs git
fi

if ! command -v pnpm &> /dev/null; then
    echo "Installing pnpm package manager..."
    sudo npm install -g pnpm
fi

echo "=== 2. Checking Flowise Source Repository ==="
if [ ! -d "Flowise" ]; then
    echo "Cloning Flowise repository..."
    git clone https://github.com/FlowiseAI/Flowise.git
fi

cd Flowise

echo "=== 3. Installing Dependencies & Building Flowise ==="
if [ ! -d "node_modules" ] || [ ! -d "packages/server/dist" ]; then
    echo "Installing dependencies with build permissions..."
    PNPM_CONFIG_DANGEROUSLY_ALLOW_ALL_BUILDS=true pnpm install --no-frozen-lockfile
    
    echo "Building Flowise UI and Server..."
    export NODE_OPTIONS="--max-old-space-size=4096"
    pnpm build
else
    echo "Flowise build artifacts found. Skipping full re-build."
fi

echo "=== 4. Opening Firewall Port ${PORT_FLOWISE} ==="
if command -v firewall-cmd &> /dev/null; then
    sudo firewall-cmd --add-port=${PORT_FLOWISE}/tcp --permanent >/dev/null 2>&1 || true
    sudo firewall-cmd --reload >/dev/null 2>&1 || true
fi

echo "--------------------------------------------------------"
echo "🎨 Starting Flowise Native Server..."
echo "Visual Dashboard: http://localhost:${PORT_FLOWISE}"
echo "--------------------------------------------------------"

PNPM_CONFIG_DANGEROUSLY_ALLOW_ALL_BUILDS=true pnpm start
