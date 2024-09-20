#!/bin/sh

# Connect as root
/opt/omero/server/venv3/bin/omero login root@localhost:4064 -w password

# Create public user and group
/opt/omero/server/venv3/bin/omero group add public-data --type=read-only
/opt/omero/server/venv3/bin/omero user add public public access --group-name public-data -P password

# Connect as previously created user
/opt/omero/server/venv3/bin/omero login public@localhost:4064 -w password

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
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/int8.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/uint16.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/int16.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/uint32.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/int32.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/float32.tiff
/opt/omero/server/venv3/bin/omero import -d $dataset /resources/images/float64.tiff

# Import image as an orphaned image
/opt/omero/server/venv3/bin/omero import /resources/images/complex.tiff

# Create a screen and a plate inside it
screen=$(/opt/omero/server/venv3/bin/omero obj new Screen name=screen)
/opt/omero/server/venv3/bin/omero import -r $screen /resources/plate/hcs.companion.ome

# Create an orphaned plate
/opt/omero/server/venv3/bin/omero import /resources/plate/hcs.companion.ome

# Create an archive from the /OMERO directory (needed for the OMERO web container)
tar -czvf /tmp/OMERO.tar.gz /OMERO

# Print the file annotation ID (needed because it's not fixed, and a unit test needs to know its value)
echo $analysis