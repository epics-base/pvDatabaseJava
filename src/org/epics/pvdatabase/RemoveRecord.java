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
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Structure;

public class RemoveRecord extends PVRecord {
    PVDatabase pvDatabase;
    PVString pvRecordName;
    PVString pvResult;

    public static PVRecord create(String recordName)
    {
        FieldCreate fieldCreate = FieldFactory.getFieldCreate();
        PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        Structure structure = 
                fb.addNestedStructure("argument").
                add("recordName",ScalarType.pvString).
                endNested().
                addNestedStructure("result").
                add("status",ScalarType.pvString).
                endNested().
                createStructure();
        PVRecord pvRecord = new RemoveRecord(recordName,pvDataCreate.createPVStructure(structure));
        PVDatabase master = PVDatabaseFactory.getMaster();
        master.addRecord(pvRecord);
        return pvRecord;
    }
    private RemoveRecord(String recordName,PVStructure pvStructure) {
        super(recordName,pvStructure);
        pvRecordName = pvStructure.getSubField(PVString.class,"argument.recordName");
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
        pvRecord.destroy();
        pvResult.put("success");
        super.process();
    }
}
