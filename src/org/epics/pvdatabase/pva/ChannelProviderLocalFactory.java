/**
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase.pva;

/**
 * A ChannelProvider for PVDatabase.
 * @author mrk
 * 2015.01.20
 *
 */

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.epics.pvaccess.client.AccessRights;
import org.epics.pvaccess.client.Channel;
import org.epics.pvaccess.client.ChannelArray;
import org.epics.pvaccess.client.ChannelArrayRequester;
import org.epics.pvaccess.client.ChannelFind;
import org.epics.pvaccess.client.ChannelFindRequester;
import org.epics.pvaccess.client.ChannelGet;
import org.epics.pvaccess.client.ChannelGetRequester;
import org.epics.pvaccess.client.ChannelListRequester;
import org.epics.pvaccess.client.ChannelProcess;
import org.epics.pvaccess.client.ChannelProcessRequester;
import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.client.ChannelProviderFactory;
import org.epics.pvaccess.client.ChannelProviderRegistryFactory;
import org.epics.pvaccess.client.ChannelPut;
import org.epics.pvaccess.client.ChannelPutGet;
import org.epics.pvaccess.client.ChannelPutGetRequester;
import org.epics.pvaccess.client.ChannelPutRequester;
import org.epics.pvaccess.client.ChannelRPC;
import org.epics.pvaccess.client.ChannelRPCRequester;
import org.epics.pvaccess.client.ChannelRequester;
import org.epics.pvaccess.client.GetFieldRequester;
import org.epics.pvaccess.server.rpc.RPCRequestException;
import org.epics.pvaccess.server.rpc.RPCResponseCallback;
import org.epics.pvaccess.server.rpc.RPCService;
import org.epics.pvaccess.server.rpc.RPCServiceAsync;
import org.epics.pvaccess.server.rpc.Service;
import org.epics.pvdata.copy.PVCopy;
import org.epics.pvdata.copy.PVCopyFactory;
import org.epics.pvdata.factory.ConvertFactory;
import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.factory.StatusFactory;
import org.epics.pvdata.misc.BitSet;
import org.epics.pvdata.monitor.Monitor;
import org.epics.pvdata.monitor.MonitorRequester;
import org.epics.pvdata.pv.Convert;
import org.epics.pvdata.pv.MessageType;
import org.epics.pvdata.pv.PVArray;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVField;
import org.epics.pvdata.pv.PVScalarArray;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.PVStructureArray;
import org.epics.pvdata.pv.PVUnionArray;
import org.epics.pvdata.pv.Scalar;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Status.StatusType;
import org.epics.pvdata.pv.StatusCreate;
import org.epics.pvdata.pv.Type;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVDatabaseFactory;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.PVRecordClient;

/**
 * Factory and implementation of local channel access, i.e. channel access that
 * accesses database records in the local pvDatabase..
 * User callbacks are called with the appropriate record locked except for
 * 1) all methods of ChannelRequester, 2) all methods of ChannelFieldGroupListener,
 * and 3) ChannelRequester.requestDone
 * @author mrk
 *
 */
public class ChannelProviderLocalFactory  {

    /**
     * Get the single instance of the local channelProvider for the PVDatabase.
     * @return The ChannelProvider
     * @deprecated
     */
    static public ChannelProvider getChannelServer() {
        return getChannelProviderLocal();
    }
    /**
     * Get the single instance of the local channelProvider for the PVDatabase.
     * @return The ChannelProvider
     */
    static public ChannelProvider getChannelProviderLocal() {
        return ChannelProviderLocal.getChannelProviderLocal();
    }
    private static final String providerName = "local";
    private static final PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
    private static final Convert convert = ConvertFactory.getConvert();
    private static final StatusCreate statusCreate = StatusFactory.getStatusCreate();
    private static final Status okStatus = statusCreate.getStatusOK();
    private static final Status notFoundStatus = statusCreate.createStatus(StatusType.ERROR, "channel not found", null);
    private static final Status illegalOffsetStatus = statusCreate.createStatus(StatusType.ERROR, "illegal offset", null);
    private static final Status illegalStrideStatus = statusCreate.createStatus(StatusType.ERROR, "illegal stride", null);
    private static final Status illegalCountStatus = statusCreate.createStatus(StatusType.ERROR, "illegal count", null);
    //private static final Status capacityImmutableStatus = statusCreate.createStatus(StatusType.ERROR, "capacity is immutable", null);
    private static final Status subFieldDoesNotExistStatus = statusCreate.createStatus(StatusType.ERROR, "subField does not exist", null);
    private static final Status subFieldNotArrayStatus = statusCreate.createStatus(StatusType.ERROR, "subField is not an array", null);
    private static final Status channelDestroyedStatus = statusCreate.createStatus(StatusType.ERROR, "channel destroyed", null);
    private static final Status requestDestroyedStatus = statusCreate.createStatus(StatusType.ERROR, "request destroyed", null);
    private static final Status illegalRequestStatus = statusCreate.createStatus(StatusType.ERROR, "illegal pvRequest", null);
    private static final Status notImplementedStatus = statusCreate.createStatus(StatusType.ERROR, "not implemented", null);

    private static boolean getProcess(PVStructure pvRequest,boolean processDefault) {
        PVField pvField = pvRequest.getSubField("record._options.process");
        if(pvField==null || pvField.getField().getType()!=Type.scalar) return processDefault;
        Scalar scalar = (Scalar)pvField.getField();
        if(scalar.getScalarType()==ScalarType.pvString) {
            PVString pvString = (PVString)pvField;
            return (pvString.get().equalsIgnoreCase("true")) ? true : false;
        } else if(scalar.getScalarType()==ScalarType.pvBoolean) {
            PVBoolean pvBoolean = (PVBoolean)pvField;
            return pvBoolean.get();
        }
        return processDefault;
    }

