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

import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

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

   public boolean isCustomThemeApplied(CustomThemesManager customThemesManager) {
      return false;
   }

   public boolean isEMDarkTheme(CustomThemesManager customThemesManager) {
      return false;
   }

   public String getScriptThemeCssPath(boolean portal, CustomThemesManager customThemesManager) {
      return "../" + CustomTheme.ScriptTheme.ECLIPSE.getFileName();
   }

   public String getSelectedTheme(Principal user, CustomThemesManager customThemesManager) {
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
