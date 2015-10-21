package org.epics.pvdatabase.perform;
import org.epics.pvaClient.*;
import org.epics.pvdata.property.*;

public class DoublePut {
    
    static void usage() {
        System.out.println("Usage:"
                + " -channelName name"
                + " -waitSecs seconds"
                + " -numberPuts number"
                );
    }
    
    static private PvaClient pvaClient = PvaClient.get();
    
    static private double waitSecs = .001;
    static private int numberPuts = 1000; 
    
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
            } else if(arg.equals("-numberPuts")) {
                numberPuts = Integer.parseInt(args[nextArg++]);
                continue;
            } else {
                System.out.println("Illegal options");
                usage();
                return;
            }
        }
        
        long waitTime = (long)(waitSecs*1000.0);
        TimeStamp start = TimeStampFactory.create();
        TimeStamp end = TimeStampFactory.create();
        double value;
        while(true) {
            start.getCurrentTime();
            PvaClientChannel channel = pvaClient.createChannel(channelName);
            PvaClientPut put = channel.createPut();
            PvaClientPutData putData = put.getData();
            for(int i=0; i< numberPuts; ++i) {
                value = i;
                putData.putDouble(value); put.put();
            }
            end.getCurrentTime();
            double diff = start.diff(end, start);
            System.out.println("time " + diff);
            channel.destroy();
            try {
                Thread.sleep(waitTime);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
        }
        
    }

}
