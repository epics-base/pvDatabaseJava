/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase;

/**
 * Callback for changes to a PVRecord.
 * @author mrk
 * @since 2015.01.20
 */
public interface PVListener {
    /**
     * The data in the field has been modified.
     * @param pvRecordField The field.
     */
    void dataPut(PVRecordField pvRecordField);
    /**
     * A put to a subfield has occurred.
     * @param requested The requester is listening to this pvStructure.
     * @param pvRecordField The field that has been modified.
     */
    void dataPut(PVRecordStructure requested,PVRecordField pvRecordField);
    /**
     * Begin a set of puts to a record.
     * Between begin and end of record processing,
     * dataPut may be called 0 or more times.
     * @param pvRecord - The record.
     */
    void beginGroupPut(PVRecord pvRecord);
    /**
     * End of a set of puts to a record.
     * @param pvRecord - The record.
     */
    void endGroupPut(PVRecord pvRecord);
    /**
     * Connection to record is being terminated.
     * @param pvRecord - The record from which the listener is being removed.
     */
    void unlisten(PVRecord pvRecord);
}
