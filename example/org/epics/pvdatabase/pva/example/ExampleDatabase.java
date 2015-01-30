/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */

package org.epics.pvdatabase.pva.example;

import org.epics.pvdata.factory.StandardPVFieldFactory;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.StandardPVField;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVDatabaseFactory;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.example.AplusBRecord;
import org.epics.pvdatabase.example.ArrayServerRecord;
import org.epics.pvdatabase.pva.ContextLocal;

/**
 * @author Marty Kraimer
 *
 */
public class ExampleDatabase {
    
    static void usage() {
        System.out.println("Usage:"
                + " -prefix name"
                + " -traceLevel traceLevel"
                );
    }
    
	private static final StandardPVField standardPVField = StandardPVFieldFactory.getStandardPVField();
	
	private static String prefix = "";
	private static int traceLevel = 0;
	
	public static void main(String[] args)
	{
	    if(args.length==1 && args[0].equals("-help")) {
	        usage();
	        return;
	    }
	    int nextArg = 0;
	    while(nextArg<args.length) {
	        String arg = args[nextArg++];
	        if(arg.equals("-prefix")) {
	            prefix = args[nextArg++];
	            continue;
	        }
	        if(arg.equals("-traceLevel")) {
	            traceLevel = Integer.parseInt(args[nextArg++]);
	            continue;
	        } else {
                System.out.println("Illegal options");
                usage();
                return;
            }
	    }
	    PVDatabase master = PVDatabaseFactory.getMaster();    
	    PVRecord pvRecord = AplusBRecord.create(prefix +"aplusb");
	    pvRecord.setTraceLevel(traceLevel);
	    master.addRecord(pvRecord);

	    pvRecord = ArrayServerRecord.create(prefix + "arrayServer", 10, 1.0);
	    pvRecord.setTraceLevel(traceLevel);
	    master.addRecord(pvRecord);

	    PVStructure pvStructure = standardPVField.scalar(ScalarType.pvDouble, "alarm,timeStamp");
	    pvRecord = new PVRecord(prefix + "doubleRecord",pvStructure);
	    pvRecord.setTraceLevel(traceLevel);
	    master.addRecord(pvRecord);

	    pvStructure = standardPVField.scalarArray(ScalarType.pvDouble, "alarm,timeStamp");
	    pvRecord = new PVRecord(prefix + "doubleArrayRecord",pvStructure);
	    pvRecord.setTraceLevel(traceLevel);
	    master.addRecord(pvRecord);

	    ContextLocal context = new ContextLocal();
	    context.start(true);
	    System.out.println("ExampleDatabase exiting");
	}
}
