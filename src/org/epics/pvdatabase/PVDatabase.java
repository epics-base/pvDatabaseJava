/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase;

/**
 * Memory resident database of PVRecords.
 * @author mrk
 * @since 2015.01.20
 *
 */
public interface PVDatabase {
    /**
     * Destroy.
     * 
     */
    void destroy();
    /**
     * Find the interface for a record instance.
     * It will be returned if it resides in the database.
     * @param recordName The instance name.
     * @return The interface or null if the record is not located.
     */
    PVRecord findRecord(String recordName);
    /**
     * Add a new record instance.
     * @param record The record instance.
     * @return true if the record was added and false if the record was already in the database.
     */
    boolean addRecord(PVRecord record);
    /**
     * Remove a record instance.
     * @param record The record instance.
     * @return true if the record was removed and false otherwise.
     */
    boolean removeRecord(PVRecord record);
    /**
     * Get an array of the record names.
     * @return The array of names.
     */
    String[] getRecordNames();
}
