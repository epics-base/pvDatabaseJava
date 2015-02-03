/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase;

import java.util.concurrent.locks.ReentrantLock;

import org.epics.pvdata.factory.ConvertFactory;
import org.epics.pvdata.misc.LinkedList;
import org.epics.pvdata.misc.LinkedListCreate;
import org.epics.pvdata.misc.LinkedListNode;
import org.epics.pvdata.property.PVTimeStamp;
import org.epics.pvdata.property.PVTimeStampFactory;
import org.epics.pvdata.property.TimeStamp;
import org.epics.pvdata.property.TimeStampFactory;
import org.epics.pvdata.pv.Convert;
import org.epics.pvdata.pv.PVField;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.PostHandler;
import org.epics.pvdata.pv.Type;
import org.epics.pvdata.copy.*;
import org.epics.pvdata.copy.PVCopyTraverseMasterCallback;


/**
 * Base class for a pvDatabase PVRecord.
 * Derived classes only need to implement a constructor and optionally process and destroy.
 * @author mrk
 *  2015.01.20
 */
public class PVRecord implements PVCopyTraverseMasterCallback {
	private static final Convert convert = ConvertFactory.getConvert();
	private static LinkedListCreate<PVListener> listenerListCreate = new LinkedListCreate<PVListener>();
	private static LinkedListCreate<PVRecordClient> clientListCreate = new LinkedListCreate<PVRecordClient>();
    private String recordName;
    private BasePVRecordStructure pvRecordStructure = null;
    private LinkedList<PVListener> pvAllListenerList = listenerListCreate.create();
    private LinkedList<PVRecordClient> clientList = clientListCreate.create();
    private ReentrantLock lock = new ReentrantLock();
    private int depthGroupPut = 0;
    private int traceLevel = 0;
    private static volatile int numberRecords = 0;
    private int id = numberRecords++;
    
    private PVTimeStamp pvTimeStamp = PVTimeStampFactory.create();
    private TimeStamp timeStamp = TimeStampFactory.create();
    
    // following only valid while addListener or removeListener is active.
    private boolean isAddListener = false;
    private PVListener pvListener = null;
    
   
    /**
     * Create a PVRecord that has pvStructure as it's top level structure.
     * A derived class must call super(recordName, pvStructure).
     * @param recordName The record name.
     * @param pvStructure The top level structure.
     */
    public PVRecord(String recordName,PVStructure pvStructure) {
    	if(pvStructure.getParent()!=null) {
    		throw new IllegalStateException(recordName + " pvStructure not a top level structure");
    	}
    	this.recordName = recordName;
    	pvRecordStructure = new BasePVRecordStructure(pvStructure,null,this);
    	PVField pvField = pvRecordStructure.getPVStructure().getSubField("timeStamp");
    	if(pvField!=null) pvTimeStamp.attach(pvField);
    }
    /**
     *  A derived method is expected to override this method and can also call this method.
     *  It is the method that makes a record smart.
     *  If a timeStamp field is present this method sets it equal to the current time.
     *  If a derived class  encounters errors it should raise alarms
     */
    public void process()
    {
        if(traceLevel>2) {
            System.out.println("PVRecord::process() " + recordName);
        }
        if(pvTimeStamp.isAttached()) {
            timeStamp.getCurrentTime();
            pvTimeStamp.set(timeStamp);
        }
    }
    /**
     *  Destroy the PVRecord. Release any resources used and
     *  get rid of listeners.
     *  If derived class overrides this then it must call PVRecord::destroy()
     *  after it has destroyed resources it uses.
     */
    public void destroy()
    {
        if(traceLevel>1) {
            System.out.println("PVRecord::destroy() " + recordName);
        }
        pvTimeStamp.detach();
    }
    /**
     * Get the record instance name.
     * @return The name.
     */
    public final String getRecordName() {
        return recordName;
    }
    /**
     * Get the top level PVRecordStructure.
     * @return The PVRecordStructure interface.
     */
    public final PVRecordStructure getPVRecordStructure() {
        return pvRecordStructure;
    }
    /**
     * Find the PVRecordField for the pvField.
     * @param pvField The pvField interface.
     * @return The PVRecordField interface or null is not in record.
     */
	public final PVRecordField findPVRecordField(PVField pvField) {
		return findPVRecordField(pvRecordStructure,pvField);
	}
	
