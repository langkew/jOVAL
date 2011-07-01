// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.intf.plugin;

import java.util.Collection;
import javax.xml.bind.JAXBElement;

import oval.schemas.definitions.core.ObjectType;
import oval.schemas.definitions.core.ObjectComponentType;
import oval.schemas.results.core.TestType;

import org.joval.oval.OvalException;
import org.joval.intf.oval.ISystemCharacteristics;

/**
 * The interface for implementing a jOVAL plug-in adapter.  An adapter operates on a pair of classes, an ObjectType subclass
 * and a TestType subclass.  The jOVAL engine uses adapters to retrieve object data from hosts, and to evaluate tests.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public interface IAdapter {
    /**
     * Initialize the plug-in.
     */
    public void init(IAdapterContext ctx);

    /**
     * Identify the class of a subclass of ObjectType for which this plug-in knows how to retrieve data.
     */
    public Class getObjectClass();

    /**
     * Identify the class of a subclass of definitions.TestType that this plug-in knows how to evaluate.
     */
    public Class getTestClass();

    /**
     * Retrieve data for all objects of the supported class(es) and store in the ISystemCharacteristics for later use.
     * Objects that are Sets should be skipped, as the Engine will handle them automatically.
     */
    public void scan(ISystemCharacteristics sc) throws OvalException;

    /**
     * Return the specified item/record field for the object (the item, field and object ID all being contained within the
     * ObjectComponentType argument.  The ISystemCharacteristics is provided as a convenience, but owing to the fact that
     * this method is used to resolve variable values, it may be necessary for the plug-in to probe the host for the
     * information, as it may be invoked <i>during</i> the scan method, when object definitions refer to variable values.
     */
    public String getItemData(ObjectComponentType object, ISystemCharacteristics sc) throws OvalException;

    /**
     * Evaluate a test.  Throw an OvalException if the test is not a supported type.  The ISystemCharacteristics contains
     * all the data stored by the IPlugin in the scan phase.  The IPlugin must fully populate the results.TestType
     * object, including the ResultEnumeration and all related ItemTypes and VariableValueTypes, with their embedded results.
     */
    public void evaluate(TestType test, ISystemCharacteristics sc) throws OvalException;
}
