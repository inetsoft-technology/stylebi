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

import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 *
 * @version 14.0, 05/15/2024
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(CustomThemesManager.Reference.class)
public class CustomThemesManager implements XMLSerializable, AutoCloseable {
   public static synchronized CustomThemesManager getManager() {
      return SingletonManager.getInstance(CustomThemesManager.class);
   }

   public CustomThemesManager() {
      try {
         impl = (CustomThemesImpl) Class.forName("inetsoft.enterprise.theme.CustomThemesImpl").newInstance();
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

   public void setSelectedTheme(String selectedTheme) {
      impl.setSelectedTheme(selectedTheme);
   }

   public boolean isCustomThemeApplied() {
      return impl.isCustomThemeApplied();
   }

   public boolean isEMDarkTheme() {
      return impl.isEMDarkTheme();
   }

   public String getScriptThemeCssPath(boolean portal) {
      return impl.getScriptThemeCssPath(portal);
   }

   public String getSelectedTheme(Principal user) {
      return impl.getSelectedTheme(user);
   }

   public void save() {
      impl.save();
   }

   public void loadThemes() {
      impl.loadThemes();
   }

   public void reloadThemes(String path) {
      if(getCustomThemes() == null || getCustomThemes().isEmpty()) {
         return;
      }

      Set<CustomTheme> newThemes = new HashSet<>();

      getCustomThemes().forEach(theme -> {
         String jarPath = theme.getJarPath();

         if(jarPath != null && jarPath.startsWith(path)) {
            CustomTheme newTheme = (CustomTheme) theme.clone();
            newTheme.setEMDark(false);
            newTheme.setPortalScript(null);
            newTheme.setEmScript(null);
            newThemes.add(newTheme);
         }
         else {
            newThemes.add(theme);
         }
      });

      setCustomThemes(newThemes);
      save();
   }

   @Override
   public void close() throws Exception {
      impl.close();
   }

   @Override
   public void writeXML(PrintWriter writer) {
      impl.writeXML(writer);
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      impl.parseXML(tag);
   }

   private CustomThemesImpl impl;
   private static final Logger LOG = LoggerFactory.getLogger(CustomThemesManager.class);

   @SingletonManager.ShutdownOrder()
   public static final class Reference extends SingletonManager.Reference<CustomThemesManager> {
      @Override
      public synchronized CustomThemesManager get(Object... parameters) {
         if(manager == null) {
            manager = new CustomThemesManager();
            manager.loadThemes();
         }

         return manager;
      }

      @Override
      public void dispose() {
         if(manager != null) {
            try {
               manager.close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close theme manager", e);
            }

            manager = null;
         }
      }

      private CustomThemesManager manager;
   }
}
