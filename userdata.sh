#!/bin/bash
# Update package list and install Docker and Git
apt-get update -y
apt-get install -y docker.io docker-compose-v2 git

# Enable and start Docker service
systemctl start docker
systemctl enable docker

# Add ubuntu user to the docker group so they can run containers without sudo
usermod -aG docker ubuntu
