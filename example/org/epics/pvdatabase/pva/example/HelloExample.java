/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */

package org.epics.pvdatabase.pva.example;

import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVDatabaseFactory;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.example.HelloRecord;
import org.epics.pvdatabase.pva.ContextLocal;

/**
 * @author Marty Kraimer
 *
 */
public class HelloExample {
    
    static void usage() {
        System.out.println("Usage:"
                + " -recordName name"
                + " -traceLevel traceLevel"
                );
    }
    
	private static String recordName = "helloExample";
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
	        if(arg.equals("-recordName")) {
	            recordName = args[nextArg++];
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
	    PVRecord pvRecord = HelloRecord.create(recordName);
	    pvRecord.setTraceLevel(traceLevel);
	    master.addRecord(pvRecord);

	    ContextLocal context = new ContextLocal();
	    context.start(true);
	    System.out.println("HelloExample exiting");
	}
}
