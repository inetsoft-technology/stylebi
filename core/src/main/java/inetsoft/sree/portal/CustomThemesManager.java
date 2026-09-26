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
package inetsoft.sree.portal;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.util.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 *
 * @version 14.0, 05/15/2024
 * @author InetSoft Technology Corp
 */
@Service
@Lazy
public class CustomThemesManager implements XMLSerializable, AutoCloseable {
   public static synchronized CustomThemesManager getManager() {
      return ConfigurationContext.getContext().getSpringBean(CustomThemesManager.class);
   }

   public CustomThemesManager(KeyValueStorageManager keyValueStorageManager, DataSpace dataSpace) {
      this.keyValueStorageManager = keyValueStorageManager;
      this.dataSpace = dataSpace;

      try {
         Class<?> clazz = Class.forName("inetsoft.enterprise.theme.CustomThemesImpl");
         Constructor<?> cstr = clazz.getConstructor(KeyValueStorageManager.class);
         impl = (CustomThemesImpl) cstr.newInstance(keyValueStorageManager);
         String name = SreeEnv.getPath("custom.themes.file", "customthemes.xml");

         dataSpace.addChangeListener(null, name, event -> {
            debouncer.debounce("themes", 500L, TimeUnit.MILLISECONDS, this::loadThemes);
         });
      }
      catch(Exception ex) {
         impl = new CustomThemesImpl();
      }
   }

   public Set<CustomTheme> getCustomThemes() {
      return impl.getCustomThemes();
   }

   /**
    * Replaces the whole set of custom themes. This is a full replace of the store: any theme
    * missing from the given set is deleted. Code that changes the current themes must use
    * {@link #updateCustomThemes(ThemesUpdate)} instead, so that the read, the change and the
    * write are not interleaved with another writer in the cluster.
    */
   public void setCustomThemes(Set<CustomTheme> customThemes) {
      impl.setCustomThemes(customThemes);
   }

