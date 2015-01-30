/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase.pva;

import java.util.concurrent.locks.ReentrantLock;

import org.epics.pvdata.copy.PVCopy;
import org.epics.pvdata.copy.PVCopyFactory;
import org.epics.pvdata.copy.PVCopyTraverseMasterCallback;
import org.epics.pvdata.factory.StatusFactory;
import org.epics.pvdata.misc.BitSet;
import org.epics.pvdata.misc.BitSetUtil;
import org.epics.pvdata.misc.BitSetUtilFactory;
import org.epics.pvdata.monitor.Monitor;
import org.epics.pvdata.monitor.MonitorElement;
import org.epics.pvdata.monitor.MonitorQueue;
import org.epics.pvdata.monitor.MonitorQueueFactory;
import org.epics.pvdata.monitor.MonitorRequester;
import org.epics.pvdata.pv.MessageType;
import org.epics.pvdata.pv.PVField;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Status.StatusType;
import org.epics.pvdata.pv.StatusCreate;
import org.epics.pvdatabase.PVListener;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.PVRecordField;
import org.epics.pvdatabase.PVRecordStructure;

/**
 * @author mrk
 * 2015.01
 */
public class MonitorFactory {
	
	/**
	 * Create a monitor.
	 * @param pvRecord The record to monitor.
	 * @param monitorRequester The requester.
	 * @param pvRequest Then request structure defining the monitor options.
	 * @return The Monitor interface.
	 */
	public static Monitor create(PVRecord pvRecord,MonitorRequester monitorRequester,PVStructure pvRequest)
	{
		MonitorLocal monitor = new MonitorLocal(pvRecord,monitorRequester);
		if(!monitor.init(pvRequest)) {
			monitorRequester.monitorConnect(failedToCreateMonitorStatus, null, null);
			return null;
		}
		return monitor;
	}
	
	
	private static final StatusCreate statusCreate = StatusFactory.getStatusCreate();
    private static final Status okStatus = statusCreate.getStatusOK();
    private static final Status failedToCreateMonitorStatus = statusCreate.createStatus(StatusType.FATAL, "failed to create monitor", null);
    private static final Status wasDestroyedStatus = statusCreate.createStatus(StatusType.ERROR,"was destroyed",null);
    private static final Status alreadyStartedStatus = statusCreate.createStatus(StatusType.WARNING,"already started",null);
    private static final Status notStartedStatus = statusCreate.createStatus(StatusType.WARNING,"not started",null);
	private static final BitSetUtil bitSetUtil = BitSetUtilFactory.getCompressBitSet();
	
	
	
	private static class MonitorLocal implements Monitor,PVCopyTraverseMasterCallback, PVListener {
	    
		enum MonitorState {idle,active,destroyed}
		
		private final MonitorRequester monitorRequester;
		private final PVRecord pvRecord;
		private MonitorState state = MonitorState.idle;
		private PVCopy pvCopy = null;
		private MonitorQueue queue = null;
		private MonitorElement activeElement;
		
        private boolean isGroupPut = false;
        private boolean dataChanged = false;
        private ReentrantLock lock = new ReentrantLock();
		
        
		private MonitorLocal(PVRecord pvRecord,MonitorRequester monitorRequester) {
			this.pvRecord = pvRecord;
			this.monitorRequester = monitorRequester;
		}
		
        /* (non-Javadoc)
         * @see org.epics.pvdata.misc.Destroyable#destroy()
         */
        public void destroy() {
            if(pvRecord.getTraceLevel()>0)
            {
                System.out.println("MonitorLocal::destroy state " + state);    
            }
            lock.lock();
            try {
                if(state==MonitorState.destroyed) return;
                
            } finally {
                lock.unlock();
            }
            pvRecord.removeListener(this);
            lock.lock();
            try {
                state = MonitorState.destroyed;
            } finally {
                lock.unlock();
            }
        }
       
