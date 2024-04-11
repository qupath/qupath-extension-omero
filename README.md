> # :warning: Important! 
> * :new: **This is a completely new OMERO extension for QuPath v0.5+**!
> * :construction: **It's still being developed - it's currently intended only for testing**
> * :computer: **You can find the older, stable extension at https://github.com/qupath/qupath-extension-omero-web**
> * :fireworks: **The biggest change is that _this extension will provide raw pixel access_**
> * :bug: **Please report any bugs through the [issues page](https://github.com/qupath/qupath-extension-omero/issues)**

# QuPath OMERO extension

Welcome to the OMERO extension for [QuPath](http://qupath.github.io)!

This adds support for accessing images hosted on an [OMERO](https://www.openmicroscopy.org/omero/) 
server through OMERO's web (and other) API.

> **Important!**
> 
> By default, this extension uses the OMERO **web** API to read images, which 
> has several limitations.
> See the [Reading images](#reading-images) section.

The extension is intended for QuPath v0.5 and later.
It is not compatible with earlier QuPath versions.

## Installing

To install the OMERO extension, download the latest `qupath-extension-omero-[version].jar` file from [releases](https://github.com/qupath/qupath-extension-omero/releases) and drag it onto the main QuPath window.

If you haven't installed any extensions before, you'll be prompted to select a QuPath user directory.
The extension will then be copied to a location inside that directory.

You might then need to restart QuPath (but not your computer).

## Reading images
The extension uses several APIs to read images:
* The **OMERO web API**:
  * This method is enabled by default and is available on every OMERO server.
  * It is fast but only 8-bit RGB images can be read, and they are JPEG-compressed.
This effectively means it is most useful for viewing and annotating RGB images
(including whole slide images), but is not suitable for quantitative analysis where
JPEG compression artifacts would be problematic.
* The **OMERO Ice API**:
  * This method can read every image and access raw pixel values.
  * However, you have to install the OMERO Java dependencies to enable it: from the
[OMERO download page](https://www.openmicroscopy.org/omero/downloads/), under
"OMERO Java", download the .zip file, unzip it and copy the *libs* folder in
your extension directory.
  * Note that it is not possible to use the Ice API when accessing an OMERO server with
a guest account, you have to be authenticated.
  * If you can't open any image with the Ice API, it may be because the OMERO.server
instance is on a different server than the OMERO.web instance. You can define a different
address and port to the OMERO.server in the settings of the extension.
* The **OMERO Pixel Data Microservice** (available [here](https://github.com/glencoesoftware/omero-ms-pixel-buffer)):
  * This method can read every image and access raw pixel values.
  * If this microservice is installed on your OMERO server, the extension will automatically
detect it. If that's not the case, check that the port indicated in the settings
of the extension corresponds to the port used by the microservice on the OMERO
server (by default *8082*). 

These APIs are only about retrieving pixel values. Everything else (for example
image metadata) is retrieved using calls to the OMERO web server. Therefore, the URI displayed
in the "Image" tab of QuPath might not reflect the API used to retrieve pixel values. You
can see which pixel API is used by looking at the "Image type" entry.

## Scripting
Script examples are located in the `sample-scripts` folder. They show how the
extension can be used from scripts (with or without the graphical user interface).

## Building

You can build the extension using OpenJDK 17 or later with

```bash
gradlew clean build
```

The output will be under `build/libs`.
You can drag the jar file on top of QuPath to install the extension.

## Running tests

You can run the tests with

```bash
gradlew test
```

Some of the tests require having Docker installed and running.

By default, a new local OMERO server will be created each time this command is run. As it takes
a few minutes, you can instead create a local OMERO server by running the
`qupath-extension-omero/src/test/resources/server.sh` script and setting the
`OmeroServer.IS_LOCAL_OMERO_SERVER_RUNNING` variable to `true`
(`qupath-extension-omero/src/test/java/qupath/ext/omero/OmeroServer` file).
That way, unit tests will use the existing OMERO server instead of creating a new one.