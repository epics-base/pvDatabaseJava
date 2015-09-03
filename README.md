pvDatabaseJava
============

A brief description of a pvDatabase is that it is a set of network accessible, smart, memory resident records. Each record has data composed of a top level PVStructure. Each record has a name which is the channelName for pvAccess. A local Channel Provider implements the complete ChannelProvider and Channel interfaces as defined by pvAccess. The local provider provides access to the records in the pvDatabase. This local provider is accessed by the remote pvAccess server. A record is smart because code can be attached to a record, which is accessed via a method named process.
pvaDatabase is a synchronous Database interface to pvAccess,
which is callback based.
pvaDatabase is thus easier to use than pvAccess itself.

pvDatabase is thus easier to use than pvAccess itself.

Building
--------

    mvn package


Examples
------------

The examples require the database in pvDatabaseTestCPP.
For example:

    mrk> pwd
    /home/epicsv4/pvDatabaseTestCPP/database/iocBoot/exampleDatabase
    mrk> ../../bin/linux-x86_64/exampleDatabase st.cmd 

Status
------

* The API is for release 4.5.0-pre1
* Everything defined in pvDatabase.h should be ready but see below for remaining work.
* Everything defined in pvDatabaseMultiChannel.h is ready but see below for remaining work.


pvDatabaseChannel
---------------

Channel::getField and channelArray are not supported for release 4.5.

pvDatabaseMultiChannel
---------------

For release 4.6 support is available for multiDouble and NTMultiChannel.
In the future additional support should be provided that at least includes NTScalarMultiChannel.

Testing with some channels not connected have not been done.
At least some testing with missing channels should be done before release 4.5
