/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdatabase;

import org.epics.pvdata.pv.PVStructure;

/**
 * PVRecordStructure is for PVStructure that is part of a PVRecord.
 * @author mrk
 * 2015.01.20
 *
 */
public interface PVRecordStructure extends PVRecordField {
    /**
     * Get the subfields.
     * @return The array of subfields;
     */
    PVRecordField[] getPVRecordFields();
    /**
     * Get the PVStructure.
     * @return The interface.
     */
    PVStructure getPVStructure();
}
