/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal;

import inetsoft.sree.schedule.TestableRemote;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Thread wrapping RMI calls in order to prevent the process from
 * hanging.
 */
public class RMICallThread extends GroupedThread {
   /**
    * Default constructor.
    */
   public RMICallThread() {
      super();

      if(OperatingSystem.isUnix()) {
         System.setProperty("java.rmi.server.hostname", Tool.getIP());
      }
   }

   /**
    * Calls java.rmi.Naming.lookup(String) from within this
    * thread. See java.rmi.Naming for more information on
    * this method.
    * @param name - a URL-formatted name for the remote object.
    * @param timeout - the length of time in milliseconds before
    *                  the connection times out.
    * @return a reference for a remote object.
    */
   public Remote lookup(String name, long timeout) {
      return lookup(name, timeout, true);
   }

   /**
    * Calls java.rmi.Naming.lookup(String) from within this
    * thread. See java.rmi.Naming for more information on
    * this method.
    * @param name - a URL-formatted name for the remote object.
    * @param timeout - the length of time in milliseconds before
    *                  the connection times out.
    * @param logTimeout <tt>true</tt> to print a warning message to the log if
    *                   the connection timed out; <tt>false</tt> otherwise.
    * @return a reference for a remote object.
    */
   public Remote lookup(String name, long timeout, boolean logTimeout) {
      this.url = name;
      this.method = RMI_LOOKUP;
      this.start();

      try {
         this.join(timeout);

         if(this.isAlive()) {
            if(logTimeout) {
               LOG.warn("Connection timed-out looking up " + name);
            }

            this.interrupt();
         }
      }
      catch(Exception e) {
      }

      return remote;
   }

   /**
    * Calls java.rmi.Naming.bind(String, Remote) from within this
    * thread. See java.rmi.Naming for more information on
    * this method.
    * @param name - a URL-formatted name for the remote object.
    * @param obj - a reference for the remote object (usually a stub).
    * @param timeout - the length of time in milliseconds before
    *                  the connection times out.
    * @return true if the object was bound, false otherwise.
    */
   public boolean bind(String name, Remote obj, long timeout) {
      this.url = name;
      this.obj = obj;
      this.method = RMI_BIND;
      this.start();

      try {
         this.join(timeout);

         if(this.isAlive()) {
            LOG.warn("Connection timed-out binding " + name);
            this.interrupt();
         }
      }
      catch(Exception e) {
      }

      return success;
   }

   /**
    * Calls java.rmi.Naming.rebind(String, Remote) from within
    * this thread. See java.rmi.Naming for more information on
    * this method.
    * @param name - a URL-formatted name for the remote object.
    * @param obj - a reference for the remote object (usually a stub).
    * @param timeout - the length of time in milliseconds before
    *                  the connection times out.
    * @return true if the object was bound, false otherwise.
    */
   public boolean rebind(String name, Remote obj, long timeout) {
      this.url = name;
      this.obj = obj;
      this.method = RMI_REBIND;
      this.start();

      try {
         this.join(timeout);

         if(this.isAlive()) {
            LOG.warn("Connection timed-out binding " + name);
            this.interrupt();
         }
      }
      catch(Exception e) {
      }

      return success;
   }

   /**
    * Calls java.rmi.Naming.unbind(String, Remote) from within
    * this thread. See java.rmi.Naming for more information on
    * this method.
    * @param name - a URL-formatted name for the remote object.
    * @param timeout - the length of time in milliseconds before
    *                  the connection times out.
    * @return true if the object was unbound, false otherwise.
    */
   public boolean unbind(String name, long timeout) {
      this.url = name;
      this.method = RMI_UNBIND;
      this.start();

      try {
         this.join(timeout);

         if(this.isAlive()) {
            LOG.warn("Connection timed-out unbinding " + name);
            this.interrupt();
         }
      }
      catch(Exception e) {
      }

      return success;
   }

   /**
    * Calls java.rmi.registry.LocateRegistry(String, int) from within
    * this thread. See java.rmi.registry.LocateRegistry for more
    * information on this method.
    * @param host - host for the remote registry.
    * @param port - port on which the registry accepts requests.
    * @param timeout - the length of time in milliseconds before
    *                  the connection times out.
    * @return reference (a stub) to the remote object registry.
    */
   public Registry getRegistry(String host, int port, long timeout) {
      this.host = host;
      this.port = port;
      this.method = RMI_GETREG;
      this.start();

      try {
         this.join(timeout);

         if(this.isAlive()) {
            LOG.warn(
               "Connection timed-out getting RMI registry on " +
               host + ", port " + port);
            this.interrupt();
         }
      }
      catch(Exception e) {
      }

      return registry;
   }

   /**
    * Calls java.lang.Process.exec(String) from within
    * this thread to start rmiregistry.
    * @param host - host for the remote registry.
    * @param port - port on which the registry accepts requests.
    * @param timeout - the length of time in milliseconds before
    *                  the connection times out.
    * @return true if the registry is started, false otherwise.
    */
   public boolean startRegistry(String host, int port, long timeout) {
      this.host = host;
      this.port = port;
      this.method = RMI_STARTREG;
      this.start();

      try {
         this.join(timeout);

         if(this.isAlive()) {
            LOG.warn("Start registry timed-out");
            this.interrupt();
         }
      }
      catch(Exception e) {
      }

      return success;
   }

   /**
    * This method should not be called directly. Instead,
    * the method reflecting the desired RMI method should
    * be called.
    */
   @Override
   protected void doRun() {
      try {
         switch(method) {
         case RMI_LOOKUP:
            remote = null;
            remote = Naming.lookup(url);

            if(remote instanceof TestableRemote) {
               ((TestableRemote) remote).test();
            }

            break;
         case RMI_BIND:
            Naming.bind(url, obj);
            break;
         case RMI_REBIND:
            Naming.rebind(url, obj);
            break;
         case RMI_UNBIND:
            Naming.unbind(url);
            break;
         case RMI_GETREG:
            registry = LocateRegistry.getRegistry(host, port);
            break;
         case RMI_STARTREG:
            registry = LocateRegistry.createRegistry(port);
            break;
         }

         success = true;
      }
      catch(Exception e) {
         if(method == RMI_GETREG) {
            LOG.warn(
               "The RMI registry is not running on " + host + ", port " + port);
         }
         else if(method == RMI_LOOKUP) {
            // LOG.warn(url + " is not bound in the RMI registry.");
         }
         else {
            LOG.debug("RMI operation failed: " + e.getMessage(), e);
         }

         remote = null;
         registry = null;
         success = false;
      }
   }

   int method = -1;
   String url;
   String host;
   int port;
   Remote obj;
   Remote remote = null;
   Registry registry = null;
   boolean success = false;
   static final int RMI_LOOKUP = 0x01;
   static final int RMI_BIND = 0x02;
   static final int RMI_REBIND = 0x03;
   static final int RMI_UNBIND = 0x04;
   static final int RMI_GETREG = 0x05;
   static final int RMI_STARTREG = 0X06;

   private static final Logger LOG =
      LoggerFactory.getLogger(RMICallThread.class);
}
