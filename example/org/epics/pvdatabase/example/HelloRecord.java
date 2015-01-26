package org.epics.pvdatabase.example;

import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.pv.FieldBuilder;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Structure;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVDatabaseFactory;
import org.epics.pvdatabase.PVRecord;

public class HelloRecord extends PVRecord {
    private static final FieldCreate fieldCreate = FieldFactory.getFieldCreate();
    private static final PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
    
    private PVString arg;
    private PVString result;

    public static PVRecord create(String recordName)
    {
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        Structure structure = 
            fb.addNestedStructure("argument").
                add("value",ScalarType.pvString).
                endNested().
            addNestedStructure("result").
                add("value",ScalarType.pvString).
                endNested().
            createStructure();
       PVRecord pvRecord = new HelloRecord(recordName,pvDataCreate.createPVStructure(structure));
       pvRecord.setTraceLevel(4);
       PVDatabase master = PVDatabaseFactory.getMaster();
       master.addRecord(pvRecord);
       return pvRecord;
    }
    public HelloRecord(String recordName,PVStructure pvStructure) {
        super(recordName,pvStructure);
        arg = pvStructure.getSubField(PVString.class, "argument.value");
        if(arg==null) throw new IllegalArgumentException("arg not found");
        result = pvStructure.getSubField(PVString.class, "result.value");
        if(result==null) throw new IllegalArgumentException("result not found");
        
    }
    
    public void process()
    {
        int level = getTraceLevel();
        if(level>1) System.out.println("PVRecordAplusB::process");
        result.put("Hello " +arg.get());
    }
}
