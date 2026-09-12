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
package inetsoft.util;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.text.MessageFormat;
import java.util.*;

/**
 * Catalog is used to handle localization and format of string values.
 * <p>
 * To load the string keys/values stored in physical properties files properly,
 * they should conform to <tt>java.util.Properties</tt> convention. e.g.
 * For a string key, if one character is a " ", we should insert a "\" before
 * it, and the same process is required for characters like "=" and ":".
 * <p>
 * As <tt>java.util.Properties</tt> won't do character encoding/decoding but
 * Unicode escapes are used, it requires us to use {JAVA_HOME}/bin/native2ascii
 * to generate a proper physical file for <tt>java.util.Properties</tt> to load
 * properly.
 * <p>
 * A localized string value will be formatted using
 * <tt>java.text.MessageFormat</tt>, so it should also conform to
 * <tt>java.text.MessageFormat</tt> convention. e.g.
 * a "{" and "}" pair included characters will be taken as a parameter,
 * characters quoted by "'" will not be parsed or formatted but just copied,
 * and "''" represents a single quote within the localized string.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class Catalog {
   /**
    * Default resource bundle name for Designer, Viewer and EM.
    */
   public static final String DEFAULT_RESOURCE = "inetsoft/util/srinter";
   public static final String DEFAULT_BUNDLE = "SreeBundle";
   private static final String GETTER_KEY = Catalog.class.getName() + ".catalogGetter";
   // resource_locale -> catalog
   private static final Map<String, Catalog> catalogs = new Object2ObjectOpenHashMap<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(Catalog.class);
   /**
    * Default catalog which is used to localize Designer, Viewer and EM.
    */
   public static final String DEFAULT = "__INETSOFT_DEFAULT_CATALOG__";
   /**
    * Report catalog which is used to localize a report.
    */
   public static final String REPORT = "__INETSOFT_REPORT_CATALOG__";
   // resource bundle keys
   private final ObjectOpenHashSet<String> keys = new ObjectOpenHashSet<>();
   // backup resource bundle keys
   private final ObjectOpenHashSet<String> keys2 = new ObjectOpenHashSet<>();
   // main resource name
   private String resource;
   // backup resource name
   private String resource2;
   // locale
   private Locale locale;
   // resource bundle
   private ResourceBundle bundle;
   // backup resource bundle
   private ResourceBundle bundle2;

   /**
    * Create a catalog.
    * @param resource the specified resource.
    */
   @SuppressWarnings("unused")
   private Catalog(String resource) {
      this(resource, null);
   }

   /**
    * Create a catalog.
    * @param resource the specified resource.
    * @param loc the specified locale
    */
   private Catalog(String resource, Locale loc) {
      this(resource, null, loc);
   }

   /**
    * Create a catalog.
    *
    * @param resource  the specified resource.
    * @param resource2 the specified backup resource.
    * @param loc       the specified locale
    */
   private Catalog(String resource, String resource2, Locale loc) {
      this.resource = resource;
      this.resource2 = resource2;
      this.locale = loc;

      reload();
   }

   /**
    * Get catalog getter for a thread group.
    * @return catalog getter which is responsible to get proper catalog.
    */
   public static CatalogGetter getCatalogGetter() {
      CatalogGetter cgetter = ConfigurationContext.getContext().get(GETTER_KEY);

      // no available catalog getter, we use default catalog getter
      if(cgetter == null) {
         cgetter = new CatalogGetter() {
            @Override
            public Catalog getCatalog(Principal user, String type) {
               if(type.equals(Catalog.DEFAULT)) {
                  return getDefaultCatalog("StyleReport.locale.resource");
               }
               else if(type.equals(Catalog.REPORT)) {
                  Locale locale = null;

                  if(user instanceof XPrincipal) {
                     String str =
                        ((XPrincipal) user).getProperty(XPrincipal.LOCALE);

                     if(str != null && str.length() > 0) {
                        locale = parseLocale(str);
                     }
                  }

                  return getDefaultCatalog("sree.bundle", "SreeBundle", locale);
               }
               else {
                  throw new RuntimeException("Unsupported type found: " + type);
               }
            }

            private Catalog getDefaultCatalog(String str) {
               return getDefaultCatalog(str, DEFAULT_RESOURCE, null);
            }

            private Catalog getDefaultCatalog(String str, String defres,
                                              Locale loc) {
               if(loc == null) {
                  loc = ThreadContext.getLocale();
               }

               String resource = SreeEnv.getEarlyLoadedProperty(str, defres);
               return Catalog.getResCatalog(resource, defres, loc);
            }
         };
      }

      return cgetter;
   }

   /**
    * Set catalog getter.
    *
    * @param cgetter the specified catalog getter.
    */
   public static void setCatalogGetter(CatalogGetter cgetter) {
      ConfigurationContext.getContext().put(GETTER_KEY, cgetter);
   }

   /**
    * Get default catalog for Designer, Viewer and EM.
    * @return default catalog.
    */
   public static Catalog getCatalog() {
      return getCatalog(null);
   }

   /**
    * Get default catalog for Designer, Viewer and EM with a principal.
    * In the principal, a locale is available.
    * @param user the specified principal contains locale info.
    * @return default catalog.
    */
   public static Catalog getCatalog(Principal user) {
      return getCatalog(user, DEFAULT);
   }

   /**
    * Get catalog of a catalog type.
    * @param user the specified principal contains locale info.
    * @param type one of the types predefined in Catalog.
    */
   public static Catalog getCatalog(Principal user, String type) {
      CatalogGetter getter = getCatalogGetter();
      return getter.getCatalog(user, type);
   }

   /**
    * Get catalog of a resource.
    * @param resource the specified resource.
    * @return catalog of the specified resource.
    */
   @SuppressWarnings("unused")
   public static Catalog getResCatalog(String resource) {
      return getResCatalog(resource, null);
   }

   /**
    * Get catalog of a resource.
    * @param resource the specified resource.
    * @param loc the specified locale.
    * @return catalog of the specified resource.
    */
   public static Catalog getResCatalog(String resource, Locale loc) {
      return getResCatalog(resource, null, loc);
   }

   /**
    * Get catalog of a resource.
    * @param resource the specified resource.
    * @param resource2 the specified backup resource.
    * @param loc the specified locale.
    * @return catalog of the specified resource.
    */
   public static Catalog getResCatalog(String resource, String resource2,
                                       Locale loc) {
      loc = loc != null ? loc : ThreadContext.getLocale();
      String key = resource + "_" +  loc.toString();
      Catalog catalog = catalogs.get(key);

      if(catalog == null) {
         synchronized(Catalog.class) {
            catalog = catalogs.get(key);

            if(catalog == null) {
               catalog = new Catalog(resource, resource2, loc);
               catalogs.put(key, catalog);
            }
         }
      }

      return catalog;
   }

   /**
    * Get locale object.
    * @param locstr the specified locale string representation.
    */
   public static Locale parseLocale(String locstr) {
      if(locstr == null || locstr.isEmpty() || SUtil.MY_LOCALE.equals(locstr)) {
         return null;
      }

      // only language?
      if(locstr.length() == 2) {
         return Locale.of(locstr, "");
      }
      // language and country?
      else if(locstr.indexOf('_') == 2) {
         return Locale.of(locstr.substring(0, 2), locstr.substring(3));
      }
      else {
         // @by davidd, removed appending of locstr because of XSS.
         throw new RuntimeException("Invalid locale specified: " + locstr);
      }
   }

   /**
    * Clear and reload all the resource bundles.
    */
   @SuppressWarnings("unused")
   public static void reloadAll() {
      ResourceBundle.clearCache();

      for(Object catalog : catalogs.values()) {
         ((Catalog) catalog).reload();
      }
   }

   /**
    * Reloads the resource bundles. This method allows the underlying resource bundle(s)
    * to be dynamically reloaded at runtime.
    */
   public void reload() {
      synchronized(keys) {
         keys.clear();

         // initialize main bundle
         if(resource != null && resource.length() > 0) {
            try {
               bundle = ResourceBundle.getBundle(resource, locale);
            }
            catch(Exception ex) {
               LOG.debug("Failed to get resource bundle: " +
                           resource + " " + locale, ex);
            }
         }

         // store keys to avoid exception, which is too expensive
         Enumeration<String> ekeys = bundle == null ? null : bundle.getKeys();

         while(ekeys != null && ekeys.hasMoreElements()) {
            keys.add(ekeys.nextElement());
         }

	 keys.trim();
      }

      synchronized(keys2) {
         keys2.clear();

         // initialize backup bundle if any
         if(resource2 != null && resource2.length() > 0) {
            try {
               bundle2 = ResourceBundle.getBundle(resource2, locale);
            }
            catch(Exception ex) {
               LOG.debug("Failed to get resource bundle(2): " +
                           resource2 + " " + locale, ex);
            }
         }

         if(bundle2 != null) {
            // store keys to avoid exception, which is too expensive
            Enumeration<String> ekeys2 = bundle2.getKeys();

            while(ekeys2.hasMoreElements()) {
               keys2.add(ekeys2.nextElement());
            }

            keys2.trim();
         }
      }
   }

   /**
    * Get main resource name.
    * @return main resource name.
    */
   public String getResource() {
      return resource;
   }

   /**
    * Get backup resource name.
    * @return backup resource name.
    */
   @SuppressWarnings("unused")
   public String getBackupResource() {
      return resource2;
   }

   /**
    * Get catalog locale.
    * @return catalog locale.
    */
   public Locale getLocale() {
      return locale;
   }

   /**
    * Get strings of an id prefix.
    * @param id_prefix the specified id_prefix MUST end with ".*".
    * @return map contains id-string pairs.
    */
   public Map<String, String> getStrings(String id_prefix) {
      if(id_prefix != null && !id_prefix.endsWith(".*")) {
         throw new RuntimeException("Invalid id prefix found: " + id_prefix);
      }

      Map<String, String> map = new HashMap<>();

      // get strings from main resource bundle
      getStrings(map, id_prefix, bundle, true);

      // get strings from backup resource bundle, here we MUST not
      // override main resource bundle strings to avoid conflict
      getStrings(map, id_prefix, bundle2, false);

      return map;
   }

   /**
    * Get strings of an id prefix.
    * @param map the specfied map.
    * @param id_prefix the specified id_prefix MUST end with ".*".
    * @param bundle the specified resource bundle.
    * @param overridden true if override existing ones.
    */
   private void getStrings(Map<String, String> map, String id_prefix,
                           ResourceBundle bundle, boolean overridden)
   {
      if(bundle == null) {
         return;
      }

      String prefix = id_prefix == null ?
         null : id_prefix.substring(0, id_prefix.length() - 1);

      Tool.toStream(bundle.getKeys())
         .filter((key) -> (prefix == null || key.startsWith(prefix)) &&
            (overridden || !map.containsKey(key)))
         .forEach((key) -> {
            map.put(key, getString(key, false));
         });
   }

   /**
    * Get string of an id with format option.
    * @param id the specified id.
    * @param formatted true if the return string need to be formatted.
    */
   public String getString(String id, boolean formatted) {
      if(!formatted) {
         return getString0(id);
      }
      else {
         return getString(id);
      }
   }

   /**
    * Get the string value of an id.
    * @param id the specified id.
    * @param params the array of the specified parameters.
    * @return the formatted string value of the specified id.
    */
   public String getString(String id, Object... params) {
      return getString(id, params, true);
   }

   /**
    * Get the string value of an id.
    * @param id the specified id.
    * @param params the array of the specified parameters.
    * @return the formatted string value of the specified id, null if not found.
    */
   public String getIDString(String id, Object... params) {
      return getIDString(id, params, true);
   }

   /**
    * Get the string value of an id.
    * @param id the specified id.
    * @param params the array of the specified parameters.
    * @param log true to enable logging.
    * @return the formatted string value of the specified id.
    */
   public String getString(String id, Object[] params, boolean log) {
      String val = getString0(id, log);

      if(val == null) {
         return null;
      }

      // @by larryl, optimization
      if((val.contains("{") || val.contains("'"))
         // @by ChrisSpagnoli bug1372259658127 2015-6-1
         // do not run MessageFormat.format() without params, or it will strip '
         && params.length > 0)
      {
         try {
            String result = MessageFormat.format(val, params);

            // if format result is empty, return original result, because in
            // most case, it is not what we wanted, it should not a format
            // pattern fix bug1371784602942
            if(result.isEmpty()) {
               return val;
            }

            return result;
         }
         catch(Exception ex) {
            return val;
         }
      }
      else {
         return val;
      }
   }

   /**
    * Get the string value of an id.
    * @param id the specified id.
    * @param params the array of the specified parameters.
    * @param log true to enable logging.
    * @return the formatted string value of the specified id, null if not found.
    */
   public String getIDString(String id, Object[] params, boolean log) {
      String val = getIDString0(id, log);

      if(val == null) {
         return null;
      }

      if(val.contains("{") || val.contains("'")) {
         try {
            return MessageFormat.format(val, params);
         }
         catch(Exception ex) {
            return val;
         }
      }
      else {
         return val;
      }
   }

   /**
    * Get string of an id.
    * @param id the specified id.
    * @return string value of the specified id, not yet formatted.
    */
   private String getString0(String id) {
      return getString0(id, true);
   }

   /**
    * Get string of an id.
    * @param id the specified id.
    * @param log true to enable logging.
    * @return string value of the specified id, not yet formatted.
    */
   private String getString0(String id, boolean log) {
      return getString0(id, log, true);
   }

   /**
    * Get string of an id.
    * @param id the specified id.
    * @param log true to enable logging.
    * @return string value of the specified id, not yet formatted,
    * null if not found.
    */
   private String getIDString0(String id, boolean log) {
      return getString0(id, log, false);
   }

   /**
    * Get string of an id.
    * @param id the specified id.
    * @param log true to enable logging.
    * @param def true if return id when not found, false otherwise.
    * @return string value of the specified id, not yet formatted.
    */
   @SuppressWarnings("UnusedParameters")
   private String getString0(String id, boolean log, boolean def) {
      if(id == null || id.endsWith(".*")) {
         return id;
      }

      if("em.datasource.cloudError".equals(id)) {
         String res = SreeEnv.getProperty("datasource.cloudError");
         return Tool.isEmptyString(res) ? "" : res;
      }

      Object obj = null;

      // get string value from main resource bundle
      boolean haskey = keys.contains(id);

      if(haskey) {
         obj = bundle.getObject(id);
      }

      // get string value from backup resource bundle
      if(obj == null) {
         haskey = keys2.contains(id);

         if(haskey) {
            obj = bundle2.getObject(id);

            /*
            if(obj != null && log) {
               LOG.debug(
                       "Get string value from backup resource: %s", id);
            }
            */
         }
      }

      if(obj == null && def) {
         obj = id;
      }

      String res = obj == null ? null : obj.toString();

      if(res == null) {
         return res;
      }

      int idx = res.indexOf("^--^");
      res = idx == -1 ? res : res.substring(0, idx);

      return res;
   }

   /**
    * Get string representation.
    */
   public String toString() {
      return "Catalog: [" + resource + ", " + resource2 + ", " + locale + ", " +
         bundle + ", " + bundle2 + "]";
   }

   /**
    * Get localizeing bundle for netbeans.
    */
   public String getLocalizingBundle() {
      String lbundle = "inetsoft.util.srinter";

      if(bundle != null) {
         lbundle = resource;
         lbundle = Tool.replaceAll(lbundle, "/", ".");
         String locale =
            bundle.getLocale() != null ? bundle.getLocale().toString() : null;
         lbundle += locale != null && !"".equals(locale) ? "_" + locale : "";
      }
      else if(bundle2 != null) {
         lbundle = resource2;
         lbundle = Tool.replaceAll(lbundle, "/", ".");
         String locale =
            bundle2.getLocale() != null ? bundle2.getLocale().toString() : null;
         lbundle += locale != null && !"".equals(locale) ? "_" + locale : "";
      }

      return lbundle;
   }

   /**
    * Reloads the user bundles.
    */
   public void reloadUserBundle() {
      if(bundle != null) {
         ResourceBundle.clearCache();
      }

      reload();
   }

   /**
    * Catalog getter responsible to get proper catalog.
    */
   public interface CatalogGetter {
      /**
       * Get catalog.
       * @param user the specified user which contains locale info.
       * @param type the specified type which is one of the types
       * predefined in Catalog.
       * @return catalog of the specified type.
       */
      Catalog getCatalog(Principal user, String type);
   }
}
