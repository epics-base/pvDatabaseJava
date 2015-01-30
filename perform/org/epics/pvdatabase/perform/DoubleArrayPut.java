package org.epics.pvdatabase.perform;
import org.epics.pvaccess.easyPVA.*;
import org.epics.pvdata.property.*;

public class DoubleArrayPut {
    
    static void usage() {
        System.out.println("Usage:"
                + " -channelName name"
                + " -waitSecs seconds"
                + " -maxSize size"
                + " -numberSizes number"
                );
    }
    
    static private EasyPVA easyPVA = EasyPVAFactory.get();
    
    static private double waitSecs = .001;
    static private int maxSize = 5000000;
    static private int numberSizes = 5;
    
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
            } else if(arg.equals("-maxSize")) {
                maxSize = Integer.parseInt(args[nextArg++]);
                continue;
            } else if(arg.equals("-numberSizes")) {
                numberSizes = Integer.parseInt(args[nextArg++]);
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
        while(true) {
            start.getCurrentTime();
            long nbytes = 0;
            EasyChannel channel = easyPVA.createChannel(channelName);
            EasyPut put = channel.createPut();
            for(int i=0; i< numberSizes; ++i) {
                int len = maxSize/(i+1);
                nbytes += len*8;
                double[] value = new double[len];
                for(int j=0; j< len; j++) value[j] = i*j;
                put.putDoubleArray(value, len);
                try {
                Thread.sleep(waitTime);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
            channel.destroy();
            try {
                Thread.sleep(waitTime);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            end.getCurrentTime();
            double diff = start.diff(end, start);
            double megaBytes = nbytes;
            megaBytes /= 1e6;
            System.out.println("time " + diff + " nbytes " + nbytes + " megaBytes " + megaBytes);
        }   
    }
}
