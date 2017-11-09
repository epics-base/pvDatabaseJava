pvDatabaseJava   [![Build Status](https://travis-ci.org/epics-base/pvDatabaseJava.svg?branch=master)](https://travis-ci.org/epics-base/pvDatabaseJava)
============

A brief description of a pvDatabase is that it is a set of network accessible, smart, memory resident records. Each record has data composed of a top level PVStructure. Each record has a name which is the channelName for pvAccess. A local Channel Provider implements the complete ChannelProvider and Channel interfaces as defined by pvAccess. The local provider provides access to the records in the pvDatabase. This local provider is accessed by the remote pvAccess server. A record is smart because code can be attached to a record, which is accessed via a method named process.

Building
--------

    mvn package


Examples
------------

Examples are available in exampleJava.

Status
------

* The API is for EPICS Version 4 release 4.6.0


