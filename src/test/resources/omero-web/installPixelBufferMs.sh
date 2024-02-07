#!/bin/sh

# Import the /OMERO folder from the OMERO server container. This is needed for the
# pixel buffer microservice to work
cd /
tar -xvkf /tmp/OMERO.tar.gz

# Build the pixel buffer microservice
yum install git java-devel -y
git clone https://github.com/glencoesoftware/omero-ms-pixel-buffer
cd /omero-ms-pixel-buffer
export JAVA_HOME=/usr/lib/jvm/jre
./gradlew installDist

# The rest is in a different script because the gradlew command was always exiting
# the current script.