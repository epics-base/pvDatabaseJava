Release Notes for pvDatabaseJava
================================

## EPICS V4 release 4.6

### Release 4.2

* The examples are moved to exampleJava.
* Support for channelRPC is now available.
* removeRecord and traceRecord are now available.


## EPICS V4 release 4.5

pvDatabaseJava is one component of EPICS V4 release 4.5.

The main change since release 4.0 is:

* recordList has been removed since pvAccess has the pvlist shell command.


## Release 4.0 IN DEVELOPMENT

The main changes since release 3.0.2 are:

* array semantics now enforce Copy On Write.
* String no longer defined.
* toString replaced by stream I/O 
* union is new type.
* copy and monitor use new code in pvDataCPP

### New Semantics for Arrays

pvDatabaseCPP has been changed to use the new array implementation from 
pvDataCPP.

### String no longer defined

String is replaced by std::string.

### toString replaced by stream I/O

All uses of toString have been changed to use the steam I/O that pvDataCPP 
implements.

### union is a new basic type.

exampleDatabase now has example records for union and union array.
There are records for regular union and for variant union.

### copy 

The implementation of copy and monitor for pvAccess has been changed
to use the new monitor and copy support from pvDataCPP.

### monitorPlugin

exampleDatabase now has a example plugin that implements onChange.

## Release 0.9.2
This was the starting point for RELEASE_NOTES.
