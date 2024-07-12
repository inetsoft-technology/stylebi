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
package inetsoft.uql.util;

import inetsoft.util.SingletonManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * XSessionService manages session ids of all sorts of resource such as user,
 * report, expore view, query, worksheet, model, etc.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class XSessionService {
   /**
    * User session.
    */
   public static final String USER = "usr_";
   /**
    * Report session.
    */
   public static final String REPORT = "rpt_";
   /**
    * Expore view session.
    */
   public static final String EXPORE_VIEW = "ev_";
   /**
    * Query session.
    */
   public static final String QUERY = "qry_";
   /**
    * Worksheet session.
    */
   public static final String WORKSHEET = "ws_";
   /**
    * Model session.
    */
   public static final String MODEL = "mdl_";
   /**
    * Viewsheet session.
    */
   public static final String VIEWSHEET = "vs_";

   /**
    * Create a session id.
    * @param type the specified type.
    * @param desc the specified description.
    * @return the created session id.
    */
   public static synchronized String createSessionID(String type, String desc) {
      return getService().createSessionID0(type, desc);
   }

   /**
    * Clear cached session manager.
    */
   public static synchronized void clear() {
      SingletonManager.reset(XSessionService.class);
   }

   /**
    * Get the session service.
    * @return the session service.
    */
   private static XSessionService getService() {
      return SingletonManager.getInstance(XSessionService.class);
   }

   /**
    * Constructor.
    */
   public XSessionService() {
      super();

      smap.put(USER, new AtomicLong(0L));
      smap.put(REPORT, new AtomicLong(0L));
      smap.put(EXPORE_VIEW, new AtomicLong(0L));
      smap.put(QUERY, new AtomicLong(0L));
      smap.put(WORKSHEET, new AtomicLong(0L));
      smap.put(VIEWSHEET, new AtomicLong(0L));
      smap.put(MODEL, new AtomicLong(0L));
   }

   /**
    * Create a session id.
    * @param type the specified type.
    * @param desc the specified description.
    * @return the created session id.
    */
   private String createSessionID0(String type, String desc) {
      AtomicLong counter = smap.get(type);

      if(counter == null) {
         throw new RuntimeException("Unsupported type found: " + type);
      }

      long count = counter.incrementAndGet();

      String id = desc == null ? type + ts + '_' + count :
         type + ts + '_' + count + '_' + desc;
      return id.length() > MAX_LENGTH ? id.substring(0, MAX_LENGTH) : id;
   }

   private static final int MAX_LENGTH = 255;

   private final long ts = System.currentTimeMillis();
   private final Map<String, AtomicLong> smap = new HashMap<>();
}
