package org.epics.pvdatabase.perform;
import org.epics.pvaccess.easyPVA.*;
import org.epics.pvdata.property.*;
import org.epics.pvdata.copy.CreateRequest;
import org.epics.pvdata.pv.*;
import org.epics.pvdata.misc.*;
import org.epics.pvdata.monitor.*;
import org.epics.pvaccess.client.*;

public class MonitorCount {

    static void usage() {
        System.out.println("Usage:"
                + " -channelName name"
                + " -waitSecs seconds"
                + " -numberMonitors number"
                + " -debugLevel level"
                );
    }

    static private EasyPVA easyPVA = EasyPVAFactory.get();

    static private double waitSecs = .001;
    static private int numberMonitors = 100; 
    static private int debugLevel = 1;

    public static void main(String[] args) {
        if(args.length<1) {
            usage();
            return;
        }
        String channelName = "";
        int nextArg = 0;
        while(nextArg<args.length) {
            String arg = args[nextArg++];
            if(arg.equals("-channelName")) {
                channelName = args[nextArg++];
                continue;
            } else if(arg.equals("-waitSecs")) {
                waitSecs = Double.parseDouble(args[nextArg++]);
                continue;
            } else if(arg.equals("-numberMonitors")) {
                numberMonitors = Integer.parseInt(args[nextArg++]);
                continue;
            } else if(arg.equals("-debugLevel")) {
                debugLevel = Integer.parseInt(args[nextArg++]);
                continue;
            } else {
                System.out.println("Illegal options");
                usage();
                return;
            }
        }
        long waitTime = (long)(waitSecs*1000.0);
        MyRequester myRequester = new MyRequester();
        TimeStamp start = TimeStampFactory.create();
        TimeStamp end = TimeStampFactory.create();
        while(true) {
            start.getCurrentTime();
            int ntimes = 0;
            int noverrun = 0;
            EasyChannel easyChannel = easyPVA.createChannel(channelName);
            easyChannel.connect(1.0);
            if(!easyChannel.waitConnect(5.0)) {
                System.out.println("did not connect to " + channelName);
                continue;
            }
            Channel channel = easyChannel.getChannel();
            PVStructure pvRequest = CreateRequest.create().createRequest("field()");
            Monitor monitor = channel.createMonitor(myRequester, pvRequest);
            try {
                Thread.sleep(10);
                monitor.start();
            } catch (Throwable th) {
                th.printStackTrace();
                continue;
            }
            while(true) {
                MonitorElement monitorElement = monitor.poll();
                if(monitorElement==null) {
                    try {
                        Thread.sleep(1);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    continue;
                }
                ntimes++;
                BitSet overrun = monitorElement.getOverrunBitSet();
                if(!overrun.isEmpty()) noverrun++;
                monitor.release(monitorElement);
                if(ntimes>numberMonitors) break;
            }
            end.getCurrentTime();
            double diff = start.diff(end, start);
            double monitorsPerSecond = numberMonitors/diff;
            System.out.println("time " + diff
                    + " numberMonitors " + numberMonitors 
                    + " overrun " +noverrun
                    + " monitorsPerSecond " + monitorsPerSecond);
            channel.destroy();
            try {
                Thread.sleep(waitTime);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

    }

    private static class MyRequester implements MonitorRequester {

        @Override
        public String getRequesterName() {
            return "myRequester";
        }

        @Override
        public void message(String arg0, MessageType arg1) {
            if(debugLevel>0) System.out.println("message " + arg0);
        }

        @Override
        public void monitorConnect(Status arg0, Monitor arg1, Structure arg2) {
            if(debugLevel>0) System.out.println("monitorConnect");
        }

        @Override
        public void monitorEvent(Monitor arg0) {
            if(debugLevel>1) System.out.println("monitorEvent");
        }

        @Override
        public void unlisten(Monitor arg0) {
            System.out.println("unlisten");
        }
    }
}
