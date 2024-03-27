#!/bin/sh

# This script will start a local OMERO server using four Docker containers.
# Docker must be installed and running before running this script. The script works on Linux and MacOS.
# The server will be accessible at http://localhost:4080/.
# To make the unit tests use this server, set the OmeroServer.IS_LOCAL_OMERO_SERVER_RUNNING variable to true.

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Create network if not exist
docker network inspect omero-network >/dev/null 2>&1 || docker network create omero-network

# Delete existing containers if exist
docker ps -qa --filter "name=omero-postgres" | grep -q . && docker rm -f omero-postgres
docker ps -qa --filter "name=omero-redis" | grep -q . && docker rm -fv omero-redis
docker ps -qa --filter "name=omero-server" | grep -q . && docker rm -fv omero-server
docker ps -qa --filter "name=omero-web" | grep -q . && docker rm -fv omero-web

# Start postgres database
docker run -d \
  --name omero-postgres \
  --network omero-network --network-alias postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres

# Start redis server
docker run -d \
  --name omero-redis \
  --network omero-network --network-alias redis \
  redis

# Start OMERO server
docker run -d \
  --name omero-server \
  --network omero-network --network-alias omero-server \
  -e CONFIG_omero_db_host=postgres \
  -e CONFIG_omero_db_user=postgres \
  -e CONFIG_omero_db_pass=postgres \
  -e CONFIG_omero_db_name=postgres \
  -e ROOTPASS=password \
  -p 4064:4064 \
  --privileged \
  --mount type=bind,src=$SCRIPT_DIR"/omero-server",target=/resources \
  openmicroscopy/omero-server

# Start OMERO web server
docker run -d \
  --name omero-web \
  --network omero-network \
  -e OMEROHOST=omero-server \
  -e CONFIG_omero_web_public_enabled=True \
  -e CONFIG_omero_web_public_user=public \
  -e CONFIG_omero_web_public_password=password \
  -e CONFIG_omero_web_public_url__filter="(.*?)" \
  -e CONFIG_omero_web_caches='{"default": {"BACKEND": "django_redis.cache.RedisCache","LOCATION": "redis://redis:6379/0"}}' \
  -e CONFIG_omero_web_session__engine=django.contrib.sessions.backends.cache \
  -p 4080:4080 -p 8082:8082 \
  --privileged \
  --mount type=bind,src=$SCRIPT_DIR"/omero-web",target=/resources \
  openmicroscopy/omero-web-standalone

echo ""
echo "Letting all containers start... (this will take two minutes)"
sleep 120

# Setup OMERO server
docker exec -i omero-server bash < $SCRIPT_DIR"/omero-server/setup.sh"

# Copy OMERO folder from OMERO server container to OMERO web server container (required for pixel buffer microservice to work)
docker cp omero-server:/tmp/OMERO.tar.gz /tmp/OMERO.tar.gz
docker cp /tmp/OMERO.tar.gz omero-web:/tmp/OMERO.tar.gz

# Setup OMERO web server
docker exec -i -u root omero-web bash < $SCRIPT_DIR"/omero-web/installPixelBufferMs.sh"
docker exec -i -u root omero-web bash < $SCRIPT_DIR"/omero-web/runPixelBufferMs.sh"