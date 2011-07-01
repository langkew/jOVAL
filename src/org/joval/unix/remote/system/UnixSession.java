// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.unix.remote.system;

import org.vngx.jsch.JSch;
import org.vngx.jsch.ChannelExec;
import org.vngx.jsch.ChannelType;
import org.vngx.jsch.Session;
import org.vngx.jsch.UserInfo;
import org.vngx.jsch.exception.JSchException;

import org.joval.intf.identity.ICredential;
import org.joval.intf.identity.ILocked;
import org.joval.intf.io.IFilesystem;
import org.joval.intf.system.IEnvironment;
import org.joval.intf.system.IProcess;
import org.joval.intf.system.ISession;
import org.joval.unix.system.Environment;
import org.joval.unix.remote.io.SftpFilesystem;

/**
 * A representation of an SSH session, which simply uses JSch to implement an ISession.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class UnixSession implements ISession, ILocked, UserInfo {
    private String hostname;
    private ICredential cred;
    private Session session;
    private IEnvironment env;
    private SftpFilesystem fs;

    public UnixSession(String hostname) {
	this.hostname = hostname;
    }

    // Implement ILocked

    public boolean unlock(ICredential cred) {
	this.cred = cred;
	return true;
    }

    // Implement ISession

    public boolean connect() {
	if (cred == null) {
	    return false;
	}
	try {
	    JSch jsch = JSch.getInstance();
	    session = jsch.createSession(cred.getUsername(), hostname);
	    session.setUserInfo(this);
	    session.connect(3000);
	    env = new Environment(this);
	    fs = new SftpFilesystem(session, env);
	    return true;
	} catch (JSchException e) {
	    e.printStackTrace();
	}
	return false;
    }

    public void disconnect() {
	session.disconnect();
    }

    public void setWorkingDir(String path) {
	// no-op
    }

    public int getType() {
	return UNIX;
    }

    public IFilesystem getFilesystem() {
	return fs;
    }

    public IEnvironment getEnvironment() {
	return env;
    }

    public IProcess createProcess(String command) throws Exception {
	ChannelExec ce = session.openChannel(ChannelType.EXEC);
	return new SshProcess(ce, command);
    }

    // Implement UserInfo

    public String getPassphrase() {
	return cred.getPassphrase();
    }

    public String getPassword() {
	return cred.getPassword();
    }

    public boolean promptPassphrase(String message) {
	System.out.println(message);
	return true;
    }

    public boolean promptPassword(String message) {
	System.out.println(message);
	return true;
    }

    public boolean promptYesNo(String message) {
	System.out.println(message);
	return true;
    }

    public void showMessage(String message) {
	System.out.println(message);
    }
}
