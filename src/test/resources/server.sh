#!/usr/bin/env bash
## bash is a lot more firendly than bare sh, and /usr/bin/env will reliably find it cross-platform

## some would call this bash "strict mode".
## it makes bash a bit more strict on substitutions and variable evaluation
set -euo pipefail
#-e exit when commands fail
# -u unset variables error rather than eval to ""
# -o pipefail makes a pipe fail if any part fails, not just the last part.
# eg `cat myfile | grep foo` fails if cat myfile fails... helpful.
IFS=$'\n\t' # narrows word splitting to only tab and newline





# This script will start a local OMERO server using four Docker containers.
# Docker must be installed and running before running this script. The script works on Linux and MacOS.
# The server will be accessible at http://localhost:4080/.
# To make the unit tests use this server, set the OmeroServer.IS_LOCAL_OMERO_SERVER_RUNNING variable to true.


# find the absolute path of this script and cd to the same directory
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
  --platform linux/x86_64 \
  --mount "type=bind,src=$SCRIPT_DIR/omero-server,target=/resources" \
  openmicroscopy/omero-server

# Start OMERO web server
docker run -d \
  --name omero-web \
  --network omero-network \
  -e OMEROHOST=omero-server \
  -e CONFIG_omero_web_public_enabled=True \
  -e CONFIG_omero_web_public_user=public \
  -e CONFIG_omero_web_public_password=password_public \
  -e CONFIG_omero_web_public_url__filter="(.*?)" \
  -e CONFIG_omero_web_caches='{"default": {"BACKEND": "django_redis.cache.RedisCache","LOCATION": "redis://redis:6379/0"}}' \
  -e CONFIG_omero_web_session__engine=django.contrib.sessions.backends.cache \
  -p 4080:4080 -p 8082:8082 \
  --privileged \
  --mount "type=bind,src=$SCRIPT_DIR/omero-web,target=/resources" \
  openmicroscopy/omero-web-standalone

# Wait for server to come online
echo "Letting OMERO server start..."
while true; do
    ERROR_OUTPUT=$(docker exec -it omero-server bash -c "/opt/omero/server/venv3/bin/omero login root@localhost:4064 -w password" 2>&1)

    if echo "$ERROR_OUTPUT" | grep -q "Exception"; then
        echo "Error detected: $ERROR_OUTPUT"
        echo "Retrying in 5 seconds..."
        sleep 5
    else
        echo "OMERO server started"
        break
    fi
done

# Setup OMERO server
docker exec -i omero-server bash < $SCRIPT_DIR"/omero-server/setup.sh"

# Copy OMERO folder from OMERO server container to OMERO web server container (required for pixel buffer microservice to work)
docker cp omero-server:/tmp/OMERO.tar.gz /tmp/OMERO.tar.gz
docker cp /tmp/OMERO.tar.gz omero-web:/tmp/OMERO.tar.gz

# Setup OMERO web server
docker exec -i -u root omero-web bash < $SCRIPT_DIR"/omero-web/installPixelBufferMs.sh"
docker exec -i -u root omero-web bash < $SCRIPT_DIR"/omero-web/runPixelBufferMs.sh"
