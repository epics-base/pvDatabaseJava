// Copyright information and license terms for this software can be
// found in the file LICENSE that is included with the distribution

/**
 * @author mrk
 * @date 2013.07.24
 */


package org.epics.pvdatabase;

import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.pv.FieldBuilder;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Structure;

/**
 * Set the trace level for another record in the same database.
 * It is meant to be used via a channelPutGet request.
 * The argument has two fields: recordName, and level.
 * The result has a field named status.
 * @author mrk
 * @since 2016.07
 */
public class TraceRecord extends PVRecord {
    PVDatabase pvDatabase;
    PVString pvRecordName;
    PVInt pvLevel;
    PVString pvResult;

    public static PVRecord create(String recordName)
    {
        FieldCreate fieldCreate = FieldFactory.getFieldCreate();
        PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        Structure structure = 
                fb.addNestedStructure("argument").
                add("recordName",ScalarType.pvString).
                add("level",ScalarType.pvInt).
                endNested().
                addNestedStructure("result").
                add("status",ScalarType.pvString).
                endNested().
                createStructure();
        PVRecord pvRecord = new TraceRecord(recordName,pvDataCreate.createPVStructure(structure));
        PVDatabase master = PVDatabaseFactory.getMaster();
        master.addRecord(pvRecord);
        return pvRecord;
    }
    private TraceRecord(String recordName,PVStructure pvStructure) {
        super(recordName,pvStructure);
        pvRecordName = pvStructure.getSubField(PVString.class,"argument.recordName");
        pvLevel = pvStructure.getSubField(PVInt.class,"argument.level");
        pvResult = pvStructure.getSubField(PVString.class,"result.status");
        pvDatabase = PVDatabaseFactory.getMaster();
    }

    public void process()
    {
        String name = pvRecordName.get();
        PVRecord pvRecord = pvDatabase.findRecord(name);
        if(pvRecord==null) {
            pvResult.put(name + " not found");
            return;
        }
        pvRecord.setTraceLevel(pvLevel.get());
        pvResult.put("success");
        super.process();
    }
}