        /* (non-Javadoc)
         * @see org.epics.pvdata.monitor.Monitor#start()
         */
        public Status start() {
            if(pvRecord.getTraceLevel()>0)
            {
                System.out.println("MonitorLocal::state state " + state);    
            }
            lock.lock();
            try {
                if(state==MonitorState.destroyed) return wasDestroyedStatus;
                if(state==MonitorState.active) return alreadyStartedStatus;
                
            } finally {
                lock.unlock();
            }
            pvRecord.addListener(this);
            pvRecord.lock();
            try {
                lock.lock();
                try {
                    state = MonitorState.active;
                    queue.clear();
                    isGroupPut = false;
                    activeElement = queue.getFree();
                    activeElement.getChangedBitSet().clear();
                    activeElement.getOverrunBitSet().clear();
                    pvCopy.traverseMaster(this);
                    activeElement.getChangedBitSet().clear();
                    activeElement.getOverrunBitSet().clear();
                    activeElement.getChangedBitSet().set(0);
                    releaseActiveElement();
                } finally {
                    lock.unlock();
                }
            } finally {
                pvRecord.unlock();
            }
            return okStatus;
        }
       
        /* (non-Javadoc)
         * @see org.epics.pvdata.monitor.Monitor#stop()
         */
        public Status stop() {
            if(pvRecord.getTraceLevel()>0)
            {
                System.out.println("MonitorLocal::stop state " + state);    
            }
            lock.lock();
            try {
                if(state==MonitorState.destroyed) return wasDestroyedStatus;
                if(state==MonitorState.idle) return notStartedStatus;
                state = MonitorState.idle;
            } finally {
                lock.unlock();
            }
            pvRecord.removeListener(this);
            return okStatus;
        }
		/* (non-Javadoc)
		 * @see org.epics.pvdata.monitor.Monitor#poll()
		 */
		public MonitorElement poll() {
		    if(pvRecord.getTraceLevel()>0)
            {
                System.out.println("MonitorLocal::poll state " + state);    
            }
		    synchronized(queue) {
		        if(state!=MonitorState.active) return null;
		        return queue.getUsed();
		    }
		}
		/* (non-Javadoc)
		 * @see org.epics.pvdata.monitor.Monitor#release(org.epics.pvdata.monitor.MonitorElement)
		 */
		public void release(MonitorElement currentElement) {
		    if(pvRecord.getTraceLevel()>0)
            {
                System.out.println("MonitorLocal::release state " + state);    
            }
		    synchronized(queue) {
		        if(state!=MonitorState.active) return;
		        queue.releaseUsed(currentElement);
		    }
		}
		
		private MonitorElement releaseActiveElement() {
		    if(pvRecord.getTraceLevel()>0)
            {
                System.out.println("MonitorLocal::releaseActiveElement state " + state);    
            }
		    synchronized(queue) {
		        if(state!=MonitorState.active) return null;
		        MonitorElement newActive = queue.getFree();
		        if(newActive==null) return activeElement;
		        pvCopy.updateCopyFromBitSet(activeElement.getPVStructure(), activeElement.getChangedBitSet());
		        bitSetUtil.compress(activeElement.getChangedBitSet(),activeElement.getPVStructure());
		        bitSetUtil.compress(activeElement.getOverrunBitSet(),activeElement.getPVStructure());
		        queue.setUsed(activeElement);
		        activeElement = newActive;
		        activeElement.getChangedBitSet().clear();
		        activeElement.getOverrunBitSet().clear();
		    }
		    monitorRequester.monitorEvent(this);
		    return activeElement;
		}	
        

		@Override
        public void dataPut(PVRecordField pvRecordField) {
		    if(pvRecord.getTraceLevel()>0) {
                System.out.println("PVCopyMonitor::dataPut(pvRecordField)");
            }
            if(state!=MonitorState.active) return;
            lock.lock();
            try {
                int offset = pvCopy.getCopyOffset(pvRecordField.getPVField());
                BitSet changedBitSet = activeElement.getChangedBitSet();
                BitSet overrunBitSet = activeElement.getOverrunBitSet();
                boolean isSet = changedBitSet.get(offset);
                changedBitSet.set(offset);;
                if(isSet) overrunBitSet.set(offset);
                dataChanged = true;
            } finally {
                lock.unlock();
            }
            if(!isGroupPut) {
                releaseActiveElement();
                dataChanged = false;
            }
        }

