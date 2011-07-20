// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.unix;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;

import oval.schemas.systemcharacteristics.core.EntityItemIPAddressStringType;
import oval.schemas.systemcharacteristics.core.InterfacesType;
import oval.schemas.systemcharacteristics.core.InterfaceType;
import oval.schemas.systemcharacteristics.core.ObjectFactory;
import oval.schemas.systemcharacteristics.core.SystemInfoType;

import org.joval.intf.io.IFilesystem;
import org.joval.intf.system.IEnvironment;
import org.joval.intf.system.IProcess;
import org.joval.intf.system.ISession;
import org.joval.util.JOVALSystem;

/**
 * Tool for creating a SystemInfoType from a Unix ISession implementation.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class UnixSystemInfo {
    private ISession session;
    private ObjectFactory coreFactory;
    private SystemInfoType info;
    private UnixFlavor flavor = UnixFlavor.UNKNOWN;

    /**
     * Create a plugin for scanning or test evaluation.
     */
    public UnixSystemInfo(ISession session) {
	coreFactory = new ObjectFactory();
	this.session = session;
    }

    public SystemInfoType getSystemInfo() {
	if (info != null) {
	    return info;
	}

	info = coreFactory.createSystemInfoType();
	try {
	    IProcess p = session.createProcess("uname -s");
	    p.start();
	    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    String osName = reader.readLine();
	    reader.close();
	    for (UnixFlavor f : UnixFlavor.values()) {
		if (f.osName().equals(osName)) {
		    flavor = f;
		    break;
		}
	    }

	    p = session.createProcess("hostname");
	    p.start();
	    reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    info.setPrimaryHostName(reader.readLine());
	    reader.close();

	    p = session.createProcess("uname -r");
	    p.start();
	    reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    info.setOsVersion(reader.readLine());
	    reader.close();

	    IFilesystem fs = session.getFilesystem();
	    List<String> releaseFiles = fs.search("^/etc/.*-release$");
	    if (releaseFiles.size() > 0) {
		p = session.createProcess("cat " + releaseFiles.get(0));
		p.start();
    		reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		info.setOsName(reader.readLine());
		reader.close();
	    } else {
		info.setOsName(osName);
	    }

	    p = session.createProcess("uname -p");
	    p.start();
    	    reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	    info.setArchitecture(reader.readLine());
	    reader.close();

	    InterfacesType interfacesType = coreFactory.createInterfacesType();
	    List<NetworkInterface> interfaces = NetworkInterface.getInterfaces(flavor, session);
	    for (NetworkInterface intf : interfaces) {
		if (intf.getIpV4Address() != null) {
		    InterfaceType interfaceType = coreFactory.createInterfaceType();
		    interfaceType.setMacAddress(intf.getMacAddress());
		    interfaceType.setInterfaceName(intf.getDescription());
		    EntityItemIPAddressStringType ipAddressType = coreFactory.createEntityItemIPAddressStringType();
		    ipAddressType.setValue(intf.getIpV4Address());
		    interfaceType.setIpAddress(ipAddressType);
		    interfacesType.getInterface().add(interfaceType);
		}
	    }
	    info.setInterfaces(interfacesType);
	} catch (Exception e) {
	    JOVALSystem.getLogger().log(Level.WARNING, JOVALSystem.getMessage("ERROR_PLUGIN_INTERFACE"), e);
	}
	return info;
    }
}