package org.epics.pvdatabase.perform;
import org.epics.pvaccess.client.Channel;
import org.epics.pvaClient.*;
import org.epics.pvdata.copy.CreateRequest;
import org.epics.pvdata.monitor.Monitor;
import org.epics.pvdata.monitor.MonitorElement;
import org.epics.pvdata.monitor.MonitorRequester;
import org.epics.pvdata.pv.DoubleArrayData;
import org.epics.pvdata.pv.MessageType;
import org.epics.pvdata.pv.PVDoubleArray;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Structure;

public class ArrayIntegrity {
    
    static void usage() {
        System.out.println("Usage:"
                + " -channelName name"
                + " -debugLevel level"
                );
    }
    
    static private PvaClient pvaClient = PvaClient.get();
    
    static private int debugLevel = 0;
    
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
            } else if(arg.equals("-debugLevel")) {
                debugLevel = Integer.parseInt(args[nextArg++]);
                continue;
            } else {
                System.out.println("Illegal options");
                usage();
                return;
            }
        }
        while(true) {
            PvaClientChannel pvaClientChannel = pvaClient.createChannel(channelName);
            pvaClientChannel.issueConnect();
            Status status = pvaClientChannel.waitConnect(2.0);
            if(!status.isOK()) {
                System.out.println("did not connect to " + channelName);
                continue;
            }
            Channel channel = pvaClientChannel.getChannel();
            PVStructure pvRequest = CreateRequest.create().createRequest("field()");
            MyRequester myRequester = new MyRequester();
            Monitor monitor = channel.createMonitor(myRequester, pvRequest);
            try {
                Thread.sleep(100);
                monitor.start();
            } catch (Throwable th) {
                th.printStackTrace();
                continue;
            }
            int len = 1000;
            double[] first = new double[len];
            double[] second = new double[len];
            PvaClientPut put1 = pvaClientChannel.createPut();
            PvaClientPutData putData1 = put1.getData();
            PvaClientPut put2 = pvaClientChannel.createPut();
            PvaClientPutData putData2 = put2.getData();
            for(int ind = 0; ind<10; ++ind) {
                for(int i=0; i<len; ++i) {
                    first[i] = (double)ind;
                    second[i] = (double)(ind+1);
                }
                putData1.putDoubleArray(first);
                putData2.putDoubleArray(second);
                put1.issuePut();
                put2.issuePut();
                put1.waitPut();
                put2.waitPut();
                try {
                    Thread.sleep(10);
                } catch (Throwable th) {
                    th.printStackTrace();
                    continue;
                }
            }
        }   
    }
    
    private static class MyRequester implements MonitorRequester {
        private DoubleArrayData data = new DoubleArrayData();
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
            while(true) {
                MonitorElement element = arg0.poll();
                if(element==null) return;
                PVStructure pvStructure = element.getPVStructure();
                PVDoubleArray pvDoubleArray = pvStructure.getSubField(PVDoubleArray.class,"value");
                if(pvDoubleArray==null) {
                    System.out.println("monitorElement illegal pvStructure");
                    return;
                }
                int len = pvDoubleArray.getLength();
                if(len==0) {
                    System.out.println("MyRequester monitorEvent array length is 0");
                    return;
                }
                pvDoubleArray.get(0, pvDoubleArray.getLength(),data);
                if(data.data[0]!=data.data[len-1]) {
                    System.out.println("data[0] " + data.data[0] + " data[" + (len-1) + "] " + data.data[len-1]);
                }
            }
        }

        @Override
        public void unlisten(Monitor arg0) {
            System.out.println("unlisten");
        }
    }
}
