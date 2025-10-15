#!/bin/sh

# Connect as root
/opt/omero/server/venv3/bin/omero login root@localhost:4064 -w password

# Create admin user
/opt/omero/server/venv3/bin/omero user add admin admin admin --group-name system -P password_admin

# Create public user and group
/opt/omero/server/venv3/bin/omero group add public-data --type=read-only
/opt/omero/server/venv3/bin/omero user add public public access --group-name public-data -P password_public

# Create other users and groups
/opt/omero/server/venv3/bin/omero group add group1 --type=read-annotate
/opt/omero/server/venv3/bin/omero group add group2 --type=read-only
/opt/omero/server/venv3/bin/omero group add group3
/opt/omero/server/venv3/bin/omero user add user1 user1 user1 --group-name group1 -P password_user1
/opt/omero/server/venv3/bin/omero user add user2 user2 user2 --group-name group2 -P password_user2
/opt/omero/server/venv3/bin/omero user add user3 user3 user3 --group-name group3 -P password_user3
/opt/omero/server/venv3/bin/omero user add user user user --group-name group1 group2 -P password_user

# Add user to group3 and set owner
/opt/omero/server/venv3/bin/omero group adduser --name group3 user --as-owner

# Connect as public user
/opt/omero/server/venv3/bin/omero login public@localhost:4064 -w password_public

# Create a project and a dataset inside it
project=$(/opt/omero/server/venv3/bin/omero obj new Project name=project)
dataset=$(/opt/omero/server/venv3/bin/omero obj new Dataset name=dataset)
/opt/omero/server/venv3/bin/omero obj new ProjectDatasetLink parent=$project child=$dataset

# Create a comment annotation and attach it to the previously created dataset
comment=$(/opt/omero/server/venv3/bin/omero obj new CommentAnnotation textValue=comment)
/opt/omero/server/venv3/bin/omero obj new DatasetAnnotationLink parent=$dataset child=$comment

# Create a file annotation and attach it to the previously created dataset
analysis=$(/opt/omero/server/venv3/bin/omero upload /resources/analysis.csv)
file=$(/opt/omero/server/venv3/bin/omero obj new FileAnnotation file=$analysis)
/opt/omero/server/venv3/bin/omero obj new DatasetAnnotationLink parent=$dataset child=$file

# Create an orphaned dataset
/opt/omero/server/venv3/bin/omero obj new Dataset name=orphaned_dataset

# Import images as children of the first dataset
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/rgb.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/uint8.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/uint16.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/int16.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/int32.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/float32.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/float64.tiff

# Import image as an orphaned image
/opt/omero/server/venv3/bin/omero import /resources/images/complex.tiff

# Create a screen and a plate inside it
screen=$(/opt/omero/server/venv3/bin/omero obj new Screen name=screen)
/opt/omero/server/venv3/bin/omero import -r $screen /resources/plate/plate-well.xml

# Create an orphaned plate
/opt/omero/server/venv3/bin/omero import /resources/plate/plate-well.xml

# Connect as user1
/opt/omero/server/venv3/bin/omero login user1@localhost:4064 -w password_user1

# Create a project and two datasets inside it
project=$(/opt/omero/server/venv3/bin/omero obj new Project name=project)
dataset=$(/opt/omero/server/venv3/bin/omero obj new Dataset name=dataset1)
/opt/omero/server/venv3/bin/omero obj new ProjectDatasetLink parent=$project child=$dataset
dataset=$(/opt/omero/server/venv3/bin/omero obj new Dataset name=dataset2)
/opt/omero/server/venv3/bin/omero obj new ProjectDatasetLink parent=$project child=$dataset

# Import images as children of the first dataset
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/rgb.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/float32.tiff

# Create orphaned datasets
/opt/omero/server/venv3/bin/omero obj new Dataset name=orphaned_dataset1
/opt/omero/server/venv3/bin/omero obj new Dataset name=orphaned_dataset2

# Create screens and plates inside it
screen=$(/opt/omero/server/venv3/bin/omero obj new Screen name=screen1)
/opt/omero/server/venv3/bin/omero import -r $screen /resources/plate/plate-well.xml
screen=$(/opt/omero/server/venv3/bin/omero obj new Screen name=screen2)
/opt/omero/server/venv3/bin/omero import -r $screen /resources/plate/plate-plate_acquisition-well.xml

# Create orphaned plates
/opt/omero/server/venv3/bin/omero import /resources/plate/plate-well.xml
/opt/omero/server/venv3/bin/omero import /resources/plate/plate-plate_acquisition-well.xml

# Connect as user2
/opt/omero/server/venv3/bin/omero login user2@localhost:4064 -w password_user2

# Import orphaned images
/opt/omero/server/venv3/bin/omero import /resources/images/uint8.tiff
/opt/omero/server/venv3/bin/omero import /resources/images/uint16.tiff
/opt/omero/server/venv3/bin/omero import /resources/images/int16.tiff
/opt/omero/server/venv3/bin/omero import /resources/images/int32.tiff
/opt/omero/server/venv3/bin/omero import /resources/images/float64.tiff

# Connect as user
/opt/omero/server/venv3/bin/omero login user@localhost:4064 -w password_user

# Import images as a orphaned images
/opt/omero/server/venv3/bin/omero import /resources/images/complex.tiff

# Create an archive from the /OMERO directory (needed for the OMERO web container)
tar -czvf /tmp/OMERO.tar.gz /OMERO
