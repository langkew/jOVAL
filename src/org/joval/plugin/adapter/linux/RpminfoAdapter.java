// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.plugin.adapter.linux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.xml.bind.JAXBElement;

import oval.schemas.common.ExistenceEnumeration;
import oval.schemas.common.MessageType;
import oval.schemas.common.MessageLevelEnumeration;
import oval.schemas.definitions.core.ObjectType;
import oval.schemas.definitions.core.ObjectComponentType;
import oval.schemas.definitions.linux.RpminfoObject;
import oval.schemas.definitions.linux.RpminfoState;
import oval.schemas.definitions.linux.RpminfoTest;
import oval.schemas.results.core.ResultEnumeration;
import oval.schemas.results.core.TestType;
import oval.schemas.systemcharacteristics.core.EntityItemStringType;
import oval.schemas.systemcharacteristics.core.FlagEnumeration;
import oval.schemas.systemcharacteristics.core.ItemType;
import oval.schemas.systemcharacteristics.core.StatusEnumeration;
import oval.schemas.systemcharacteristics.core.EntityItemEVRStringType;
import oval.schemas.systemcharacteristics.linux.ObjectFactory;
import oval.schemas.systemcharacteristics.linux.RpminfoItem;

import org.joval.intf.io.IFile;
import org.joval.intf.io.IFilesystem;
import org.joval.intf.system.IProcess;
import org.joval.intf.system.ISession;
import org.joval.intf.plugin.IAdapter;
import org.joval.intf.plugin.IAdapterContext;
import org.joval.intf.oval.IDefinitions;
import org.joval.intf.oval.ISystemCharacteristics;
import org.joval.intf.system.ISession;
import org.joval.oval.OvalException;
import org.joval.util.JOVALSystem;
import org.joval.util.Version;