    private PVRecordField findPVRecordField(PVRecordStructure pvrs,PVField pvField) {
    	int desiredOffset = pvField.getFieldOffset();
    	PVField pvf = pvrs.getPVField();
    	int offset = pvf.getFieldOffset();
    	if(offset==desiredOffset) return pvrs;
    	PVRecordField[] pvrss = pvrs.getPVRecordFields();
    	for(int i=0; i<pvrss.length; i++) {
    		PVRecordField pvrf = pvrss[i];
    	    pvf = pvrf.getPVField();
    	    offset = pvf.getFieldOffset();
    	    if(offset==desiredOffset) return pvrf;
    	    int nextOffset = pvf.getNextFieldOffset();
    	    if(nextOffset<=desiredOffset) continue;
    	    return findPVRecordField((PVRecordStructure)pvrf,pvField);
    	}
    	throw new IllegalStateException(recordName + " pvField " + pvField.getFieldName() + " not in PVRecord");
    }
    /**
     * Lock the record instance.
     * This must be called before accessing the record.
     */
    public final void lock() {
        if(traceLevel>2) {
            System.out.println("PVRecord::lock() " + recordName);
        }
        lock.lock();
    }
    /**
     * Unlock the record.
     */
    public final void unlock() {
        if(traceLevel>2) {
            System.out.println("PVRecord::unlock() " + recordName);
        }
        lock.unlock();
    }
    /**
     * Try to lock the record instance.
     * This can be called before accessing the record instead of lock.
     * @return If true then it is just like lock. If false the record must not be accessed.
     */
    public final boolean tryLock() {
        if(traceLevel>2) {
            System.out.println("PVRecord::tryLock() " + recordName);
        }
        return lock.tryLock();
    }
    /**
     * While holding lock on this record lock another record.
     * If the other record is already locked than this record may be unlocked.
     * The caller must call the unlock method of the other record when done with it.
     * @param otherRecord the other record.
     */
    public final void lockOtherRecord(PVRecord otherRecord) {
        if(traceLevel>2) {
            System.out.println("PVRecord::lockOtherRecord() " + recordName);
        }
        PVRecord impl = (PVRecord)otherRecord;
        int otherId = impl.id;
        if(id<=otherId) {
            otherRecord.lock();
            return;
        }
        int count = lock.getHoldCount();
        for(int i=0; i<count; i++) lock.unlock();
        otherRecord.lock();
        for(int i=0; i<count; i++) lock.lock();
    }
    /**
     * Register a client of the record.
     * This must be called by any code that connects to the record.
     * @param pvRecordClient The record client.
     */
    public final boolean addPVRecordClient(PVRecordClient pvRecordClient) {
        if(traceLevel>2) {
            System.out.println("PVRecord::addPVRecordClient() " + recordName);
        }
        lock.lock();
        try {
            if(clientList.contains(pvRecordClient)) return false;
            LinkedListNode<PVRecordClient> listNode = clientListCreate.createNode(pvRecordClient);
            clientList.addTail(listNode);
            return true;
        } finally {
            lock.unlock();
        }
   }
    /**
     * remove a client of the record.
     * This must be called by any code that disconnects from the record.
     * @param pvRecordClient The record client.
     */
    public final boolean removePVRecordClient(PVRecordClient pvRecordClient) {
        if(traceLevel>2) {
            System.out.println("PVRecord::removePVRecordClient() " + recordName);
        }
        lock.lock();
        try {
            if(!clientList.contains(pvRecordClient)) return false;
            clientList.remove(pvRecordClient);
            return true;
        } finally {
            lock.unlock();
        }
    }
    /**
     * Detach all registered clients.
     */
    public final void detachClients() {
        if(traceLevel>1) {
            System.out.println("PVRecord::detachClients() " + recordName);
        }
        lock.lock();
        try {
            while(true) {
                LinkedListNode<PVRecordClient> listNode = clientList.removeHead();
                if(listNode==null) break;
                PVRecordClient pvRecordClient = listNode.getObject();
                pvRecordClient.detach(this);
            }
        } finally {
            lock.unlock();
        }
    }
    /**
     * Add a PVListener. This must be called before pvField.addListener.
     * @param listener The listener.
     * @param pvCopy The pvStructure that has the client fields.
     */
    public final boolean addListener(PVListener listener,PVCopy pvCopy) {
        if(traceLevel>1) {
            System.out.println("PVRecord::addListener() " + recordName);
        }
        lock.lock();
        try {
            if(pvAllListenerList.contains(listener)) return false;
            LinkedListNode<PVListener> listNode = listenerListCreate.createNode(listener);
            pvAllListenerList.addTail(listNode);
            this.pvListener = listener;
            isAddListener = true;
            pvCopy.traverseMaster(this);
            this.pvListener = null;
            return true;
        } finally {
            lock.unlock();
        }
    }
    /* (non-Javadoc)
     * @see org.epics.pvdata.copy.PVCopyTraverseMasterCallback#nextMasterPVField(org.epics.pvdata.pv.PVField)
     */
    public void nextMasterPVField(PVField pvField) {
        BasePVRecordField pvRecordField = (BasePVRecordField)findPVRecordField(pvField);
        if(isAddListener) {
            pvRecordField.addListener(this.pvListener);
        } else {
            pvRecordField.removeListener(this.pvListener);
        }
    }
    /**
     * Remove a PVListener.
     * @param listener The listener.
     *  @param pvCopy The pvStructure that has the client fields.
     */
    public final boolean removeListener(PVListener listener,PVCopy pvCopy) {
        if(traceLevel>1) {
            System.out.println("PVRecord::removeListener() " + recordName);
        }
        lock.lock();
        try {
            if(!pvAllListenerList.contains(listener)) return false;
            pvAllListenerList.remove(listener);
            this.pvListener = listener;
            isAddListener = false;
            pvCopy.traverseMaster(this);
            this.pvListener = null;
            return true;
        } finally {
            lock.unlock();
        }
    }
    /**
     * Begin a group of related puts.
     */
   public final void beginGroupPut() {
    	if(++depthGroupPut>1) return;
    	 if(traceLevel>2) {
             System.out.println("PVRecord::beginGroupPut() " + recordName);
         }
    	// no need to synchronize because record must be locked when this is called.
    	LinkedListNode<PVListener> listNode = pvAllListenerList.getHead();
    	while(listNode!=null) {
    		PVListener pvListener = listNode.getObject();
    		pvListener.beginGroupPut(this);
    		listNode = pvAllListenerList.getNext(listNode);
    	}
    }
   /**
    * End of a group of related puts.
    */
    public final void endGroupPut() {
        if(--depthGroupPut>0) return;
        if(traceLevel>2) {
            System.out.println("PVRecord::endGroupPut() " + recordName);
        }
        // no need to synchronize because record must be locked when this is called.
        LinkedListNode<PVListener> listNode = pvAllListenerList.getHead();
    	while(listNode!=null) {
    		PVListener pvListener = listNode.getObject();
    		pvListener.endGroupPut(this);
    		listNode = pvAllListenerList.getNext(listNode);
    	}
    }
    /**
     * get trace level (0,1,2) means (nothing,lifetime,process)
     * @return the level
     */
    public final int getTraceLevel(){ return traceLevel;}
    /**
     * set trace level (0,1,2) means (nothing,lifetime,process)
     * @param level The level
     */
    public final void setTraceLevel(int level) { traceLevel = level;}
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() { return toString(0);}
    /**
     * Implement standard toString()
     * @param indentLevel indentation level.
     * @return The record as a string.
     */
    public String toString(int indentLevel) {
    	StringBuilder builder = new StringBuilder();
    	convert.newLine(builder,indentLevel);
    	builder.append("record ");
    	builder.append(recordName);
    	builder.append(" ");
    	pvRecordStructure.getPVStructure().toString(builder, indentLevel);
    	return builder.toString();
    }  
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return id;
    }
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		return (obj instanceof PVRecord && ((PVRecord)obj).id == id);
	}
	
	private static class BasePVRecordField implements PVRecordField, PostHandler{
	    private LinkedListCreate<PVListener> linkedListCreate = new LinkedListCreate<PVListener>();
	    private PVField pvField = null;
	    private PVRecord pvRecord = null;
	    private PVRecordStructure parent = null;
	    private boolean isStructure = false;
	    private LinkedList<PVListener> pvListenerList = linkedListCreate.create();
	    private String fullName = null;
	    private String fullFieldName = null;

	    BasePVRecordField(PVField pvField,PVRecordStructure parent,PVRecord pvRecord) {
	        this.pvField = pvField;
	        this.parent = parent;
	        this.pvRecord = pvRecord;
	        if(pvField.getField().getType()==Type.structure) isStructure = true;
	        pvField.setPostHandler(this);
	    }
	    
	    public PVRecordStructure getParent() {
	        return parent;
	    }
	    public PVField getPVField() {
	        return pvField;
	    }
	    public String getFullFieldName() {
	        if(fullFieldName==null) createNames();
	        return fullFieldName;
	    }
	    public String getFullName() {
	        if(fullName==null) createNames();
	        return fullName;
	    }
	    public PVRecord getPVRecord() {
	        return pvRecord;
	    }
	   
	     public void postPut() {
	         if(parent!=null) {
	             BasePVRecordField pvf = (BasePVRecordField)parent;
	             pvf.postParent(this);
	         }
	         postSubField();
	     }
	     private void postParent(PVRecordField subField) {
	         LinkedListNode<PVListener> listNode = pvListenerList.getHead();
	         while(listNode!=null) {
	             PVListener pvListener = listNode.getObject();
	             pvListener.dataPut((PVRecordStructure)this,subField);
	             listNode = pvListenerList.getNext(listNode);
	         }
	         if(parent!=null) {
	             BasePVRecordField pv = (BasePVRecordField)parent;
	             pv.postParent(subField);
	         }
	     }
	     
	     private void postSubField() {
	         callListener();
	         if(isStructure) {
	             PVRecordStructure recordStructure = (PVRecordStructure)this;
	             PVRecordField[] pvRecordFields = recordStructure.getPVRecordFields();
	             for(int i=0; i<pvRecordFields.length; i++) {
	                 BasePVRecordField pv = (BasePVRecordField)pvRecordFields[i];
	                 pv.postSubField();
	             }
	         }
	     }

	     private void callListener() {
	         LinkedListNode<PVListener> listNode = pvListenerList.getHead();
	         while(listNode!=null) {
	             PVListener pvListener = listNode.getObject();
	             pvListener.dataPut(this);
	             listNode = pvListenerList.getNext(listNode);
	         }
	     }

	     private void createNames(){
	         StringBuilder builder = new StringBuilder();
	         PVField pvField = getPVField();
	         boolean isLeaf = true;
	         while(pvField!=null) {
	             String fieldName = pvField.getFieldName();
	             PVStructure pvParent = pvField.getParent();
	             if(!isLeaf && pvParent!=null) {
	                 builder.insert(0, '.');
	             }
	             isLeaf = false;
	             builder.insert(0, fieldName);
	             pvField = pvParent;
	         }
	         fullFieldName = builder.toString();
	         String xxx = pvRecord.getRecordName();
	         if(fullFieldName.length()>0) xxx += ".";
	         builder.insert(0, xxx);
	         fullName = builder.toString();
	     }

	     private boolean addListener(PVListener pvListener) {
	         if(pvRecord.getTraceLevel()>1) {
	             System.out.println("PVRecordField::addListener() " + getFullName() );
	         }
	         if(pvListenerList.contains(pvListener)) return false;
	         LinkedListNode<PVListener> listNode = linkedListCreate.createNode(pvListener);
	         pvListenerList.addTail(listNode);
	         return true;
	     }
	     // This is only called by PVRecord, which has the record locked.
	     private void removeListener(PVListener pvListener) {
	         if(pvRecord.getTraceLevel()>1) {
	             System.out.println("PVRecordField::removeListener() " + getFullName() );
	         }
	         pvListenerList.remove(pvListener);
	     }
	}
	private static class BasePVRecordStructure extends BasePVRecordField implements PVRecordStructure {
	    private BasePVRecordField[] pvRecordFields;
	    
	    BasePVRecordStructure(PVStructure pvStructure,PVRecordStructure parent,PVRecord pvRecord) {
	        super(pvStructure,parent,pvRecord);
	        PVField[] pvFields = pvStructure.getPVFields();
	        pvRecordFields = new BasePVRecordField[pvFields.length];
	        for(int i=0; i<pvFields.length; i++) {
	            PVField pvField = pvFields[i];
	            if(pvField.getField().getType()==Type.structure) {
	                pvRecordFields[i]  = new BasePVRecordStructure((PVStructure)pvField,this,pvRecord);
	            } else {
	                pvRecordFields[i] = new BasePVRecordField(pvField,this,pvRecord);
	            }
	        }
	    }
	    
	    public PVRecordField[] getPVRecordFields() {
	        return pvRecordFields;
	    }
	   
	    public PVStructure getPVStructure() {
	        return (PVStructure)super.getPVField();
	    }
	}
}
