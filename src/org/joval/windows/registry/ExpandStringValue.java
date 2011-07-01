// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.windows.registry;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.joval.intf.windows.registry.IExpandStringValue;
import org.joval.intf.windows.registry.IKey;
import org.joval.util.JOVALSystem;

/**
 * Representation of a Windows registry expand-string value.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class ExpandStringValue extends Value implements IExpandStringValue {
    private String data, expandedData;

    public ExpandStringValue(IKey parent, String name, String data, String expandedData) {
	type = REG_EXPAND_SZ;
	this.parent = parent;
	this.name = name;
	this.data = data;
	this.expandedData = expandedData;
	JOVALSystem.getLogger().log(Level.FINEST, JOVALSystem.getMessage("STATUS_WINREG_VALINSTANCE", toString()));
    }

    public String getData() {
	return data;
    }

    /**
     * Get the value with environment variables filled out from the system environment.
     */
    public String getExpandedData() {
	return expandedData;
    }

    public String toString() {
	return "StringValue [Name=\"" + name + "\" Value=\"" + data + "\"]";
    }
}
