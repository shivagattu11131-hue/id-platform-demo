#!/bin/bash
# ============================================================
#  Raksul ID Platform - Docker VM Setup Script
#  For: Oracle Linux 9.6 (RHEL-based)
# ============================================================
set -e

APP_DIR="/opt/raksul-id-platform"
VM_IP=$(curl -s ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')

echo "============================================"
echo "  Raksul ID Platform - Docker Deployment"
echo "  Detected IP: $VM_IP"
echo "============================================"

# --- 1. Install Docker ---
echo ""
echo "[1/4] Installing Docker..."
if command -v docker &>/dev/null; then
    echo "  Docker $(docker --version 2>&1 | awk '{print $3}') already installed"
else
    sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
    sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    sudo systemctl enable --now docker
    echo "  Docker installed and started"
fi

# --- 2. Add user to docker group ---
echo ""
echo "[2/4] Configuring Docker access..."
if groups $(whoami) | grep -q docker; then
    echo "  User already in docker group"
else
    sudo usermod -aG docker $(whoami)
    echo "  Added $(whoami) to docker group"
    echo "  NOTE: You may need to log out and back in for group changes to take effect"
fi

# --- 3. Setup application directory ---
echo ""
echo "[3/4] Setting up application directory..."
if [ -d "$APP_DIR" ]; then
    echo "  Application directory exists: $APP_DIR"
else
    sudo mkdir -p $APP_DIR
    sudo chown $(whoami):$(whoami) $APP_DIR
    echo "  Created: $APP_DIR"
fi

# --- 4. Build and start containers ---
echo ""
echo "[4/4] Building and starting Docker containers..."
cd $APP_DIR

# Build images
echo "  Building Docker images (this may take a few minutes)..."
VM_IP=$VM_IP docker compose build

# Start services
echo "  Starting services..."
VM_IP=$VM_IP docker compose up -d

# Wait for health checks
echo "  Waiting for services to become healthy..."
sleep 10

# Show status
echo ""
echo "============================================"
echo "  DEPLOYMENT COMPLETE!"
echo "============================================"
echo ""
docker compose ps
echo ""
echo "  Service URLs:"
echo "    ID Platform:  http://$VM_IP:3000"
echo "    Main Site:    http://$VM_IP:3001"
echo "    MA Site:      http://$VM_IP:3002"
echo ""
echo "  Docker commands:"
echo "    docker compose ps              # View running containers"
echo "    docker compose logs -f         # View all logs"
echo "    docker compose logs -f id-platform  # View ID Platform logs"
echo "    docker compose restart         # Restart all services"
echo "    docker compose down            # Stop all services"
echo "    docker compose up -d --build   # Rebuild and start"
echo "============================================"
