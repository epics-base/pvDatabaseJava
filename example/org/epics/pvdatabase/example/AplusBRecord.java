package org.epics.pvdatabase.example;

import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.factory.StandardFieldFactory;
import org.epics.pvdata.pv.*;
import org.epics.pvdatabase.PVRecord;

public class AplusBRecord extends PVRecord {
    private static final FieldCreate fieldCreate = FieldFactory.getFieldCreate();
    private static final PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
    private static final StandardField standardField = StandardFieldFactory.getStandardField();
    
    private PVDouble pva;
    private PVDouble pvb;
    private PVDouble pvc;
    
    public static PVRecord create(String recordName)
    {
        
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        Structure structure = 
            fb.add("a", ScalarType.pvDouble).
            add("b", ScalarType.pvDouble).
            add("c", ScalarType.pvDouble).
            add("timeStamp",standardField.timeStamp()).
            createStructure();
       return new AplusBRecord(recordName,pvDataCreate.createPVStructure(structure));
    }
    
    public AplusBRecord(String recordName,PVStructure pvStructure) {
        super(recordName,pvStructure);
        pva = pvStructure.getSubField(PVDouble.class, "a");
        if(pva==null) throw new IllegalArgumentException("a not found");
        pvb = pvStructure.getSubField(PVDouble.class, "b");
        if(pvb==null) throw new IllegalArgumentException("b not found");
        pvc = pvStructure.getSubField(PVDouble.class, "c");
        if(pvc==null) throw new IllegalArgumentException("c not found");
    }
    public void process()
    {
        int level = getTraceLevel();
        if(level>1) System.out.println("AplusB::process");
        pvc.put(pva.get() + pvb.get());
        super.process();
    }
}
