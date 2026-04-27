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

   public void setCustomThemes(Set<CustomTheme> customThemes) {
      impl.setCustomThemes(customThemes);
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

   public void reloadThemes(String path) {
      if(getCustomThemes() == null || getCustomThemes().isEmpty()) {
         return;
      }

      Set<CustomTheme> newThemes = new HashSet<>();

      getCustomThemes().forEach(theme -> {
         String jarPath = theme.getJarPath();

         if(jarPath == null || !jarPath.startsWith(path)) {
            newThemes.add(theme);
         }
      });

      setCustomThemes(newThemes);
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

   private final KeyValueStorageManager keyValueStorageManager;
   private final DataSpace dataSpace;
   private CustomThemesImpl impl;
   private static final Logger LOG = LoggerFactory.getLogger(CustomThemesManager.class);

   private final Debouncer<String> debouncer = new DefaultDebouncer<>();
}
