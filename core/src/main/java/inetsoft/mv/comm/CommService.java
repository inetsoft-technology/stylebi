/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.comm;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CommService, the service is responsible for communication.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class CommService {
   /**
    * Get the communication service.
    */
   public static CommService getService() throws Exception {
      if(service == null) {
         lock.lock();

         try {
            if(service == null) {
               service = new CommService();
            }
         }
         finally {
            lock.unlock();
         }
      }

      return service;
   }

   /**
    * Get the connection config used by the current service.
    */
   public static XConnectionConfig getConfig() throws Exception {
      return getService().config;
   }

   /**
    * Get the short class name by its class.
    */
   public static String getClassName(Class<?> cls) {
      String name = cls.getName();
      return name.substring(PREFIX.length());
   }

   /**
    * Get the class by its receiver.
    */
   public static Class<?> getClass(String receiver)
      throws ClassNotFoundException
   {
      String cname = PREFIX + receiver;
      return Class.forName(Tool.convertUserClassName(cname));
   }

   /**
    * Create an instance of CommService.
    */
   private CommService() throws Exception {
      super();
      config = new ConnectionConfig();
   }

   /**
    * The default implementation of XConnectionConfig.
    */
   private static final class ConnectionConfig implements XConnectionConfig {
      public ConnectionConfig() {
         super();
      }

      @Override
      public int getBlockSize() {
         String val = SreeEnv.getProperty("comm.block.size");
         return val != null ? Integer.parseInt(val) : 32 * K;
      }
   }

   private static final int K = 1024;
   private static final String PREFIX = "inetsoft.mv.";
   private static CommService service;
   private static Lock lock = new ReentrantLock();
   private XConnectionConfig config;
}
