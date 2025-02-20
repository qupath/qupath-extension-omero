#!/bin/sh

# Import the /OMERO folder from the OMERO server container. This is needed for the
# pixel buffer microservice to work
cd /
tar -xvkf /tmp/OMERO.tar.gz

# See https://stackoverflow.com/a/78693402
# This should be needed as long as the base image is the deprecated CentOS7
# The bug only happens from Linux
sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*
sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*

# Build the pixel buffer microservice
yum install git java-11-openjdk-devel -y
git clone https://github.com/glencoesoftware/omero-ms-pixel-buffer
cd /omero-ms-pixel-buffer
export JAVA_HOME=/usr/lib/jvm/jre-11
./gradlew installDist

# The rest is in a different script because the gradlew command was always exiting
# the current script.