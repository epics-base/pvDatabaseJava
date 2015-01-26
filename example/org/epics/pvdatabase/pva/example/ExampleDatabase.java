/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */

package org.epics.pvdatabase.pva.example;

import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.server.impl.remote.ServerContextImpl;
import org.epics.pvaccess.server.impl.remote.plugins.DefaultBeaconServerDataProvider;
import org.epics.pvdata.factory.StandardPVFieldFactory;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.StandardPVField;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVDatabaseFactory;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.example.AplusBRecord;
import org.epics.pvdatabase.pva.ChannelServerFactory;

/**
 * @author Marty Kraimer
 *
 */
public class ExampleDatabase {

	private static final StandardPVField standardPVField = StandardPVFieldFactory.getStandardPVField();
	
	
	public static void main(String[] args)
	{
	    simple();
	}
	
	static void simple()
    {
	   PVDatabase master = PVDatabaseFactory.getMaster();    
       PVRecord pvRecord = AplusBRecord.create("aplusb");
       pvRecord.setTraceLevel(4);
       master.addRecord(pvRecord);
       
       PVStructure pvStructure = standardPVField.scalar(ScalarType.pvDouble, "alarm,timeStamp");
       pvRecord = new PVRecord("doubleRecord",pvStructure);
       pvRecord.setTraceLevel(4);
       master.addRecord(pvRecord);
       
       ChannelProvider channelProvider = ChannelServerFactory.getChannelServer();
       // Create a context with default configuration values.
       final ServerContextImpl context = new ServerContextImpl();
       context.setBeaconServerStatusProvider(new DefaultBeaconServerDataProvider(context));
       
       try {
           context.initialize(channelProvider);
       } catch (Throwable th) {
           th.printStackTrace();
       }

       // Display basic information about the context.
       System.out.println(context.getVersion().getVersionString());
       context.printInfo(); System.out.println();

       new Thread(new Runnable() {
           
           @Override
           public void run() {
               try {
                   System.out.println("Running server...");
                   context.run(0);
                   System.out.println("Done.");
               } catch (Throwable th) {
                   System.out.println("Failure:");
                   th.printStackTrace();
               }
           }
       }, "pvAccess server").start();
    }
	
	
	
}