    private static class ChannelFindLocal implements ChannelFind {

        private ChannelFindLocal() {
        }   
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelFind#cancel()
         */
        @Override
        public void cancel() {}
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelFind#getChannelProvider()
         */
        @Override
        public ChannelProvider getChannelProvider() {
            return getChannelProviderLocal();
        } 
    }

    private static class ChannelProviderLocal implements ChannelProvider{

        private static ChannelProviderLocal singleImplementation = null;
        private PVDatabase pvDatabase = PVDatabaseFactory.getMaster();
        private ReentrantLock lock = new ReentrantLock();
        private boolean beingDestroyed = false;
        private ChannelFind channelFinder = new ChannelFindLocal();

        private static synchronized ChannelProviderLocal getChannelProviderLocal() {
            if (singleImplementation==null) {
                singleImplementation = new ChannelProviderLocal();
                ChannelProviderRegistryFactory.registerChannelProviderFactory(
                        new ChannelProviderFactory() {

                            @Override
                            public ChannelProvider sharedInstance() {
                                return singleImplementation;
                            }

                            @Override
                            public ChannelProvider newInstance() {
                                throw new RuntimeException("not supported");
                            }

                            @Override
                            public String getFactoryName() {
                                return providerName;
                            }
                        });
            }
            return singleImplementation;
        }
        private ChannelProviderLocal(){
        } // don't allow creation except by getChannelServer. 
        public void destroy() {
            lock.lock();
            try {
                if(beingDestroyed) return;
                beingDestroyed = true;
                pvDatabase.destroy();
            } finally {
                lock.unlock();
            }
        }
        public String getProviderName() {
            return providerName;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelProvider#channelFind(java.lang.String, org.epics.pvaccess.client.ChannelFindRequester)
         */
        public ChannelFind channelFind(String channelName,ChannelFindRequester channelFindRequester) {
            lock.lock();
            try {
                PVRecord pvRecord = pvDatabase.findRecord(channelName);
                if(pvRecord!=null) {
                    channelFindRequester.channelFindResult(okStatus, channelFinder,true);
                } else {
                    channelFindRequester.channelFindResult(notFoundStatus,channelFinder,false);
                }
                return channelFinder;
            } finally {
                lock.unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelProvider#channelList(org.epics.pvaccess.client.ChannelListRequester)
         */
        public ChannelFind channelList(ChannelListRequester channelListRequester) {
            Set<String> channelNamesSet = new HashSet<String>(Arrays.asList(pvDatabase.getRecordNames()));
            channelListRequester.channelListResult(okStatus, channelFinder, channelNamesSet, false);
            return channelFinder;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelProvider#createChannel(java.lang.String, org.epics.pvaccess.client.ChannelRequester, short, java.lang.String)
         */
        @Override
        public Channel createChannel(
                String channelName,
                ChannelRequester channelRequester,
                short priority,
                String address)
        {
            if (address != null && address.length()>0)
                throw new IllegalArgumentException("address not allowed for local implementation");
            return createChannel(channelName, channelRequester, priority);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.ChannelProvider#createChannel(java.lang.String, org.epics.pvaccess.client.ChannelRequester, short)
         */
        @Override
        public Channel createChannel(String channelName,ChannelRequester channelRequester, short priority) {
            lock.lock();
            try {
                PVRecord pvRecord = pvDatabase.findRecord(channelName);
                if(pvRecord!=null) {
                    ChannelLocal channel = new ChannelLocal(this,pvRecord,channelRequester);
                    channelRequester.channelCreated(okStatus, channel);
                    pvRecord.addPVRecordClient(channel);
                    return channel;
                } else {
                    channelRequester.channelCreated(notFoundStatus, null);
                    return null;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private static class ChannelLocal implements Channel,PVRecordClient{
        private final ChannelProvider provider;
        private final ChannelRequester channelRequester;
        private final PVRecord pvRecord;
        private final AtomicBoolean isDestroyed = new AtomicBoolean(false);

        private ChannelLocal(ChannelProvider provider,PVRecord pvRecord,ChannelRequester channelRequester)
        {
            this.provider = provider;
            this.channelRequester = channelRequester;
            this.pvRecord = pvRecord;
        }       
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#destroy()
         */
        @Override
        public void destroy() {
            if(pvRecord.getTraceLevel()>0) {
                System.out.println("ChannelLocal::destroy() isDestroyed " + isDestroyed.get());
            }
            if(!isDestroyed.compareAndSet(false, true)) return;
            pvRecord.removePVRecordClient(this);
        }
        /* (non-Javadoc)
         * @see org.epics.pvdata.pv.PVRecordClient#detach(org.epics.pvdata.pv.PVRecord)
         */
        @Override
        public void detach(PVRecord pvRecord) {
            if(pvRecord.getTraceLevel()>0) {
                System.out.println("ChannelLocal::detach()");
            }
            channelRequester.channelStateChange(this, ConnectionState.DESTROYED);
        }
        /* (non-Javadoc)
         * @see org.epics.pvdata.pv.Requester#getRequesterName()
         */
        @Override
        public String getRequesterName() {
            return channelRequester.getRequesterName();
        }
        /* (non-Javadoc)
         * @see org.epics.pvdata.pv.Requester#message(java.lang.String, org.epics.pvdata.pv.MessageType)
         */
        @Override
        public void message(String message, MessageType messageType) {
            channelRequester.message(message, messageType);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#getProvider()
         */
        @Override
        public ChannelProvider getProvider() {
            return provider;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#getRemoteAddress()
         */
        @Override
        public String getRemoteAddress() {
            return providerName;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#getConnectionState()
         */
        @Override
        public ConnectionState getConnectionState() {
            if (isDestroyed.get())
                return ConnectionState.DESTROYED;
            else
                return ConnectionState.CONNECTED;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#getChannelName()
         */
        @Override
        public String getChannelName() {
            return pvRecord.getRecordName();
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#getChannelRequester()
         */
        @Override
        public ChannelRequester getChannelRequester() {
            return channelRequester;
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#isConnected()
         */
        @Override
        public boolean isConnected() {
            return !isDestroyed.get();
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#getAccessRights(org.epics.pvdata.pv.PVField)
         */
        @Override
        public AccessRights getAccessRights(PVField pvField) {
            throw new UnsupportedOperationException("method getAccessRights not implemented");
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#getField(org.epics.pvaccess.client.GetFieldRequester, java.lang.String)
         */
        @Override
        public void getField(GetFieldRequester requester,String subField) {
            if(subField==null || subField.length()<1) {
                requester.getDone(okStatus, pvRecord.getPVRecordStructure().getPVStructure().getStructure());
                return;
            }
            PVField pvField = pvRecord.getPVRecordStructure().getPVStructure().getSubField(subField);
            if(pvField==null) {
                requester.getDone(subFieldDoesNotExistStatus, null);
            } else {
                requester.getDone(okStatus, pvField.getField());
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#createChannelProcess(org.epics.pvaccess.client.ChannelProcessRequester, org.epics.pvdata.pv.PVStructure)
         */
        @Override
        public ChannelProcess createChannelProcess(
                ChannelProcessRequester channelProcessRequester,
                PVStructure pvRequest)
        {
            return ChannelProcessLocal.create(
                    this,
                    channelProcessRequester,
                    pvRequest,
                    pvRecord);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#createChannelGet(org.epics.pvaccess.client.ChannelGetRequester, org.epics.pvdata.pv.PVStructure)
         */
        @Override
        public ChannelGet createChannelGet(
                ChannelGetRequester channelGetRequester,
                PVStructure pvRequest)
        {
            return ChannelGetLocal.create(
                    this,
                    channelGetRequester,
                    pvRequest,
                    pvRecord);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#createChannelPut(org.epics.pvaccess.client.ChannelPutRequester, org.epics.pvdata.pv.PVStructure)
         */
        @Override
        public ChannelPut createChannelPut(ChannelPutRequester channelPutRequester, PVStructure pvRequest)
        {
            return ChannelPutLocal.create(this, channelPutRequester, pvRequest, pvRecord);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#createChannelPutGet(org.epics.pvaccess.client.ChannelPutGetRequester, org.epics.pvdata.pv.PVStructure, boolean, org.epics.pvdata.pv.PVStructure, boolean, boolean, org.epics.pvdata.pv.PVStructure)
         */
        @Override
        public ChannelPutGet createChannelPutGet(
                ChannelPutGetRequester channelPutGetRequester,
                PVStructure pvRequest)
        {
            return ChannelPutGetLocal.create(this,channelPutGetRequester,pvRequest,pvRecord);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#createChannelRPC(org.epics.pvaccess.client.ChannelRPCRequester, org.epics.pvdata.pv.PVStructure)
         */
        @Override
        public ChannelRPC createChannelRPC(
                ChannelRPCRequester channelRPCRequester, PVStructure pvRequest)
        {
            return ChannelRPCLocal.create(this, channelRPCRequester, pvRequest, pvRecord);
        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#createMonitor(org.epics.pvdata.monitor.MonitorRequester, org.epics.pvdata.pv.PVStructure, org.epics.pvdata.pv.PVStructure)
         */
        @Override
        public Monitor createMonitor(
                MonitorRequester monitorRequester,
                PVStructure pvRequest)
        {
            if (monitorRequester == null)
                throw new IllegalArgumentException("null channelPutRequester");
            if (pvRequest == null)
                throw new IllegalArgumentException("null pvRequest");
            if(isDestroyed.get()) {
                monitorRequester.monitorConnect(channelDestroyedStatus, null, null);
                return null;
            }
            return MonitorFactory.create(pvRecord,monitorRequester, pvRequest);

        }
        /* (non-Javadoc)
         * @see org.epics.pvaccess.client.Channel#createChannelArray(org.epics.pvaccess.client.ChannelArrayRequester, java.lang.String, org.epics.pvdata.pv.PVStructure)
         */
        @Override
        public ChannelArray createChannelArray(
                ChannelArrayRequester channelArrayRequester, PVStructure pvRequest)
        {
            return ChannelArrayLocal.create(this,channelArrayRequester,pvRequest,pvRecord);
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "{ name = " + pvRecord.getRecordName() + (isDestroyed.get() ? " disconnected }" : " connected }" ); 
        }

        private static class ChannelProcessLocal implements ChannelProcess
        {    
            private boolean isDestroyed = false;
            private final ChannelLocal channelLocal;
            private final ChannelProcessRequester channelProcessRequester;
            private final PVRecord pvRecord;
            private ReentrantLock lock = new ReentrantLock();
            private int nProcess = 0;

            private ChannelProcessLocal(
                    ChannelLocal channelLocal,
                    ChannelProcessRequester channelProcessRequester,
                    PVRecord pvRecord,
                    int nProcess)
            {
                this.channelLocal = channelLocal;
                this.channelProcessRequester = channelProcessRequester;
                this.pvRecord = pvRecord;
                this.nProcess = nProcess;
            }
            static ChannelProcessLocal create(
                    ChannelLocal channelLocal,
                    ChannelProcessRequester channelProcessRequester,
                    PVStructure pvRequest,
                    PVRecord pvRecord)
            {
                PVField pvField = null;
                int nProcess = 1;
                if(pvRequest!=null) pvField = pvRequest.getSubField("record._options");
                if(pvField!=null) 
                {
                    PVStructure options= (PVStructure)pvField;
                    pvField = options.getSubField("nProcess");
                    if(pvField!=null) {
                        PVString pvString = options.getSubField(PVString.class,"nProcess");
                        if(pvString!=null) nProcess = Integer.parseInt(pvString.get());
                    }
                }
                if(pvRecord.getTraceLevel()>0) {
                    System.out.println("ChannelProcessLocal::create recordName " + pvRecord.getRecordName());
                }
                ChannelProcessLocal processLocal = new ChannelProcessLocal(
                        channelLocal,
                        channelProcessRequester,
                        pvRecord,
                        nProcess);

                channelProcessRequester.channelProcessConnect(okStatus,processLocal);
                return processLocal;
            }

            public Channel getChannel() {
                return channelLocal;
            }
            public void cancel() {}
            public void lastRequest() {}
            public void lock() {pvRecord.lock();}
            public void unlock() {pvRecord.unlock();}
            public void destroy() {
                lock.lock();
                try {
                    if(isDestroyed) return;
                    isDestroyed = true;
                } finally {
                    lock.unlock();
                }
            }
            public void process() {
                if(isDestroyed) {
                    channelProcessRequester.processDone(requestDestroyedStatus,this);
                    return;
                }
                for(int i=0; i< nProcess; ++i) {
                    pvRecord.lock();
                    try {
                        pvRecord.beginGroupPut();
                        pvRecord.process();
                        pvRecord.endGroupPut();
                    } finally {
                        pvRecord.unlock();
                    }
                }
                channelProcessRequester.processDone(okStatus,this);
            }
        }

        private static class ChannelGetLocal implements ChannelGet
        {

            private boolean firstTime = true;
            private boolean isDestroyed = false;
            private boolean callProcess;
            private final ChannelLocal channelLocal;
            private final ChannelGetRequester channelGetRequester;
            private final PVCopy pvCopy;
            private final PVStructure pvStructure;
            private final BitSet bitSet;
            private final PVRecord pvRecord;
            private ReentrantLock lock = new ReentrantLock();

            private ChannelGetLocal(
                    boolean callProcess,
                    ChannelLocal channelLocal,
                    ChannelGetRequester channelGetRequester,
                    PVCopy pvCopy,
                    PVStructure pvStructure,
                    BitSet bitSet,
                    PVRecord pvRecord)
            {
                this.callProcess = callProcess;
                this.channelLocal = channelLocal;
                this.channelGetRequester = channelGetRequester;
                this.pvCopy = pvCopy;
                this.pvStructure = pvStructure;
                this.bitSet = bitSet;
                this.pvRecord = pvRecord;
            }

            static ChannelGetLocal create(
                    ChannelLocal channelLocal,
                    ChannelGetRequester channelGetRequester,
                    PVStructure pvRequest,
                    PVRecord pvRecord)
            {
                PVCopy pvCopy = PVCopyFactory.create(pvRecord.getPVRecordStructure().getPVStructure(), pvRequest,"");
                if(pvCopy==null) {
                    channelGetRequester.channelGetConnect(illegalRequestStatus, null, null);
                    return null;
                }
                PVStructure pvStructure = pvCopy.createPVStructure();
                BitSet bitSet = new BitSet(pvStructure.getNumberFields());
                ChannelGetLocal getLocal = new ChannelGetLocal(
                        ChannelProviderLocalFactory.getProcess(pvRequest,false),
                        channelLocal,
                        channelGetRequester,
                        pvCopy,
                        pvStructure,
                        bitSet,pvRecord);
                if(pvRecord.getTraceLevel()>0) {
                    System.out.println("ChannelGetLocal::create recordName " + pvRecord.getRecordName());
                }
                channelGetRequester.channelGetConnect(okStatus, getLocal,pvStructure.getStructure());
                return getLocal;
            }
            public Channel getChannel() {
                return channelLocal;
            }
            public void cancel() {}
            public void lastRequest() {}
            public void lock() {pvRecord.lock();}
            public void unlock() {pvRecord.unlock();}


            /* (non-Javadoc)
             * @see org.epics.pvdata.misc.Destroyable#destroy()
             */
            @Override
            public void destroy() {
                lock.lock();
                try {
                    if(isDestroyed) return;
                    isDestroyed = true;
                } finally {
                    lock.unlock();
                }
            }
            /* (non-Javadoc)
             * @see org.epics.pvaccess.client.ChannelGet#get()
             */
            @Override
            public void get() {
                if(isDestroyed) {
                    channelGetRequester.getDone(requestDestroyedStatus,this,null,null);
                    return;
                }
                bitSet.clear();
                pvRecord.lock();
                try {
                    if(callProcess) {
                        pvRecord.beginGroupPut();
                        pvRecord.process();
                        pvRecord.endGroupPut();
                    }
                    pvCopy.updateCopySetBitSet(pvStructure, bitSet);
                } finally {
                    pvRecord.unlock();
                }
                if(firstTime) {
                    bitSet.clear();
                    bitSet.set(0);
                    firstTime = false;
                }
                channelGetRequester.getDone(okStatus,this,pvStructure,bitSet);
                if(pvRecord.getTraceLevel()>1) {
                    System.out.println("ChannelGetLocal::get recordName " + pvRecord.getRecordName());
                }
            }
        }

        private static class ChannelPutLocal implements ChannelPut
        {

            private boolean isDestroyed = false;
            private boolean callProcess;
            private final ChannelLocal channelLocal;
            private final ChannelPutRequester channelPutRequester;
            private final PVCopy pvCopy;
            private PVRecord pvRecord;
            private ReentrantLock lock = new ReentrantLock();

            private ChannelPutLocal(
                    boolean callProcess,
                    ChannelLocal channelLocal,
                    ChannelPutRequester channelPutRequester,
                    PVCopy pvCopy,
                    PVRecord pvRecord)
            {
                this.callProcess = callProcess;
                this.channelLocal = channelLocal;
                this.channelPutRequester = channelPutRequester;
                this.pvCopy = pvCopy;
                this.pvRecord = pvRecord;
            }

            static ChannelPutLocal create(
                    ChannelLocal channelLocal,
                    ChannelPutRequester channelPutRequester,
                    PVStructure pvRequest,
                    PVRecord pvRecord)
            {
                PVCopy pvCopy = PVCopyFactory.create(pvRecord.getPVRecordStructure().getPVStructure(), pvRequest,"");
                if(pvCopy==null) {
                    channelPutRequester.channelPutConnect(illegalRequestStatus, null, null);
                    return null;
                }
                PVStructure pvStructure = pvCopy.createPVStructure();
                ChannelPutLocal putLocal = new ChannelPutLocal(
                        ChannelProviderLocalFactory.getProcess(pvRequest,true),
                        channelLocal,
                        channelPutRequester,
                        pvCopy,
                        pvRecord);
                if(pvRecord.getTraceLevel()>0) {
                    System.out.println("ChannelPutLocal::create recordName " + pvRecord.getRecordName());
                }
                channelPutRequester.channelPutConnect(okStatus, putLocal,pvStructure.getStructure());
                return putLocal;
            }
            public Channel getChannel() {
                return channelLocal;
            }
            public void cancel() {}
            public void lastRequest() {}
            public void lock() {pvRecord.lock();}
            public void unlock() {pvRecord.unlock();}

            public void destroy() {
                lock.lock();
                try {
                    if(isDestroyed) return;
                    isDestroyed = true;
                } finally {
                    lock.unlock();
                }
            }

            public void get() {
                if(isDestroyed) {
                    channelPutRequester.getDone(requestDestroyedStatus,this,null,null);
                    return;
                }
                PVStructure pvStructure = pvCopy.createPVStructure();
                BitSet bitSet = new BitSet(pvStructure.getNumberFields());
                bitSet.clear();
                bitSet.set(0);
                pvRecord.lock();
                try {
                    pvCopy.updateCopyFromBitSet(pvStructure, bitSet);
                } finally {
                    pvRecord.unlock();
                }
                if(pvRecord.getTraceLevel()>1) {
                    System.out.println("ChannelPutLocal::get recordName " + pvRecord.getRecordName());
                }
                channelPutRequester.getDone(okStatus,this,pvStructure,bitSet);
            }

            public void put(PVStructure pvPutStructure, BitSet bitSet) {
                if(isDestroyed) {
                    channelPutRequester.putDone(requestDestroyedStatus,this);
                    return;
                }
                pvRecord.lock();
                try {
                    pvRecord.beginGroupPut();
                    pvCopy.updateMaster(pvPutStructure,bitSet);
                    if(callProcess) {
                        pvRecord.process();
                    }
                    pvRecord.endGroupPut();
                } finally {
                    pvRecord.unlock();
                }
                if(pvRecord.getTraceLevel()>1) {
                    System.out.println("ChannelPutLocal::put recordName " + pvRecord.getRecordName());
                }
                channelPutRequester.putDone(okStatus,this);
            }
        }

        private static class ChannelPutGetLocal implements ChannelPutGet
        {

            private boolean isDestroyed = false;
            private boolean callProcess;
            private final ChannelLocal channelLocal;
            private ChannelPutGetRequester channelPutGetRequester;
            private final PVCopy pvPutCopy;
            private final PVCopy pvGetCopy;
            private final PVStructure pvGetStructure;
            private final BitSet getBitSet;
            private final PVRecord pvRecord;
            private ReentrantLock lock = new ReentrantLock();

            private ChannelPutGetLocal(
                    boolean callProcess,
                    ChannelLocal channelLocal,
                    ChannelPutGetRequester channelPutGetRequester,
                    PVCopy pvPutCopy,
                    PVCopy pvGetCopy,
                    PVStructure pvGetStructure,
                    BitSet getBitSet,
                    PVRecord pvRecord)
            {
                this.callProcess = callProcess;
                this.channelLocal = channelLocal;
                this.channelPutGetRequester = channelPutGetRequester;
                this.pvPutCopy = pvPutCopy;
                this.pvGetCopy = pvGetCopy;
                this.getBitSet = getBitSet;
                this.pvGetStructure = pvGetStructure;
                this.pvRecord = pvRecord;
            }
            static ChannelPutGetLocal create(
                    ChannelLocal channelLocal,
                    ChannelPutGetRequester channelPutGetRequester,
                    PVStructure pvRequest,
                    PVRecord pvRecord)
            {
                PVCopy pvPutCopy = PVCopyFactory.create(
                        pvRecord.getPVRecordStructure().getPVStructure(),
                        pvRequest,
                        "putField");
                PVCopy pvGetCopy = PVCopyFactory.create(
                        pvRecord.getPVRecordStructure().getPVStructure(),
                        pvRequest,
                        "getField");
                if(pvPutCopy==null || pvGetCopy==null) {
                    channelPutGetRequester.channelPutGetConnect(illegalRequestStatus, null, null,null);
                    return null;
                }
                PVStructure pvGetStructure = pvGetCopy.createPVStructure();
                BitSet getBitSet = new BitSet(pvGetStructure.getNumberFields());

                ChannelPutGetLocal putGet = new ChannelPutGetLocal(
                        ChannelProviderLocalFactory.getProcess(pvRequest,true),
                        channelLocal,
                        channelPutGetRequester,
                        pvPutCopy,
                        pvGetCopy,
                        pvGetStructure,
                        getBitSet,
                        pvRecord);
                if(pvRecord.getTraceLevel()>0) {
                    System.out.println("ChannelPutGetLocal::create recordName " + pvRecord.getRecordName());
                }

                channelPutGetRequester.channelPutGetConnect(
                        okStatus, putGet,pvPutCopy.getStructure(),pvGetCopy.getStructure());
                return putGet;
            }
            public Channel getChannel() {
                return channelLocal;
            }
            public void cancel() {}
            public void lastRequest() {}
            public void lock() {pvRecord.lock();}
            public void unlock() {pvRecord.unlock();}

            public void destroy() {
                lock.lock();
                try {
                    if(isDestroyed) return;
                    isDestroyed = true;
                } finally {
                    lock.unlock();
                }
            }

            public void putGet(PVStructure pvPutStructure, BitSet putBitSet)
            {
                if(isDestroyed) {
                    channelPutGetRequester.putGetDone(requestDestroyedStatus,this,null,null);
                    return;
                }
                pvRecord.lock();
                try {
                    pvRecord.beginGroupPut();
                    pvPutCopy.updateMaster(pvPutStructure, putBitSet);
                    if(callProcess) pvRecord.process();
                    getBitSet.clear();
                    pvGetCopy.updateCopySetBitSet(pvGetStructure, getBitSet);
                    pvRecord.endGroupPut();
                } finally {
                    pvRecord.unlock();
                }
                if(pvRecord.getTraceLevel()>1) {
                    System.out.println("ChannelPutGetLocal::putGet recordName " + pvRecord.getRecordName());
                }
                channelPutGetRequester.putGetDone(okStatus,this,pvGetStructure,getBitSet);
            }

            public void getPut() {
                if(isDestroyed) {
                    channelPutGetRequester.getPutDone(requestDestroyedStatus,this,null,null);
                    return;
                }
                PVStructure pvPutStructure = pvPutCopy.createPVStructure();
                BitSet putBitSet = new BitSet(pvPutStructure.getNumberFields());
                pvRecord.lock();
                try {
                    pvPutCopy.initCopy(pvPutStructure, putBitSet);
                } finally {
                    pvRecord.unlock();
                }
                if(pvRecord.getTraceLevel()>1) {
                    System.out.println("ChannelPutGetLocal::getPut recordName " + pvRecord.getRecordName());
                }
                channelPutGetRequester.getPutDone(okStatus,this,pvPutStructure,putBitSet);

            }

            public void getGet() {
                if(isDestroyed) {
                    channelPutGetRequester.getGetDone(requestDestroyedStatus,this,null,null);
                    return;
                }
                getBitSet.clear();
                pvRecord.lock();
                try {
                    pvGetCopy.updateCopySetBitSet(pvGetStructure, getBitSet);
                } finally {
                    pvRecord.unlock();
                }
                if(pvRecord.getTraceLevel()>1) {
                    System.out.println("ChannelPutGetLocal::getGet recordName " + pvRecord.getRecordName());
                }
                channelPutGetRequester.getGetDone(okStatus,this,pvGetStructure,getBitSet);
            }
        }

        private static class ChannelRPCLocal implements ChannelRPC, RPCResponseCallback
        {
            private boolean isDestroyed = false;
            private final Channel channel;
            private final ChannelRPCRequester channelRPCRequester;
            private final PVRecord pvRecord;
            private volatile boolean lastRequest = false;
            private final Service service;
            private ReentrantLock lock = new ReentrantLock();


            private ChannelRPCLocal(Channel channel, ChannelRPCRequester channelRPCRequester,
                    PVStructure pvRequest, PVRecord pvRecord, Service service) {
                this.channel = channel;
                this.channelRPCRequester = channelRPCRequester;
                this.pvRecord = pvRecord;
                this.service = service;
            }

            static ChannelRPCLocal create(
                    ChannelLocal channelLocal,
                    ChannelRPCRequester channelRPCRequester,
                    PVStructure pvRequest,
                    PVRecord pvRecord)
            {
                ChannelRPCLocal channelRPC = null;
                final Service service = pvRecord.getService(pvRequest);

                if (service == null)
                {
                    channelRPCRequester.channelRPCConnect(notImplementedStatus, null);
                }
                else
                {
                    channelRPC = new ChannelRPCLocal(channelLocal, channelRPCRequester, pvRequest,
                            pvRecord, service);
                    channelRPCRequester.channelRPCConnect(okStatus,channelRPC);
                    if(pvRecord.getTraceLevel()>0) {
                        System.out.println("ChannelRPCLocal::create recordName " + pvRecord.getRecordName());
                    }
                }

                return channelRPC;
            }

            public void lastRequest()
            {
                lastRequest = true;
            }

            public Channel getChannel()
            {
                return channel;
            }

            private void processRequest(RPCService rpcService, PVStructure pvArgument)
            {
                PVStructure result = null;
                Status status = okStatus;
                boolean ok = true;
                try
                {
                    result = rpcService.request(pvArgument);
                }
                catch (RPCRequestException rre)
                {
                    status =
                            statusCreate.createStatus(
                                    rre.getStatus(),
                                    rre.getMessage(),
                                    rre);
                    ok = false;
                }
                catch (Throwable th)
                {
                    // handle user unexpected errors
                    status = 
                            statusCreate.createStatus(StatusType.FATAL,
                                    "Unexpected exception caught while calling RPCService.request(PVStructure).",
                                    th);
                    ok = false;
                }

                // check null result
                if (ok && result == null)
                {
                    status =
                            statusCreate.createStatus(
                                    StatusType.FATAL,
                                    "RPCService.request(PVStructure) returned null.",
                                    null);
                }

                channelRPCRequester.requestDone(status, this, result);

                if (lastRequest)
                    destroy();
            }

            @Override
            public void requestDone(Status status, PVStructure result) {
                channelRPCRequester.requestDone(status, this, result);

                if (lastRequest)
                    destroy();
            }

            private void processRequest(RPCServiceAsync rpcServiceAsync, PVStructure pvArgument)
            {
                try
                {
                    rpcServiceAsync.request(pvArgument, this);
                }
                catch (Throwable th)
                {
                    // handle user unexpected errors
                    Status status = 
                            statusCreate.createStatus(StatusType.FATAL,
                                    "Unexpected exception caught while calling RPCService.request(PVStructure).",
                                    th);

                    channelRPCRequester.requestDone(status, this, null);

                    if (lastRequest)
                        destroy();
                }

                // we wait for callback to be called
            }

            @Override
            public void request(final PVStructure pvArgument) {

                if(isDestroyed) {
                    channelRPCRequester.requestDone(requestDestroyedStatus, this, null);
                    return;
                }

                if(pvRecord.getTraceLevel()>1) {
                    System.out.println("ChannelRPCLocal::request");
                }

                if (service instanceof RPCService)
                {
                    final RPCService rpcService = (RPCService)service;
                    processRequest(rpcService, pvArgument);
                }
                else if (service instanceof RPCServiceAsync)
                {
                    final RPCServiceAsync rpcServiceAsync = (RPCServiceAsync)service;
                    processRequest(rpcServiceAsync, pvArgument);
                }
                else
                    throw new RuntimeException("unsupported Service type");
            }

            @Override
            public void destroy() {
                lock.lock();
                try {
                    if(isDestroyed) return;
                    isDestroyed = true;
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void lock() {
                // noop
            }

            @Override
            public void unlock() {
                // noop
            }

            @Override
            public void cancel() {
            }
        }

        private static class ChannelArrayLocal implements ChannelArray {
            private boolean isDestroyed = false;
            private ChannelLocal channelLocal;
            private ChannelArrayRequester channelArrayRequester;
            private PVArray pvArray;
            private PVArray pvCopy;
            private PVRecord pvRecord;
            private ReentrantLock lock = new ReentrantLock();

            private ChannelArrayLocal(
                    ChannelLocal channelLocal,
                    ChannelArrayRequester channelArrayRequester,
                    PVArray pvArray,
                    PVArray pvCopy,
                    PVRecord pvRecord)
            {
                this.channelLocal = channelLocal;
                this.channelArrayRequester = channelArrayRequester;
                this.pvArray = pvArray;
                this.pvCopy = pvCopy;
                this.pvRecord = pvRecord;

                channelArrayRequester.channelArrayConnect(okStatus, this, pvArray.getArray());
            }
            static ChannelArrayLocal create(
                    ChannelLocal channelLocal,
                    ChannelArrayRequester channelArrayRequester,
                    PVStructure pvRequest,
                    PVRecord pvRecord)
            {
                PVField[] pvFields = pvRequest.getPVFields();
                if(pvFields.length!=1) {
                    channelArrayRequester.channelArrayConnect(illegalRequestStatus, null, null);
                    return null;
                }
                PVField pvField = pvFields[0];
                String fieldName = "";
                while(pvField!=null) {
                    String name = pvField.getFieldName();
                    if(name!=null && name.length()>0) {
                        if(fieldName.length()>0) fieldName += '.';
                        fieldName += name;
                    }
                    PVStructure pvs = (PVStructure)pvField;
                    pvFields = pvs.getPVFields();
                    if(pvFields.length!=1) break;
                    pvField = pvFields[0];
                }
                if(fieldName.startsWith("field.")) {
                    fieldName = fieldName.substring(6);
                }
                pvField = pvRecord.getPVRecordStructure().getPVStructure().getSubField(fieldName);
                if(pvField==null) {
                    channelArrayRequester.channelArrayConnect(illegalRequestStatus, null, null);
                    return null;
                }
                Type type = pvField.getField().getType();
                if(type!=Type.scalarArray && type!=Type.structureArray&&type!=Type.unionArray) {
                    channelArrayRequester.channelArrayConnect(subFieldNotArrayStatus, null, null);
                    return null;
                }
                PVArray pvArray = (PVArray)pvField;
                PVArray pvCopy;
                if(type==Type.scalarArray) {
                    PVScalarArray xxx = (PVScalarArray)pvField;
                    pvCopy = pvDataCreate.createPVScalarArray(xxx.getScalarArray().getElementType());
                } else if(type==Type.structureArray) {
                    PVStructureArray xxx = (PVStructureArray)pvField;
                    pvCopy = pvDataCreate.createPVStructureArray(xxx.getStructureArray().getStructure());
                } else {
                    PVUnionArray xxx = (PVUnionArray)pvField;
                    pvCopy = pvDataCreate.createPVUnionArray(xxx.getUnionArray().getUnion());
                }
                ChannelArrayLocal array = new ChannelArrayLocal(
                        channelLocal,
                        channelArrayRequester,
                        pvArray,
                        pvCopy,
                        pvRecord);
                if(pvRecord.getTraceLevel()>0) {
                    System.out.println("ChannelArray::create recordName " + pvRecord.getRecordName());
                }
                return array;
            }
            public Channel getChannel() {
                return channelLocal;
            }
            public void cancel() {}
            public void lastRequest() {}
            public void lock() {pvRecord.lock();}
            public void unlock() {pvRecord.unlock();}

            public void destroy() {
                lock.lock();
                try {
                    if(isDestroyed) return;
                    isDestroyed = true;
                } finally {
                    lock.unlock();
                }
            }


            /* (non-Javadoc)
             * @see org.epics.pvaccess.client.ChannelArray#getArray(int, int, int)
             */
            public void getArray(int offset, int count,int stride) {
                if(isDestroyed) {
                    channelArrayRequester.getArrayDone(requestDestroyedStatus,this,null);
                    return;
                }
                if(pvRecord.getTraceLevel()>0) {
                    System.out.println("ChannelArray::getArray recordName " + pvRecord.getRecordName());
                }
                if(offset<0) {
                    channelArrayRequester.getArrayDone(illegalOffsetStatus,this,null);
                    return;
                }
                if(stride!=1) {
                    channelArrayRequester.getArrayDone(illegalStrideStatus,this,null);
                    return;
                }

                pvRecord.lock();
                try {
                    boolean ok = false;
                    while(true) {
                        int length = pvArray.getLength();
                        if(length<=0) break;
                        if(count<=0) {
                            count = (length -offset + stride -1)/stride;
                            if(count>0) ok = true;
                            break;
                        }
                        int maxcount = (length -offset + stride -1)/stride;
                        if(count>maxcount) count = maxcount;
                        ok = true;
                        break;
                    }
                    if(ok) {
                        pvCopy.setLength(count);
                        Type type = pvArray.getArray().getType();
                        if(type==Type.scalarArray) {
                            PVScalarArray from = (PVScalarArray)pvArray;
                            PVScalarArray to = (PVScalarArray)pvCopy;
                            convert.copyScalarArray(from, offset, to, 0, count);
                        } else if (type==Type.structureArray) {
                            PVStructureArray from = (PVStructureArray)pvArray;
                            PVStructureArray to = (PVStructureArray)pvCopy;
                            convert.copyStructureArray(from, offset, to, 0, count);
                        } else {
                            PVUnionArray from = (PVUnionArray)pvArray;
                            PVUnionArray to = (PVUnionArray)pvCopy;
                            convert.copyUnionArray(from, offset, to, 0, count);
                        }
                    }
                } finally  {
                    pvRecord.unlock();
                }
                channelArrayRequester.getArrayDone(okStatus,this,pvCopy);
            }

            /* (non-Javadoc)
             * @see org.epics.pvaccess.client.ChannelArray#putArray(org.epics.pvdata.pv.PVArray, int, int, int)
             */
            public void putArray(PVArray putArray,int offset, int count,int stride) {
                if(isDestroyed) {
                    channelArrayRequester.putArrayDone(requestDestroyedStatus,this);
                    return;
                }
                if(pvRecord.getTraceLevel()>0) {
                    System.out.println("ChannelArray::putArray recordName " + pvRecord.getRecordName());
                }
                if(offset<0) {
                    channelArrayRequester.putArrayDone(illegalOffsetStatus,this);
                    return;
                }
                if(count<0) {
                    channelArrayRequester.putArrayDone(illegalCountStatus,this);
                    return;
                }
                if(stride!=1) {
                    channelArrayRequester.putArrayDone(illegalStrideStatus,this);
                    return;
                }
                int newLength = offset + count*stride;
                if(newLength<pvArray.getLength()) pvArray.setLength(newLength);

                pvRecord.lock();
                try {
                    Type type = pvArray.getArray().getType();
                    if(type==Type.scalarArray) {
                        PVScalarArray to = (PVScalarArray)pvArray;
                        PVScalarArray from = (PVScalarArray)pvCopy;
                        convert.copyScalarArray(from, 0, to, offset, count);
                    } else if (type==Type.structureArray) {
                        PVStructureArray to = (PVStructureArray)pvArray;
                        PVStructureArray from = (PVStructureArray)pvCopy;
                        convert.copyStructureArray(from, 0, to, offset, count);
                    } else {
                        PVUnionArray to = (PVUnionArray)pvArray;
                        PVUnionArray from = (PVUnionArray)pvCopy;
                        convert.copyUnionArray(from, 0, to, offset, count);
                    }
                } finally  {
                    pvRecord.unlock();
                }
                channelArrayRequester.putArrayDone(okStatus,this);
            }

            /* (non-Javadoc)
             * @see org.epics.pvaccess.client.ChannelArray#getLength()
             */
            @Override
            public void getLength() {
                int length = 0;
                pvRecord.lock();
                try {
                    length = pvArray.getLength();
                } finally {
                    pvRecord.unlock();
                }
                channelArrayRequester.getLengthDone(okStatus, this,length);
            }
            /* (non-Javadoc)
             * @see org.epics.pvaccess.client.ChannelArray#setLength(int)
             */
            public void setLength(int length) {
                if(isDestroyed) {
                    channelArrayRequester.setLengthDone(requestDestroyedStatus,this);
                    return;
                }
                pvRecord.lock();
                try {
                    if(length>=0) {
                        if(pvArray.getLength()!=length) pvArray.setLength(length);
                    }
                } finally  {
                    pvRecord.unlock();
                }
                channelArrayRequester.setLengthDone(okStatus,this);
            }

        }

    }
}
