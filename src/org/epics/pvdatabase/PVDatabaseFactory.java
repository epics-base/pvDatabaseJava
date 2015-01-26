/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;



/**
 * Factory for PVDatabase.
 * @author mrk
 *
 */
public class PVDatabaseFactory {
    
    /**
     * Get the master database.
     * Currently only a single database is possible. and is named master.
     * @return Interface to the single database instance.
     */
    public static synchronized PVDatabase getMaster() {
        if(master==null) master = new Database();
        return master;
    }
    
    private static Database master = null;
    
    private static class Database implements PVDatabase {
       
        private LinkedHashMap<String,PVRecord> recordMap = new LinkedHashMap<String,PVRecord>();
        private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        private boolean isDestroyed = false;
        
        private Database() {}
        
        /* (non-Javadoc)
         * @see org.epics.pvdatabase.PVDatabase#destroy()
         */
        public void destroy()
        {
            rwLock.writeLock().lock();
            try {
                if(isDestroyed) return;
                for(String key: recordMap.keySet()) {
                    recordMap.get(key).destroy();
                }
                recordMap.clear();
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvdatabase.PVDatabase#findRecord(java.lang.String)
         */
        public PVRecord findRecord(String recordName) {
            rwLock.readLock().lock();
            try {
                if(isDestroyed) return null;
                return recordMap.get(recordName);
            } finally {
                rwLock.readLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvdatabase.PVDatabase#addRecord(org.epics.pvdatabase.PVRecord)
         */
        public boolean addRecord(PVRecord record) {
            rwLock.writeLock().lock();
            try {
                if(isDestroyed) return false;
                String key = record.getRecordName();
                if(recordMap.containsKey(key)) {
                    return false;
                }
                recordMap.put(key,record);
            } finally {
                rwLock.writeLock().unlock();
            }
            return true;
        }
        /* (non-Javadoc)
         * @see org.epics.pvdatabase.PVDatabase#removeRecord(org.epics.pvdatabase.PVRecord)
         */
        public boolean removeRecord(PVRecord record) {
            rwLock.writeLock().lock();
            try {
                String key = record.getRecordName();
                if(recordMap.remove(key)!=null) return true;
                return false;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        /* (non-Javadoc)
         * @see org.epics.pvdatabase.PVDatabase#getRecordNames()
         */
        public String[] getRecordNames() {
            rwLock.readLock().lock();
            try {
                if(isDestroyed) {
                    String[] xxx = new String[0];
                    return xxx;
                }
                String[] array = new String[recordMap.size()];
                int index = 0;
                Set<String> keys = recordMap.keySet();
                for(String key: keys) {
                    array[index++] = key;
                }
                return array;
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }
}
