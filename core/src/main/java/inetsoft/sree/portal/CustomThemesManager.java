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
package inetsoft.sree.portal;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.gui.GuiTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.xpath.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
