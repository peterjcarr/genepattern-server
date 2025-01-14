/*
 * $Header$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.commons.httpclient.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.httpclient.HttpConnectionManager;

/**
 * A utility class for periodically closing idle connections.
 * 
 * @see org.apache.commons.httpclient.HttpConnectionManager#closeIdleConnections(long)
 * 
 * @since 3.0
 */
public class IdleConnectionTimeoutThread extends Thread {
    
    private List connectionManagers = new ArrayList();
    
    private boolean shutdown = false;
    
    private long timeoutInterval = 1000;
    
    private long connectionTimeout = 3000;
    
    public IdleConnectionTimeoutThread() {
        setDaemon(true);
    }
    
    /**
     * Adds a connection manager to be handled by this class.  
     * {@link HttpConnectionManager#closeIdleConnections(long)} will be called on the connection
     * manager every {@link #setTimeoutInterval(long) timeoutInterval} milliseconds.
     * 
     * @param connectionManager The connection manager to add
     */
    public synchronized void addConnectionManager(HttpConnectionManager connectionManager) {
        if (shutdown) {
            throw new IllegalStateException("IdleConnectionTimeoutThread has been shutdown");
        }
        this.connectionManagers.add(connectionManager);
    }
    
    /**
     * Removes the connection manager from this class.  The idle connections from the connection
     * manager will no longer be automatically closed by this class.
     * 
     * @param connectionManager The connection manager to remove
     */
    public synchronized void removeConnectionManager(HttpConnectionManager connectionManager) {
        if (shutdown) {
            throw new IllegalStateException("IdleConnectionTimeoutThread has been shutdown");
        }
        this.connectionManagers.remove(connectionManager);
    }
    
    /**
     * Closes idle connections.
     */
    public synchronized void run() {
        while (!shutdown) {
            Iterator iter = connectionManagers.iterator();
            
            while (iter.hasNext()) {
                HttpConnectionManager connectionManager = (HttpConnectionManager) iter.next();
                connectionManager.closeIdleConnections(connectionTimeout);
            }
            
            try {
                this.wait(timeoutInterval);
            } catch (InterruptedException e) {
            }
        }
        // clear out the connection managers now that we're shutdown
        this.connectionManagers.clear();
    }
    
    /**
     * Stops the thread used to close idle connections.  This class cannot be used once shutdown.
     */
    public synchronized void shutdown() {
        this.shutdown = true;
        this.notifyAll();
    }
    
    /**
     * Sets the timeout value to use when testing for idle connections.
     * 
     * @param connectionTimeout The connection timeout in milliseconds
     * 
     * @see HttpConnectionManager#closeIdleConnections(long)
     */
    public synchronized void setConnectionTimeout(long connectionTimeout) {
        if (shutdown) {
            throw new IllegalStateException("IdleConnectionTimeoutThread has been shutdown");
        }
        this.connectionTimeout = connectionTimeout;
    }
    /**
     * Sets the interval used by this class between closing idle connections.  Idle 
     * connections will be closed every <code>timeoutInterval</code> milliseconds.
     *  
     * @param timeoutInterval The timeout interval in milliseconds
     */
    public synchronized void setTimeoutInterval(long timeoutInterval) {
        if (shutdown) {
            throw new IllegalStateException("IdleConnectionTimeoutThread has been shutdown");
        }
        this.timeoutInterval = timeoutInterval;
    }
    
}
