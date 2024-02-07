#!/bin/sh

# Configure the pixel buffer microservice
cd /omero-ms-pixel-buffer/build/install/omero-ms-pixel-buffer/conf/
sed -i 's/host: "localhost"/host: "omero-server"/g' config.yaml
sed -i 's/omero.db.host: "omero-server"/omero.db.host: "postgres"/g' config.yaml
sed -i 's/omero.db.name: "omero"/omero.db.name: "postgres"/g' config.yaml
sed -i 's/omero.db.user: "omero"/omero.db.user: "postgres"/g' config.yaml
sed -i 's/omero.db.pass: "omero"/omero.db.pass: "postgres"/g' config.yaml
sed -i 's\uri: "redis://:@localhost:6379/1"\uri: "redis://:@redis:6379/0"\g' config.yaml

# Start the pixel buffer microservice in the background
cd /omero-ms-pixel-buffer/build/install/omero-ms-pixel-buffer/
export JAVA_HOME=/usr/lib/jvm/jre
bin/omero-ms-pixel-buffer &