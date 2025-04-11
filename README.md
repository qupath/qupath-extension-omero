# QuPath OMERO extension

Welcome to the OMERO extension for [QuPath](http://qupath.github.io)!

This adds support for accessing images hosted on an [OMERO](https://www.openmicroscopy.org/omero/) 
server through OMERO's web (and other) API.

The extension is intended for QuPath v0.6 and later.
It is not compatible with earlier QuPath versions.

## Installing

To install the OMERO extension, you can:
* Open the QuPath [extension manager](https://qupath.readthedocs.io/en/latest/docs/intro/extensions.html#managing-extensions-with-the-extension-manager) and install the extension from there (recommended).
* Or download the latest `qupath-extension-omero-[version].jar` file from [releases](https://github.com/qupath/qupath-extension-omero/releases) and drag it onto the main QuPath window.

If you haven't installed any extensions before, you'll be prompted to select a QuPath user directory.
The extension will then be copied to a location inside that directory.

You might then need to restart QuPath (but not your computer).

If you want to use the ICE pixel API (see the [documentation](https://qupath.readthedocs.io/en/stable/docs/advanced/omero.html#opening-omero-images)), you need to install the OMERO Java dependencies. This can be done:
* With the [extension manager](https://qupath.readthedocs.io/en/latest/docs/intro/extensions.html#managing-extensions-with-the-extension-manager) by clicking on *Install optional dependencies* when installing the extension (recommended).
* Or by going on the [OMERO download page](https://www.openmicroscopy.org/omero/downloads/), under "OMERO Java", download the .zip file, unzip it and copy the *libs* folder in your extension directory.

## Documentation

The main documentation for the extension is at https://qupath.readthedocs.io/en/latest/docs/advanced/omero.html.

## Building

You can build the extension using OpenJDK 21 or later with

```bash
./gradlew clean build
```

The output will be under `build/libs`.
You can drag the jar file on top of QuPath to install the extension.

## Running tests

You can run the tests with

```bash
./gradlew test
```

Some of the tests require having Docker installed and running.

By default, a new local OMERO server will be created each time this command is run. As it takes
a few minutes, you can instead create a local OMERO server by running the
`qupath-extension-omero/src/test/resources/server.sh` script and setting the
`OmeroServer.IS_LOCAL_OMERO_SERVER_RUNNING` variable to `true`
(`qupath-extension-omero/src/test/java/qupath/ext/omero/OmeroServer` file).
That way, unit tests will use the existing OMERO server instead of creating a new one.