   /**
    * Changes the custom themes under a cluster-wide lock. The current themes are read from the
    * store after the lock is acquired and passed to the update as a mutable copy. The set the
    * update returns is then written with {@link #setCustomThemes(Set)} before the lock is
    * released, so no other writer in the cluster can read or write the themes in between.
    * <p>
    * Every lookup that decides the change (finding a theme by ID, checking whether an ID is
    * taken, ...) must be done by the update on the set it is given, not on a set read before.
    * Keep the update short: work that does not decide the change, such as notifying other
    * nodes, should be done before or after this call. The lock is reentrant for the thread
    * that holds it, so an update may itself call this method.
    *
    * @param update the change to apply. It returns the set of themes to store, or
    *               <tt>null</tt> to leave the store unchanged.
    * @param <E>    the type of exception the update can throw.
    *
    * @throws E if the update throws. The store is not changed in that case.
    */
   public <E extends Exception> void updateCustomThemes(ThemesUpdate<E> update) throws E {
      Lock lock = getThemesLock();
      lock.lock();

      try {
         Set<CustomTheme> themes = update.apply(new HashSet<>(getCustomThemes()));

         if(themes != null) {
            setCustomThemes(themes);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Gets the cluster-wide lock that serializes the changes of the custom themes.
    */
   protected Lock getThemesLock() {
      return Cluster.getInstance().getLock(THEMES_LOCK_NAME);
   }

   public String getSelectedTheme() {
      return impl.getSelectedTheme();
   }

   public String getGlobalSelectedTheme() {
      return impl.getGlobalSelectedTheme();
   }

   public String getOrgSelectedTheme() {
      return impl.getOrgSelectedTheme();
   }

   /**
    * Gets the selected theme of the specified organization, rather than of the org that is
    * current on this thread. Returns "default" when the organization has no theme selected.
    */
   public String getOrgSelectedTheme(String orgID) {
      return impl.getOrgSelectedTheme(orgID);
   }

   public void setGlobalSelectedTheme(String selectedTheme) {
      impl.setGlobalSelectedTheme(selectedTheme);
   }

   public void setOrgSelectedTheme(String selectedTheme) {
      impl.setOrgSelectedTheme(selectedTheme);
   }

   public void setOrgSelectedTheme(String selectedTheme, String orgID) {
      impl.setOrgSelectedTheme(selectedTheme, orgID);
   }

   public void removeSelectedTheme(String selectedTheme) {
      impl.removeSelectedTheme(selectedTheme);
   }

   public boolean isCustomThemeApplied() {
      return impl.isCustomThemeApplied(this);
   }

   public boolean isEMDarkTheme() {
      return impl.isEMDarkTheme(this);
   }

   public String getScriptThemeCssPath(boolean portal) {
      return impl.getScriptThemeCssPath(portal, this);
   }

   public String getSelectedTheme(Principal user) {
      return impl.getSelectedTheme(user, this);
   }

   /**
    * Updates the jarPath of any theme whose JAR path matches oldPath exactly or starts
    * with oldPath as a folder prefix, keeping KV store metadata in sync with the physical
    * file location after a file or folder rename in the DataSpace.
    */
   public void renameThemeJar(String oldPath, String newPath) {
      updateCustomThemes(themes -> {
         if(themes.isEmpty()) {
            return null;
         }

         boolean changed = false;
         String oldPathPrefix = oldPath + "/";

         for(CustomTheme theme : new ArrayList<>(themes)) {
            String jarPath = theme.getJarPath();

            if(jarPath == null) {
               continue;
            }

            String updatedPath = null;

            if(oldPath.equals(jarPath)) {
               updatedPath = newPath;
            }
            else if(jarPath.startsWith(oldPathPrefix)) {
               updatedPath = newPath + jarPath.substring(oldPath.length());
            }

            if(updatedPath != null) {
               themes.remove(theme);
               theme.setJarPath(updatedPath);
               themes.add(theme);
               changed = true;
            }
         }

         return changed ? themes : null;
      });
   }

   public void reloadThemes(String path) {
      Set<CustomTheme> removed = new HashSet<>();

      updateCustomThemes(themes -> {
         if(themes.isEmpty()) {
            return null;
         }

         Set<CustomTheme> newThemes = new HashSet<>();
         String pathPrefix = path + "/";

         themes.forEach(theme -> {
            String jarPath = theme.getJarPath();

            if(jarPath == null || (!jarPath.equals(path) && !jarPath.startsWith(pathPrefix))) {
               newThemes.add(theme);
            }
            else {
               removed.add(theme);
            }
         });

         return newThemes;
      });

      removed.forEach(t -> removeSelectedTheme(t.getId()));
   }

   @Override
   @PreDestroy
   public void close() throws Exception {
      impl.close();
   }

   public void loadThemes() {
      impl.loadThemes();
   }

   @Override
   public void writeXML(PrintWriter writer) {
      impl.writeXML(writer);
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      impl.parseXML(tag);
   }

   public KeyValueStorageManager getKeyValueStorageManager() {
      return keyValueStorageManager;
   }

   /**
    * A change of the custom themes, applied by {@link #updateCustomThemes(ThemesUpdate)}.
    *
    * @param <E> the type of exception the change can throw.
    */
   @FunctionalInterface
   public interface ThemesUpdate<E extends Exception> {
      /**
       * Changes the themes.
       *
       * @param themes a mutable copy of the current themes, read under the lock.
       *
       * @return the themes to store, or <tt>null</tt> to leave the store unchanged.
       */
      Set<CustomTheme> apply(Set<CustomTheme> themes) throws E;
   }

   /**
    * The name of the cluster lock that serializes the changes of the custom themes. It is a
    * literal so that it stays the same on every node if the class is renamed or moved.
    */
   public static final String THEMES_LOCK_NAME = "inetsoft.sree.portal.CustomThemes.lock";

   private final KeyValueStorageManager keyValueStorageManager;
   private final DataSpace dataSpace;
   private CustomThemesImpl impl;
   private static final Logger LOG = LoggerFactory.getLogger(CustomThemesManager.class);

   private final Debouncer<String> debouncer = new DefaultDebouncer<>();
}
