/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase;

import org.epics.pvdata.pv.PVField;

/**
 * PVRecordField is for PVField that is part of a PVRecord.
 * Each PVType has an interface that extends PVField.
 * @author mrk
 * 2015.01.20
 *
 */
public interface PVRecordField {

    /**
     * Get the parent of this field.
     * @return The parent interface or null if top level structure.
     */
    PVRecordStructure getParent();
    /**
     * Get the PVField.
     * @return The PVField interface.
     */
    PVField getPVField();
    /**
     * Get the fullFieldName, i.e. the complete hierarchy.
     * @return The name.
     */
    String getFullFieldName();
    /**
     * Get the full name, which is the recordName plus the fullFieldName
     * @return The name.
     */
    String getFullName();
    /**
     * Get the record.
     * @return The record interface.
     */
    PVRecord getPVRecord();
    /**
     * post that data has been modified.
     * This must be called by the code that implements put.
     */
    void postPut();
}
