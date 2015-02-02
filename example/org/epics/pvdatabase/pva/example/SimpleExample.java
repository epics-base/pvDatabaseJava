/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */

package org.epics.pvdatabase.pva.example;

import org.epics.pvdata.factory.StandardPVFieldFactory;
import org.epics.pvdata.misc.ThreadPriority;
import org.epics.pvdata.misc.Timer;
import org.epics.pvdata.misc.Timer.TimerCallback;
import org.epics.pvdata.misc.Timer.TimerNode;
import org.epics.pvdata.misc.TimerFactory;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVDatabaseFactory;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.pva.ContextLocal;

/**
 */
public class SimpleExample {
    
	static class PassiveScalarRecord extends PVRecord
	{
		static PassiveScalarRecord create(String recordName, ScalarType scalarType, String properties)
		{
			return new PassiveScalarRecord(
					recordName,
					StandardPVFieldFactory.getStandardPVField().scalar(scalarType, properties)
					);
		}
		
		private PassiveScalarRecord(String recordName, PVStructure pvStructure) {
			super(recordName, pvStructure);
		}
	}
	
	
	static class ScanPVRecord extends PVRecord implements TimerCallback 
	{
		private static Timer timer = TimerFactory.create("ScanPVRecord-timer", ThreadPriority.middle);
		private final TimerNode timerNode = TimerFactory.createNode(this);
		
		private ScanPVRecord(String recordName, PVStructure pvStructure, double period) {
			super(recordName, pvStructure);
			timer.schedulePeriodic(timerNode, 0, period);
		}

		@Override
		public void destroy() {
			timerNode.cancel();
			super.destroy();
		}

		@Override
		public void callback() {
			process();
		}

		@Override
		public void timerStopped() {
			// noop
		}
	}
	
	// 1Hz 32-bit counter
	static class CounterRecord extends ScanPVRecord
	{
		static CounterRecord create(String recordName)
		{
			return new CounterRecord(
					recordName,
					StandardPVFieldFactory.getStandardPVField().scalar(ScalarType.pvInt, "timeStamp")
					);
		}
		
		private final PVInt value;
		
		private CounterRecord(String recordName, PVStructure pvStructure) {
			super(recordName, pvStructure, 1.0);
			value = pvStructure.getIntField("value");
		}

		@Override
		public void process() {
			beginGroupPut();
			
			// increment value
			value.put(value.get() + 1);
			
			// update timeStamp
			super.process();
			
			endGroupPut();
		}
	}
	
	public static void main(String[] args)
	{
	    PVDatabase master = PVDatabaseFactory.getMaster();
	    master.addRecord(PassiveScalarRecord.create("testScalar", ScalarType.pvInt, "timeStamp"));
	    master.addRecord(CounterRecord.create("testCounter"));

	    ContextLocal context = new ContextLocal();
	    context.start(true);
	}
}
