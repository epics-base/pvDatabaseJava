/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */

package org.epics.pvdatabase.example;

import org.epics.pvdata.pv.PVDouble;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdatabase.PVListener;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.PVRecordClient;
import org.epics.pvdatabase.PVRecordField;
import org.epics.pvdatabase.PVRecordStructure;
import org.epics.pvdata.copy.*;

/**
 * @author Marty Kraimer
 *
 */
public class PVRecordExample {
    
	public static void main(String[] args)
	{
	    simple();
	}
	
	static void simple()
    {
       PVRecord pvRecord = AplusBRecord.create("aplusb");
       pvRecord.setTraceLevel(4);
       Client client = new Client(pvRecord);
       PVStructure pvStructure = pvRecord.getPVRecordStructure().getPVStructure();
       PVDouble pva = pvStructure.getSubField(PVDouble.class, "a");
       PVDouble pvb = pvStructure.getSubField(PVDouble.class, "b");
       PVDouble pvc = pvStructure.getSubField(PVDouble.class, "c");
       pvRecord.beginGroupPut();
       pva.put(1.0);
       pvb.put(1.0);
       pvRecord.process();
       pvRecord.endGroupPut();
       System.out.println("c " + pvc.get());
       client.destroy();
    }
	
	private static class Client implements PVListener, PVRecordClient 
	{
	    private PVRecord pvRecord;
	    private PVCopy pvCopy;
	    
	    Client(PVRecord pv)
	    {
	        pvRecord = pv; 
	        PVStructure pvRequest = CreateRequest.create().createRequest("field()");
	        pvCopy = PVCopyFactory.create(pvRecord.getPVRecordStructure().getPVStructure(), pvRequest, "");
	        pvRecord.addPVRecordClient(this);
	        pvRecord.addListener(this,pvCopy);
	    }
	    void destroy()
	    {
	        pvRecord.removeListener(this,pvCopy);
	        pvRecord.removePVRecordClient(this);
	    }
	    public void detach(PVRecord pvRecord) {
	        System.out.println("Client::detach " + pvRecord.getRecordName());
	    }
	    public void dataPut(PVRecordField pvRecordField) {
	        System.out.println("Client::dataPut " + pvRecord.getRecordName() + pvRecordField.getFullFieldName());
	    }
	    public void dataPut(PVRecordStructure requested,PVRecordField pvRecordField) {
	        System.out.println("Client::dataPut " + pvRecord.getRecordName() + " requested " + requested.getFullFieldName() + " actual " + pvRecordField.getFullFieldName());
	    }
	    public void beginGroupPut(PVRecord pvRecord) {
	        System.out.println("Client::beginGroupPut " + pvRecord.getRecordName());
	    }
	    public void endGroupPut(PVRecord pvRecord) {
	        System.out.println("Client::endGroupPut " + pvRecord.getRecordName());
	    }
	    public void unlisten(PVRecord pvRecord) {
	        System.out.println("Client::unlisten " + pvRecord.getRecordName());
	    }
	    
	}
	
}