/**
 * Evaluates Rpminfo OVAL tests.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class RpminfoAdapter implements IAdapter {
    private IAdapterContext ctx;
    private IDefinitions definitions;
    private ISession session;
    private ObjectFactory factory;
    private oval.schemas.systemcharacteristics.core.ObjectFactory coreFactory;

    public RpminfoAdapter(ISession session) {
	this.session = session;
	factory = new ObjectFactory();
	coreFactory = new oval.schemas.systemcharacteristics.core.ObjectFactory();
    }

    // Implement IAdapter

    public Class getObjectClass() {
	return RpminfoObject.class;
    }

    public Class getTestClass() {
	return RpminfoTest.class;
    }

    public void init(IAdapterContext ctx) {
	definitions = ctx.getDefinitions();
	this.ctx = ctx;
    }

    /**
     * A generic implementation of the IAdapter.scan method for File-type objects.  Subclasses need only implement the
     * createFileItem and createStorageItem methods.
     */
    public void scan(ISystemCharacteristics sc) throws OvalException {
	Iterator <ObjectType>iter = definitions.iterateObjects(getObjectClass());
	while (iter.hasNext()) {
	    RpminfoObject obj = (RpminfoObject)iter.next();
	    if (obj.isSetSet()) {
		// Set objects only contain references to other objects, which are scanned elsewhere.
		continue;
	    } else {
		ctx.status(obj.getId());
		try {
		    RpminfoItem item = getItem(obj);
		    FlagEnumeration flag = FlagEnumeration.COMPLETE;
		    switch(item.getStatus()) {
		      case DOES_NOT_EXIST:
			sc.setObject(obj.getId(), obj.getComment(), obj.getVersion(), FlagEnumeration.DOES_NOT_EXIST, null);
			break;

		      case EXISTS: {
			sc.setObject(obj.getId(), obj.getComment(), obj.getVersion(), FlagEnumeration.COMPLETE, null);
			JAXBElement<? extends ItemType> storageItem = factory.createRpminfoItem(item);
			BigInteger itemId = sc.storeItem(storageItem);
			sc.relateItem(obj.getId(), itemId);
			break;
		      }
		    }
		} catch (Exception e) {
		    MessageType msg = new MessageType();
		    msg.setLevel(MessageLevelEnumeration.ERROR);
		    msg.setValue(e.getMessage());
		    sc.setObject(obj.getId(), obj.getComment(), obj.getVersion(), FlagEnumeration.ERROR, msg);
		}
	    }
	}
    }

    public String getItemData(ObjectComponentType oc, ISystemCharacteristics sc) throws OvalException {
	throw new RuntimeException("getItemData not supported");
    }

    public void evaluate(TestType testResult, ISystemCharacteristics sc) throws OvalException {
	String testId = testResult.getTestId();
	RpminfoTest testDefinition = definitions.getTest(testId, RpminfoTest.class);
	String objectId = testDefinition.getObject().getObjectRef();
	RpminfoState state = null;
	if (testDefinition.isSetState() && testDefinition.getState().get(0).isSetStateRef()) {
	    String stateId = testDefinition.getState().get(0).getStateRef();
	    state = definitions.getState(stateId, RpminfoState.class);
	} else {
	    throw new OvalException(JOVALSystem.getMessage("ERROR_STATE_MISSING", testId));
	}

	try {
	    List<? extends ItemType> items = sc.getItemsByObjectId(objectId);

	    switch(testDefinition.getCheckExistence()) {
	      case AT_LEAST_ONE_EXISTS:
		if (items.size() < 1) {
		    testResult.setResult(ResultEnumeration.FALSE);
		} else {
		    RpminfoItem item = (RpminfoItem)items.get(0);
		    if (state.getEvr() == null) {
			throw new OvalException(JOVALSystem.getMessage("ERROR_STATE_BAD", state.getId()));
		    } else {
			String evr = (String)state.getEvr().getValue();
			int end = evr.indexOf(":");
			String epoch = evr.substring(0, end);
			int begin = end+1;
			end = evr.indexOf("-", begin);
			String version = evr.substring(begin, end);
			String release = evr.substring(end+1);
    
			switch(state.getEvr().getOperation()) {
			  case EQUALS:
			    if (epoch.equals((String)item.getEpoch().getValue()) &&
				version.equals((String)item.getVersion().getValue()) &&
				release.equals((String)item.getRelease().getValue())) {
				testResult.setResult(ResultEnumeration.TRUE);
			    } else {
				testResult.setResult(ResultEnumeration.FALSE);
			    }
			    break;
			  case LESS_THAN:
			    if (Version.isVersion(epoch) && Version.isVersion((String)item.getEpoch().getValue())) {
				Version stateEpoch = new Version(epoch);
				Version itemEpoch = new Version((String)item.getEpoch().getValue());
				if (itemEpoch.lessThan(stateEpoch)) {
				    testResult.setResult(ResultEnumeration.TRUE);
				    break;
				}
			    } else if (((String)item.getEpoch().getValue()).compareTo(epoch) > 0) {
				testResult.setResult(ResultEnumeration.TRUE);
				break;
			    }
    
			    if (Version.isVersion(version) && Version.isVersion((String)item.getVersion().getValue())) {
				Version stateEpoch = new Version(version);
				Version itemEpoch = new Version((String)item.getVersion().getValue());
				if (itemEpoch.lessThan(stateEpoch)) {
				    testResult.setResult(ResultEnumeration.TRUE);
				    break;
				}
			    } else if (((String)item.getVersion().getValue()).compareTo(version) > 0) {
				testResult.setResult(ResultEnumeration.TRUE);
				break;
			    }
    
			    if (Version.isVersion(release) && Version.isVersion((String)item.getRelease().getValue())) {
				Version stateEpoch = new Version(release);
				Version itemEpoch = new Version((String)item.getRelease().getValue());
				if (itemEpoch.lessThan(stateEpoch)) {
				    testResult.setResult(ResultEnumeration.TRUE);
				    break;
				}
			    } else if (((String)item.getRelease().getValue()).compareTo(release) > 0) {
				testResult.setResult(ResultEnumeration.TRUE);
				break;
			    }
    
			    testResult.setResult(ResultEnumeration.FALSE);
			    break;
			  default:
			    throw new OvalException(JOVALSystem.getMessage("ERROR_UNSUPPORTED_OPERATION",
									   state.getEvr().getOperation()));
			}
		    }
		}
		break;

	      case NONE_EXIST:
		if (items.size() == 0) {
		    testResult.setResult(ResultEnumeration.TRUE);
		} else {
		    testResult.setResult(ResultEnumeration.FALSE);
		}
		break;

	      default:
		throw new OvalException(JOVALSystem.getMessage("ERROR_UNSUPPORTED_EXISTENCE",
							       testDefinition.getCheckExistence()));
	    }
	} catch (NoSuchElementException e) {
	    testResult.setResult(ResultEnumeration.NOT_EVALUATED);
	}
    }

    // Private

    private RpminfoItem getItem(RpminfoObject obj) throws Exception {
	RpminfoItem item = factory.createRpminfoItem();

	String packageName = (String)obj.getName().getValue();
	IProcess p = session.createProcess("rpm -q " + packageName);
	p.start();
	BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
	String result = br.readLine();
	br.close();

	if (result.indexOf("not installed") == -1) {
	    p = session.createProcess("rpm -q --qf '%{EPOCH}\\n' " + packageName);
	    p.start();
	    br = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    String pkgEpoch = br.readLine();
	    br.close();
	    if (pkgEpoch.equals("(none)")) {
		pkgEpoch = "0";
	    }

	    int end = result.indexOf("-");
	    String pkgName = result.substring(0, end);
	    int begin = end+1;
	    end = result.indexOf("-", begin+1);
	    String pkgRelease = result.substring(begin, end);
	    begin = end+1;
	    end = result.lastIndexOf(".");
	    String pkgVersion = result.substring(begin, end);
	    String pkgArch = result.substring(end+1);

	    item.setStatus(StatusEnumeration.EXISTS);
	    EntityItemStringType name = coreFactory.createEntityItemStringType();
	    name.setValue(pkgName);
	    item.setName(name);

	    RpminfoItem.Epoch epoch = factory.createRpminfoItemEpoch();
	    epoch.setValue(pkgEpoch);
	    item.setEpoch(epoch);

	    EntityItemStringType arch = coreFactory.createEntityItemStringType();
	    arch.setValue(pkgArch);
	    item.setArch(arch);

	    RpminfoItem.Version version = factory.createRpminfoItemVersion();
	    version.setValue(pkgVersion);
	    item.setVersion(version);

	    RpminfoItem.Release release = factory.createRpminfoItemRelease();
	    release.setValue(pkgRelease);
	    item.setRelease(release);

	    EntityItemEVRStringType evr = new EntityItemEVRStringType();
	    evr.setValue(pkgEpoch + ":" + pkgVersion + "-" + pkgRelease);
	    item.setEvr(evr);
	} else {
	    item.setStatus(StatusEnumeration.DOES_NOT_EXIST);
	}

	return item;
    }
}
