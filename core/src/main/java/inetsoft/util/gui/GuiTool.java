/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.gui;

import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.util.DataSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.InputStream;
import java.util.List;
import java.util.*;

/**
 * Common functions used in gui classes.
 *
 * @author InetSoft Technology Corp
 * @version 8.5
 */
public class GuiTool {
   public static String[] getAllFonts() {
      List<String> fontLists = new ArrayList<>(PortalThemesManager.getManager().getUserFonts());
      fontLists.add(0, StyleFont.DEFAULT_FONT_FAMILY);
      fontLists.addAll(Arrays.asList(Common.getAllFonts()));
      return fontLists.toArray(new String[0]);
   }

   /**
    * Get the user font
    */
   public static Font getUserFont(String fontName, String fileName) {
      DataSpace space = DataSpace.getDataSpace();

      try(InputStream in = space.getInputStream("portal/font", fileName + ".ttf")) {
         return new StyleFont(Font.createFont(Font.TRUETYPE_FONT, in), fontName);
      }
      catch(Exception ex) {
         LOG.warn("Failed to load user font: " + fontName);
      }

      return null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(GuiTool.class);
}
