---
title: 'QuPath OMERO extension: A QuPath extension to exchange data with OMERO servers'
tags:
  - QuPath
  - OMERO
  - Java
  - image analysis
authors:
  - name: Léo Leplat
    orcid: 0009-0006-4178-7980
    affiliation: 1
    corresponding: true
  - name: Peter Bankhead
    orcid: 0000-0003-4851-8813
    affiliation: 1
    corresponding: true
affiliations:
  - name: Institute of Genetics and Cancer, University of Edinburgh, Edinburgh, EH4 2XU, UK
    index: 1
    ror: 05hygey35
date: 12 March 2026
bibliography: paper.bib
---

# Summary

QuPath [@Bankhead2017] is open-source software for bioimage analysis, specifically designed for digital pathology and whole slide analysis in research.
QuPath enables visualizing and annotating large images with many formats, running algorithms for detecting and classifying cells or tissue, and provides a workflow system for reproducibility. 

OMERO [@Allan2012] is open-source software to store, view, organize, and share large images with many formats.
It is often used for exchanging images and analysis results between collaborators.

The QuPath OMERO extension allows to exchange data between QuPath and an OMERO server, facilitating collaboration in whole slide analysis.

# Statement of need

QuPath is a popular, open-source application for image analysis, written in JavaFX [@Bankhead2017].
With over 900,000 downloads (across all releases) and more than 6,000 paper citations to date, QuPath's support for large and complex images have helped establish the software as a key biomedical research tool in labs worldwide.

QuPath is routinely used to analyse whole slide images, which are common in research and the field of digital pathology.
A 'small' whole slide image might be 120,000 x 60,000 pixels in size, which equates to around 20 GB uncompressed data (assuming 8-bit, 3-channel RGB data).
Some images used with QuPath can be much larger, including fluorescence multiplexed whole slide images that can contain dozens of 16-bit or 32-bit channels.
While lossy compression can somewhat reduce file sizes, data management is a major issue for large studies - particularly given that QuPath is primarily a desktop application, designed to work with a local file system.

OMERO is a popular, open-source image management solution that enables images to be stored on a central server [@Allan2012].
OMERO also supports whole slide and multiplexed images, enabling them to be viewed locally through a web browser.
It is the image management system of choice for many institutions worldwide.

The QuPath OMERO extension exists to bridge the gap between both tools - making it possible to apply QuPath analysis at scale to images hosted in OMERO.
By efficiently accessing only the required pixels and metadata, the extension avoids the need to download and duplicate entire datasets.

# State of the field

The current work builds upon lessons learned in the development of two previous QuPath extensions that connected to OMERO.

Initially, within the QuPath development team we created the [QuPath Web OMERO extension](https://github.com/qupath/qupath-extension-omero-web).
This lightweight extension could be added to QuPath and access images through the OMERO web API.
As a single jar file without external dependencies, this was easy to install and use.
However, it had the major limitation of being able to request only JPEG-compressed, 8-bit RGB tiles - not raw pixel data.
This made it unsuitable for quantitative analysis and incapable of supporting most fluorescence images, which can have different bit-depths and channels.

The [BIOP OMERO extension](https://github.com/BIOP/qupath-extension-biop-omero) started as a fork of our original extension.
It was created by the [BioImaging and Optics Platform (BIOP)](https://biop.epfl.ch/) team to use the OMERO Ice API, rather than the web API.
This had the major advantage of raw pixel access, but introduced three disadvantages: it required many additional dependencies, raw pixel access could be slow, and it supported only OMERO servers with authentication.

We developed the present QuPath OMERO extension from scratch in order to provide a more flexible and maintainable solution.
It aims to overcome previous limitations, while adding support for recent OMERO features and implementing best practice through extensive tests.
Key features include:

1. Browse and import images from both public and private OMERO servers.
2. Flexible pixel access through different APIs.
3. Exchange extra information (e.g., annotated regions of interest) between QuPath and OMERO.
4. Extensive unit tests.
5. Run custom scripts within QuPath to interact with the OMERO server.
6. Easy installation, through QuPath's new extension manager - including optional dependencies, if required.
 
# Software design
While the need for flexible pixel access from both public and private OMERO servers was the initial motivation for this work, 
we developed a new extension so that we could better separate core logic and the user interface.
This separation is reflected in the code being divided into two main packages: `gui` and `core`.

The `gui` package contains the main user interface controls and interaction with the main QuPath application.
It is designed to follow the general style of QuPath and to always stay responsive by delegating long-running tasks to background threads.
When browsing an OMERO server, the interface resembles the OMERO web client to facilitate user adoption.

The `core` package is largely independent of the user interface, although we did allow the use of JavaFX observable properties and some QuPath classes (e.g., to manage preferences).
This was a pragmatic decision that allowed us to avoid unnecessary code duplication and complexity.
The key distinguishing factor of the `core` package is that it contains classes that can be used headlessly and via scripts, while the `gui` package requires the interface to be instantiated.

The `core` package is also extensively unit tested.
Because a lot of functions can only be tested when an active connection to an OMERO server is established, a Docker container that hosts an OMERO server is automatically created when unit tests are run.
The extension also provides a bash script to create this Docker container outside of the unit tests, which can be useful for manual testing.

A central part of the `core` package is support for different pixel APIs.
Three are currently implemented:

* The **web** pixel API: this method is enabled by default and is available on every OMERO server. It is fast, but suffers from the same limitations as the QuPath Web OMERO extension: only RGB images can be read, and images are JPEG-compressed.
* The **Ice** pixel API: similar to the BIOP OMERO extension, this can read any image and access raw pixel values, but additional dependencies have to be installed. It does not support reading images when the connection to the OMERO server is not authenticated.
* The **pixel data microservice** API: this method can read any image and access the raw pixel values. It works for both public and private servers, assuming the [OMERO Pixel Data Microservice](https://github.com/glencoesoftware/omero-ms-pixel-buffer) is installed.

Of the three options, the pixel data microservice API has significant advantages from the QuPath perspective, however the required microservice is not yet available on enough OMERO servers to make it a default.
Alternatives might also become adopted in the future, such as the [OMERO Zarr Pixel Buffer](https://github.com/glencoesoftware/omero-zarr-pixel-buffer).
This makes flexibility essential.

User instructions can be found on the OMERO page of the [QuPath documentation](https://qupath.readthedocs.io/en/stable/docs/advanced/omero.html).
We have also provided javadoc comments for all public fields and methods within the extension.
The javadocs are installed along with the extension and are available to support scripting via QuPath's built-in Javadoc viewer.

# Research impact statement

The QuPath OMERO extension was initially released on February 2024.
Since then, the extension has evolved through contributions, bugs reporting, and feature requests, mainly by the [BioImaging and Optics Platform (BIOP) team](https://biop.epfl.ch/).

As of the beginning of March 2026, the extension was downloaded 29,727 times, demonstrating a broad and active user community. 

# AI usage disclosure

No generative AI was used in the software creation, documentation, or paper authoring.

# Acknowledgements

This work was supported by the Wellcome Trust [223750/Z/21/Z].
This project has been made possible in part by grant number 2021-237595 from the Chan Zuckerberg Initiative DAF, an advised fund of Silicon Valley Community Foundation.

We thank Melvin Gelbard and Rémy Dornier for contributions to earlier QuPath OMERO extensions, and the BIOP team (especially Rémy) for extensive testing and feedback on the current work.

# References