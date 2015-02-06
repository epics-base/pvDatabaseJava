/**
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase.pva;

import java.io.*;


import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.server.impl.remote.ServerContextImpl;
import org.epics.pvaccess.server.impl.remote.plugins.DefaultBeaconServerDataProvider;

/**
 * A Thread Context for a pvAccess channel Provider.
 * @author mrk
 * 2015.01.20
 *
 */
public class ContextLocal implements Runnable{
    /**
     * Constructor.
     */
    public ContextLocal()
    {    
        context.setBeaconServerStatusProvider(new DefaultBeaconServerDataProvider(context));
        try {
            context.initialize(channelProvider);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        // Display basic information about the context.
        System.out.println(context.getVersion().getVersionString());
        context.printInfo(); System.out.println();
        thread = new Thread(this,"localContext");
    }

    /**
     * Start the context thread.
     * After this is called clients can connect to the server.
     * @param waitForExit In true then start waits for "exit" to be entered on standard in and call destroy before returning.
     */
    public void start(boolean waitForExit)
    {
        thread.start();
        if(!waitForExit) return;
        while(true) {
            System.out.print("Waiting for exit: ");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String value = null;
            try {
                value = br.readLine();
             } catch (IOException ioe) {
                System.out.println("IO error trying to read input!");
             }
            if(value.equals("exit")) break;
        }
        destroy();
    }
    private ChannelProvider channelProvider = ChannelProviderLocalFactory.getChannelServer();
    private final ServerContextImpl context = new ServerContextImpl();
    private Thread thread = null;
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
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
    /**
     * This destroys the context.
     * All clients will be disconnected.
     */
    public void destroy()
    {
        try {
            context.destroy();
            channelProvider.destroy();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
}
