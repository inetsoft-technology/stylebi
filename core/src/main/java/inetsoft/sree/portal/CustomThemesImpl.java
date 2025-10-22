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
public class CustomThemesImpl implements XMLSerializable, AutoCloseable {
   public Set<CustomTheme> getCustomThemes() {
      return new HashSet<>();
   }

   public void setCustomThemes(Set<CustomTheme> customThemes) {
   }

   public String getSelectedTheme() {
      return "default";
   }

   public String getOrgSelectedTheme() {
      return "default";
   }

   public String getGlobalSelectedTheme() {
      return "default";
   }

   public void setGlobalSelectedTheme(String selectedTheme) {
   }

   public void setOrgSelectedTheme(String selectedTheme) {
   }

   public void setOrgSelectedTheme(String selectedTheme, String orgID) {
   }

   public void removeSelectedTheme(String selectedTheme) {
   }

   public boolean isCustomThemeApplied() {
      return false;
   }

   public boolean isEMDarkTheme() {
      return false;
   }

   public String getScriptThemeCssPath(boolean portal) {
      return "../" + CustomTheme.ScriptTheme.ECLIPSE.getFileName();
   }

   public String getSelectedTheme(Principal user) {
      return "default";
   }

   @Override
   public void writeXML(PrintWriter writer) {
   }

   @Override
   public void parseXML(Element tag) throws Exception {
   }

   public void loadThemes() {
   }

   @Override
   public void close() throws Exception {
   }
}