        @Override
        public void dataPut(PVRecordStructure requested,PVRecordField pvRecordField)
        {
            if(pvRecord.getTraceLevel()>0) {
                System.out.println("PVCopyMonitor::dataPut(requested,pvRecordField)");
            }
            if(state!=MonitorState.active) return;
            lock.lock();
            try {
                BitSet changedBitSet = activeElement.getChangedBitSet();
                BitSet overrunBitSet = activeElement.getOverrunBitSet();
                int offsetCopyRequested = pvCopy.getCopyOffset(requested.getPVField());
                int offset = offsetCopyRequested +(pvRecordField.getPVField().getFieldOffset()
                        - requested.getPVField().getFieldOffset());
                boolean isSet = changedBitSet.get(offset);
                changedBitSet.set(offset);;
                if(isSet) overrunBitSet.set(offset);
                dataChanged = true;
            } finally {
                lock.unlock();
            }
            if(!isGroupPut) {
                releaseActiveElement();
                dataChanged = false;
            }
        }

        @Override
        public void beginGroupPut(PVRecord pvRecord) {
            if(pvRecord.getTraceLevel()>0) {
                System.out.println("PVCopyMonitor::beginGroupPut");
            }
            if(state!=MonitorState.active) return;
            lock.lock();
            try {
                isGroupPut = true;
                dataChanged = false;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void endGroupPut(PVRecord pvRecord) {
            if(pvRecord.getTraceLevel()>0) {
                System.out.println("PVCopyMonitor::endGroupPut");
            }
            if(state!=MonitorState.active) return;
            lock.lock();
            try {
                isGroupPut = false;
            } finally {
                lock.unlock();
            }
            if(dataChanged) {
                dataChanged = false;
                releaseActiveElement();
            }
        }

        /* (non-Javadoc)
         * @see org.epics.pvdatabase.PVListener#unlisten(org.epics.pvdatabase.PVRecord)
         */
        public void unlisten(PVRecord pvRecord) {
            pvRecord.removeListener(this);
        }

        /* (non-Javadoc)
         * @see org.epics.pvdata.copy.PVCopyTraverseMasterCallback#nextMasterPVField(org.epics.pvdata.pv.PVField)
         */
        public void nextMasterPVField(PVField pvField) {
            pvRecord.findPVRecordField(pvField).addListener(this);
        }

        private boolean init(PVStructure pvRequest) {
		    PVField pvField = null;
		    PVStructure pvOptions = null;
			int queueSize = 2;
			pvField = pvRequest.getSubField("record._options");
			if(pvField!=null) {
			    pvOptions = (PVStructure)pvField;
			    pvField = pvOptions.getSubField("queueSize");
			}
			if(pvField!=null && (pvField instanceof PVString)) {
				PVString pvString = (PVString)pvField;
				String value = pvString.get();
				try {
					queueSize = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					monitorRequester.message("queueSize " + e.getMessage(), MessageType.error);
					return false;
				}
			}
			pvField = pvRequest.getSubField("field");
			if(pvField==null) {
				pvCopy = PVCopyFactory.create(pvRecord.getPVRecordStructure().getPVStructure(), pvRequest, "");
				if(pvCopy==null) {
					monitorRequester.message("illegal pvRequest", MessageType.error);
					return false;
				}
			} else {
				if(!(pvField instanceof PVStructure)) {
					monitorRequester.message("illegal pvRequest.field", MessageType.error);
					return false;
				}
				pvCopy = PVCopyFactory.create(pvRecord.getPVRecordStructure().getPVStructure(), pvRequest, "field");
				if(pvCopy==null) {
					monitorRequester.message("illegal pvRequest", MessageType.error);
					return false;
				}
			}
			if(queueSize<2) queueSize = 2;
			MonitorElement[] elementArray = new MonitorElement[queueSize];
			for(int i=0; i<queueSize; ++i) {
			    elementArray[i] = MonitorQueueFactory.createMonitorElement(pvCopy.createPVStructure());
			}
			queue = MonitorQueueFactory.create(elementArray);
			monitorRequester.monitorConnect(okStatus, this, pvCopy.getStructure());
			return true;
		}
	}
}
