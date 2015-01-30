package org.epics.pvdatabase.example;

import org.epics.pvdata.factory.StandardPVFieldFactory;
import org.epics.pvdata.pv.PVDoubleArray;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.StandardPVField;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVDatabaseFactory;
import org.epics.pvdatabase.PVRecord;

public class ArrayServerRecord extends PVRecord implements Runnable {
    private static final StandardPVField standardPVField = StandardPVFieldFactory.getStandardPVField();
    private PVDoubleArray pvValue = null;
    private double[] value;
    private Thread thread;
    private int arraySize;
    private long waitTime;
    private double elementValue = 0.0;

    public static PVRecord create(String recordName,int arraySize, double waitSecs)
    {
        PVStructure pvStructure = standardPVField.scalarArray(ScalarType.pvDouble, "alarm,timeStamp");
        PVRecord pvRecord = new ArrayServerRecord(recordName,pvStructure,arraySize,waitSecs);
        PVDatabase master = PVDatabaseFactory.getMaster();
        master.addRecord(pvRecord);
        return pvRecord;
    }
    public ArrayServerRecord(String recordName,PVStructure pvStructure,int arraySize, double waitSecs) {

        super(recordName,pvStructure);
        this.arraySize = arraySize;
        waitTime = (long)(waitSecs*1000.0);
        pvValue = pvStructure.getSubField(PVDoubleArray.class, "value");
        if(pvValue==null) throw new IllegalArgumentException("value not found");
        value = new double[arraySize];
        thread = new Thread(this,"ArrayServerReord");
        thread.start();
    }

    public void process()
    {
        pvValue.put(0, value.length, value, 0);
        super.process();
    }
    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(waitTime);
            } catch (Throwable th) {
                th.printStackTrace();
            }
            for(int i=0; i<arraySize; i++) value[i] = elementValue + i;
            ++elementValue;
            lock();
            try {
                beginGroupPut();
                process();
                endGroupPut();
            } finally {
                unlock();
            }
        }
    }
}
