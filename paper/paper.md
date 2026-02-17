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
  - name: Peter Bankhead
    orcid: 0000-0003-4851-8813
    affiliation: 1
    corresponding: true
affiliations:
  - name: Institute of Genetics and Cancer, University of Edinburgh, Edinburgh, EH4 2XU, UK
    index: 1
    ror: 05hygey35
date: 16 February 2026
bibliography: paper.bib
---

# Summary

QuPath [@Bankhead2017] is open-source software for bioimage analysis, specifically designed for digital pathology and whole slide analysis in research. The software allows visualizing and annotating large images with many formats, running algorithms for detecting and classifying cells or tissue, and a workflow system that allows reproducibility. 

OMERO [@Allan2012] is an open-source server that can store, view, organize, analyse, and share large images with many formats. It is often used for exchanging images and analysis results between collaborators.

The QuPath OMERO extension allows to exchange data between QuPath and an OMERO server, facilitating collaboration in whole slide analysis.

# Statement of need

Importing images stored on an OMERO server to QuPath used to require a lot of manual steps. First, images had to be manually downloaded, which can take a lot of time when working with large whole slide images. Second, OMERO allows to upload and share different types of analysis results, such as annotations and measurements. Importing these results to QuPath (or exporting them to OMERO) was also a manual and long process.

The QuPath OMERO extension was created to address these issues. It is a QuPath extension that allows QuPath to exchange data with an OMERO server. When the extension is added to QuPath, the user can:

* Connect to an OMERO server. The connection can be authenticated or unauthenticated if the server allows it. 
* Browse an OMERO server. This means having a view of all the projects, datasets, images, screens, plates, and wells of the server accessible by the current user.
* Importing an image stored on an OMERO server to QuPath, without having to download it. The extension fetch pixels on demand, allowing to open large images in just a few seconds.
* Send data from QuPath to OMERO or from OMERO to QuPath, such as:
  * Annotations, annotation measurements, and detection measurements.
  * Metadata, in the form of key-value pairs.
  * Image settings, such as the image name, channel names, colors, and display ranges.

Additionnally, the API of the extension was designed to be easily used from QuPath scripts. Sample scripts performing operations mentionned above are provided.
 
The instructions to use the extension can be found on the OMERO page of the [QuPath documentation](https://qupath.readthedocs.io/en/stable/docs/advanced/omero.html).

# State of the field

Two other QuPath extensions allows to work with OMERO from QuPath:

* The [QuPath Web OMERO extension](https://github.com/qupath/qupath-extension-omero-web). It is the original (now deprecated) version of the extension developed by the QuPath team. It only allows RGB images to be accessed, and a JPEG-compression is applied to pixel values. This means that non-RGB images cannot be opened, and pixel values of RGB images may slightly differ from their original values.
* The [BIOP OMERO extension](https://github.com/BIOP/qupath-extension-biop-omero). It was developed by the [BioImaging and Optics Platform (BIOP)](https://biop.epfl.ch/) team to address the limitations of the QuPath Web OMERO extension, and to provide more features. However, it requires a lot of additional dependencies, and does not provide access to OMERO servers without authentication.

The QuPath OMERO extension was developped to address the issues of the two existing extensions. It provides access to OMERO servers without authentication, and supports three methods (called "pixel APIs") to retrieve pixel values:

* The **web** pixel API: this method is enabled by default and is available on every OMERO server. It is fast but suffers from the same defaults as the QuPath Web OMERO extension: only RGB images can be read, and pixel values are JPEG-compressed.
* The **Ice** pixel API: this method is the same as the one used in the BIOP OMERO extension: it can read any image and access raw pixel values, but additional dependencies have to be installed. Also, this method cannot read images when the connection to the OMERO server is not authenticated.
* The **pixel data microservice** API: this method can read any image and access the raw pixel values, without any limitation. However, the [OMERO Pixel Data Microservice](https://github.com/glencoesoftware/omero-ms-pixel-buffer) plugin has to be installed on the OMERO server.

With these features, the QuPath OMERO extension provides more flexibility than the two existing extensions. As a result, these extensions have been marked as deprecated.
 
# Software design

The user interface of the QuPath OMERO extension was designed to follow the general style of QuPath, and to always stay responsive. The responsiveness of the user interface was indeed an issue in the two other versions of the extension. A lot of operations involve exchanging data between QuPath and an OMERO server, which can take time depending on the speed of the internet connection. Concurrency was used to handle such operations on different threads, so that the user interface thread is never blocked and can provide dynamic visual feedback about the operations being carried. Finally, the visual aspect of the extension was made similar to the design of the OMERO web client, to facilitate user adoption of the extension.

The code of the extension was designed to be easily used for scripting. As such, a single class (representing a connection to an OMERO web server) serves as the entry point to the extension. Almost all operations of the extension can be performed using this class, which is more convenient for scripting. Also, Javadocs comments were added to all public classes and functions of this extension. QuPath integrates a Javadoc viewer that has automatically access to the documentation of the extension when it is installed.

Unit tests were added for everything, except for code related to the user interface. Since a lot of functions can only be tested when an active connection to an OMERO server is established, a Docker container that hosts an OMERO server is automatically created when unit tests are run. The extension also provides a bash script to create this Docker container outside of the unit tests. This can be useful for manual testing for example.

# Research impact statement

The QuPath OMERO extension was initially released on February 2024. Since then, the extension has evolved through contributions, bugs reporting, and feature requests, mainly by the [BioImaging and Optics Platform (BIOP) team](https://biop.epfl.ch/).

As of the beginning of February 2026, the extension was downloaded 26,559 times, demonstrating a broad and active user community. 

# AI usage disclosure

No generative AI was used in the software creation, documentation, or paper authoring.

# Acknowledgements

This work was supported by the Wellcome Trust [223750/Z/21/Z].
This project has been made possible in part by grant number 2021-237595 from the Chan Zuckerberg Initiative DAF, an advised fund of Silicon Valley Community Foundation.

We thank Melvin Gelbard and Rémy Dornier for contributions to earlier QuPath OMERO extensions, and the BIOP team (especially Rémy) for extensive testing and feedback on the current work.
