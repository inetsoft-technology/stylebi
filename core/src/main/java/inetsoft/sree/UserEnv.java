/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.sree;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.*;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * User properties class.  Properties file is saved in sreeUserData directory
 * under sree.home, where a property file is created per user.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class UserEnv {
   /**
    * Get catalog getter.
    */
   public static Catalog.CatalogGetter getCatalogGetter() {
      return catalogGetter;
   }

   /**
    * Get a property for a user.
    */
   public static Object getProperty(Principal user, String name) {
      return init(user).get(name);
   }

   /**
    * Get a property for a user.
    */
   public static Object getProperty(Principal user, String name, Object def) {
      Object val = getProperty(user, name);
      return (val == null) ? def : val;
   }

   /**
    * Set a property for a user.
    */
   public static void setProperty(Principal user, String name, Object val) {
      Map<String, Object> prop = init(user);
      Object oldValue;

      if(val == null || val.equals("")) {
         oldValue = prop.remove(name);
      }
      else {
         oldValue = prop.put(name, val);
      }

      if(oldValue == val || val != null && val.equals(oldValue)) {
         return;
      }

      if(transients.contains(name)) {
         return;
      }

      // @by charvi,
      // If the properties could be copied to the persistent storage,
      // then revert the changes in memory as well.
      try {
         save(user, prop);
      }
      catch(Exception e) {
         if(oldValue == null) {
            prop.remove(name);
         }
         else {
            prop.put(name, oldValue);
         }
      }

      // useless
      /*
      synchronized(propmap) {
         Object key = getKey(user);
         propmap.put(key, prop);
      }
      */
   }

   /**
    * Get all properties defined for the user.
    */
   public static Map<String, Object> getProperties(Principal user) {
      return init(user);
   }

   /**
    * Get locale of a user.
    */
   private static Locale getLocale(Principal user) {
      Locale loc = null;

      if(user instanceof SRPrincipal) {
         SRPrincipal suser = ((SRPrincipal) user);
         loc = suser.getLocale();

         if(loc == null) {
            String locstr = suser.getProperty(SRPrincipal.LOCALE);

            if(locstr != null && !SUtil.MY_LOCALE.equals(locstr)) {
               loc = Catalog.parseLocale(locstr);
               suser.setLocale(loc);
            }
         }
      }

      return loc;
   }

   /**
    * Set local to user.
    */
   private static void setLocale(Principal user, Locale loc) {
      if(!(user instanceof SRPrincipal) || loc == null) {
         return;
      }

      String language = loc.getLanguage();
      String country = loc.getCountry();
      StringBuilder text = new StringBuilder(language);

      if(country != null && !country.isEmpty()) {
         text.append('_');
         text.append(country);
      }

      ((SRPrincipal) user).setProperty(SRPrincipal.LOCALE, text.toString());
   }

   /**
    * Get catalog of a user for viewer localization.
    * @param user the specified user.
    * @return viewer catalog of the specified user.
    */
   public static Catalog getViewerCatalog(Principal user) {
      Principal principal = user == null ? ThreadContext.getContextPrincipal() : user;
      boolean found;
      Locale loc = getLocale(principal);
      found = loc != null;

      if(loc == null) {
         loc = getLocale(user);
         found = loc != null;
      }

      if(loc == null) {
         String locstr = SreeEnv.getProperty("viewer.locale");

         if(locstr != null) {
            loc = Catalog.parseLocale(locstr);
         }
      }

      if(loc == null) {
         loc = ThreadContext.getLocale();
      }

      if(loc == null) {
         loc = Locale.getDefault();
      }

      user = user == null ? principal : user;

      if(!found) {
         setLocale(user, loc);
      }

      String resource = SreeEnv.getProperty("StyleReport.locale.resource");
      String resource2 = Catalog.DEFAULT_RESOURCE;
      return Catalog.getResCatalog(resource, resource2, loc);
   }

   /**
    * Get report catalog of a user.
    * @param user the specified user.
    * @return report catalog of the specified user.
    */
   public static Catalog getReportCatalog(Principal user) {
      Principal principal = user == null ? ThreadContext.getContextPrincipal() : user;
      Locale loc = getLocale(principal);

      if(loc == null) {
         loc = getLocale(user);
      }

      if(loc == null) {
         String locstr = SreeEnv.getProperty("viewer.locale");
         loc = Catalog.parseLocale(locstr);
      }

      if(loc == null) {
         loc = Locale.getDefault();
      }

      String resource = SreeEnv.getProperty("sree.bundle");
      return Catalog.getResCatalog(resource, loc);
   }

   /**
    * Get the directory where the files are stored.
    */
   static String getUserFile(Object key) {
      IdentityID user = getName(key);
      IdentityID name = user == null ? new IdentityID("anonymous","") : user;
      return name.getName() + "_" + name.getOrgID() + ".xml";
   }

   /**
    * Check if a user was supported to use userEnv to
    * save it's user data.
    * Anonymous is only supported when the property
    * anonymous.userdata.save was set to true.
    */
   public static boolean supportedUser(Principal user) {
      if(user == null ||
         ClientInfo.ANONYMOUS.equals(user.getName()))
      {
         return enableAnonymous;
      }

      return true;
   }

   /**
    * Get the name.
    */
   private static IdentityID getName(Object key) {
      if(key == null) {
         return new IdentityID("","");
      }
      else if(key instanceof String) {
         return IdentityID.getIdentityIDFromKey((String) key);
      }

      ClientInfo info = (ClientInfo) key;
      return info.getUserIdentity();
   }

   /**
    * Get the key.
    */
   private static Object getKey(Principal user) {
      Object key = user == null ? "anonymous" : user.getName();

      if(user instanceof SRPrincipal) {
         SRPrincipal suser = (SRPrincipal) user;
         ClientInfo cinfo = suser.getUser();

         if(cinfo != null) {
            key = cinfo;
         }
      }

      return key;
   }

   /**
    * Initialize and return a user properties.
    */
   static Map<String, Object> init(Principal user) {
      if(!supportedUser(user)) {
         return new Hashtable<>();
      }

      if(propmap.size() >= 50) {
         propmap.clear();
      }

      Object key = getKey(user);
      Map<String, Object> prop = propmap.get(key);
      DataSpace space = DataSpace.getDataSpace();
      boolean changed = false;

      if(prop == null) {
         prop = new Hashtable<>();

         synchronized(propmap) {
            propmap.put(key, prop);
         }

         String userFile = getUserFile(key);

         try(InputStream inp = space.getInputStream(USER_DIR, userFile)) {
            addChangeListener(space, key, userFile);

            if(inp != null) {
               Document doc = Tool.parseXML(inp);
               Element root = Tool.findRootElement(doc);
               NodeList nodes = Tool.getChildNodesByTagName(root, "property");

               for(int i = 0; i < nodes.getLength(); i++) {
                  Element node = (Element) nodes.item(i);
                  NodeList list = node.getChildNodes();
                  String name = null;
                  Object value = null;

                  for(int j = 0; j < list.getLength(); j++) {
                     if(list.item(j) instanceof Element) {
                        Element subnode = (Element) list.item(j);

                        if(subnode.getTagName().equals("name")) {
                           name = Tool.getValue(subnode);
                        }
                        else if(subnode.getTagName().equals("value")) {
                           String type = Tool.getAttribute(subnode, "type");

                           // parse array items
                           if(type != null && type.equals("array")) {
                              NodeList itemlist =
                                 Tool.getChildNodesByTagName(subnode, "item");
                              Object[] arr = new Object[itemlist.getLength()];

                              for(int k = 0; k < arr.length; k++) {
                                 Element item = (Element) itemlist.item(k);
                                 String tp = Tool.getAttribute(item, "type");

                                 arr[k] = Tool.getValue(item);

                                 if(tp != null) {
                                    arr[k] = Tool.getData(tp, (String) arr[k]);
                                 }
                              }

                              value = arr;
                           }
                           else {
                              value = Tool.getValue(subnode);

                              if(type != null) {
                                 value = Tool.getData(type, (String) value);
                              }
                           }
                        }
                     }
                  }

                  if(name != null && value != null ) {
                     prop.put(name, value);
                  }
               }

               inp.close();
            }

            if(changed) {
               save(user, prop);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to initialize properties for user " + user, e);
         }
      }

      return prop;
   }

   private static void addChangeListener(DataSpace space, Object key, String userFile) {
      addChangeListener(space, key, userFile, Long.MIN_VALUE);
   }

   private static void addChangeListener(DataSpace space, Object key, String userFile, long ts) {
      if(changeListenerTracker.add(new ChangeListenerRecord(key, userFile))) {
         dmgr.addChangeListener(space, USER_DIR, userFile, changeListener);
      }
   }

   private static void removeChangeListener(DataSpace space, String userFile) {
      dmgr.removeChangeListener(space, USER_DIR, userFile, changeListener);
      changeListenerTracker.remove(new ChangeListenerRecord(userFile));
   }

   /**
    * Save user properties.
    */
   public static void save(Principal user, Map<String, Object> prop) throws Exception {
      if(!supportedUser(user)) {
         return;
      }

      Object key0 = getKey(user);

      synchronized(propmap) {
         propmap.put(key0, prop);
      }

      DataSpace space = DataSpace.getDataSpace();
      String userFile = getUserFile(key0);

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream output = tx.newStream(USER_DIR, userFile))
      {
         removeChangeListener(space, userFile);

         PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
         writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         writer.println("<properties>");

         for(Map.Entry<String, Object> e : prop.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();

            if(transients.contains(key)) {
               continue;
            }

            if(val instanceof Object[]) {
               Object[] arr = (Object[]) val;

               writer.println("<property><name><![CDATA[" + Encode.forCDATA(key) +
                  "]]></name><value type=\"array\">");

               for(Object o : arr) {
                  writer.println("<item type=\"" + Tool.getDataType(o) +
                                    "\"><![CDATA[" + Tool.getDataString(o) +
                                    "]]></item>");
               }

               writer.println("</value></property>");
            }
            else {
               writer.println("<property><name><![CDATA[" + Encode.forCDATA(key) +
                  "]]></name><value type=\"" + Encode.forHtmlAttribute(Tool.getDataType(val)) +
                  "\"><![CDATA[" + Encode.forCDATA(Tool.getDataString(val)) +
                  "]]></value></property>");
            }
         }

         writer.println("</properties>");
         writer.flush();
         tx.commit();
      }
      catch(Throwable e) {
         throw new Exception(
            "Failed to save user properties file: " +
            USER_DIR + File.separator + getUserFile(key0), e);
      }
      finally {
         if(space != null) {
            long ts = space.getLastModified(USER_DIR, userFile);
            addChangeListener(space, key0, userFile, ts);
         }
      }
   }

   /**
    * Get the user names containing a UserEnv file
    */
   public static Set<String> getUsersWithFile() {
      DataSpace space = DataSpace.getDataSpace();
      String[] files = space.list(USER_DIR);
      return Arrays.stream(files)
         .filter((file) -> file.endsWith(".xml"))
         .map((file) -> file.substring(0, file.length() - 4))
         .collect(Collectors.toSet());
   }

   /**
    * Get user dir in the storage.
    *
    * @return user dir.
    */
   public static String getUserDir() {
      return UserEnv.USER_DIR;
   }

   /**
    * Record class for tracking change listeners and cleaning them up.
    */
   private static final class ChangeListenerRecord {
      public ChangeListenerRecord(String userFile) {
         this(null, userFile);
      }

      public ChangeListenerRecord(Object key, String userFile) {
         this.key = key == null ? null : new WeakReference<>(key);
         this.userFile = userFile;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         ChangeListenerRecord that = (ChangeListenerRecord) o;
         return Objects.equals(userFile, that.userFile);
      }

      @Override
      public int hashCode() {
         return Objects.hash(userFile);
      }

      final WeakReference<Object> key;
      final String userFile;
   }

   private static final DataChangeListener changeListener = new DataChangeListener() {
      @Override
      public void dataChanged(DataChangeEvent e) {
         LOG.debug(e.toString());

         synchronized(propmap) {
            for(Object key : propmap.keySet()) {
               IdentityID user = getName(key);
               int index = e.getFile().indexOf(".xml");

               if(index >= 0 && e.getFile().substring(0, index).equals(user.name)) {
                  propmap.remove(key);
                  return;
               }
            }
         }
      }
   };

   private static final WeakHashMap<Object, Map<String, Object>> propmap =
      new WeakHashMap<Object, Map<String, Object>>()
   {
      @Override
      public synchronized void clear() {
         for(Object s : keySet()) {
            removeFromDataSpace(s);
         }

         super.clear();
      }

      // remove from dataspace
      private void removeFromDataSpace(Object key) {
         try {
            DataSpace space = DataSpace.getDataSpace();
            removeChangeListener(space, getUserFile(key));
         }
         catch(Exception e) {
            // ignore
         }
      }

      @Override
      public synchronized Map<String, Object> remove(Object key) {
         removeFromDataSpace(key);
         return super.remove(key);
      }
   };

   /**
    * Catalog getter.
    */
   private static final Catalog.CatalogGetter catalogGetter = (user, type) -> {
      if(type.equals(Catalog.DEFAULT)) {
         return getViewerCatalog(user);
      }
      else if(type.equals(Catalog.REPORT)) {
         return getReportCatalog(user);
      }
      else {
         throw new RuntimeException("Unsupported type found: " + type);
      }
   };

   private static final String USER_DIR = "sreeUserData";
   // data change listener manager
   private static final DataChangeListenerManager dmgr = new DataChangeListenerManager();
   private static final boolean enableAnonymous =
      "true".equals(SreeEnv.getProperty("anonymous.userdata.save"));
   private static final Logger LOG =
      LoggerFactory.getLogger(UserEnv.class);
   private static final Set<String> transients = new HashSet<>();
   private static final Set<ChangeListenerRecord> changeListenerTracker =
      ConcurrentHashMap.newKeySet();
   private static final ScheduledExecutorService changeListenerCleanupThread =
      Executors.newSingleThreadScheduledExecutor();

   static {
      transients.add("locale");

      // Scheduled task which cleans up listeners corresponding to the WeakReference keys that were
      // GCed and removed from propmap.
      changeListenerCleanupThread.scheduleAtFixedRate(() -> {
         final Iterator<ChangeListenerRecord> iterator = changeListenerTracker.iterator();

         while(iterator.hasNext()) {
            final ChangeListenerRecord record = iterator.next();
            final Object key = record.key.get();

            if(key == null) {
               iterator.remove();
            }
            else if(!propmap.containsKey(key)) {
               iterator.remove();
               dmgr.removeChangeListener(DataSpace.getDataSpace(), USER_DIR,
                  record.userFile, changeListener);
            }
         }
      }, 0, 10, TimeUnit.MINUTES);
   }
}
