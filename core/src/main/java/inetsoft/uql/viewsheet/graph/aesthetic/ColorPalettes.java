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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.*;
import inetsoft.util.css.CSSDictionary;
import inetsoft.util.css.CSSParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * This class tracks configurable color palettes.
 *
 * @version 11.2
 * @author InetSoft Technology
 */
public class ColorPalettes {
   /**
    * Get the name of the color palettes.
    */
   public static Collection<String> getPaletteNames() {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes();
         return singleton.paletteMap.get(OrganizationManager.getInstance().getCurrentOrgID()).keySet();
      }
   }

   /**
    * Get the name of the color palettes for reports.
    */
   public static Collection<String> getPaletteNames(String cssLocation) {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes(cssLocation, true);
         return singleton.paletteMap.get(OrganizationManager.getInstance().getCurrentOrgID()).keySet();
      }
   }

   /**
    * Get the named color palette.
    */
   public static CategoricalColorFrame getPalette(String name) {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes();
         return singleton.paletteMap.get(OrganizationManager.getInstance().getCurrentOrgID()).get(name);
      }
   }

   /**
    * Get the named color palette for reports.
    */
   public static CategoricalColorFrame getPalette(String name, String cssLocation) {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes(cssLocation, true);
         return singleton.paletteMap.get(OrganizationManager.getInstance().getCurrentOrgID()).get(name);
      }
   }

   private void loadPalettes() {
      loadPalettes(null, false);
   }

   /**
    * Load color palettes if necessary.
    */
   private void loadPalettes(String cssLocation, boolean report) {
      CSSDictionary cssDictionary;

      if(report) {
         cssDictionary = CSSDictionary.getDictionary(cssLocation);
      }
      else {
         cssDictionary = CSSDictionary.getDictionary();
      }

      if(paletteMap.get(OrganizationManager.getInstance().getCurrentOrgID()) == null ||
         CSSDictionary.getOrgScopedCSSLastModified(cssDictionary) > last ||
         !Tool.equals(cssLocation, lastCSSLocation))
      {
         try {
            // holds the last modified time of the css file so we know
            // to update the palette if the file changes
            last = CSSDictionary.getOrgScopedCSSLastModified(cssDictionary);
            lastCSSLocation = cssLocation;
            // get all palette names defined in the css file
            Set<String> paletteNames = cssDictionary.getCSSAttributeValues(
               "ChartPalette", null, "name");
            OrderedMap palettes = new OrderedMap<>();

            for(String name : paletteNames) {
               Map<String, String> attributes = new HashMap<>();
               attributes.put("name", name);
               // get all index values for the given palette
               Set<String> strIndexValues = cssDictionary.getCSSAttributeValues(
                  "ChartPalette", attributes, "index");
               List<Integer> indexValues = new ArrayList<>();
               int max = 0;

               // convert the indexes to integers and find the max value so we
               // know the range of colors that we need to initialize
               for(String strIndex : strIndexValues) {
                  int index = Integer.parseInt(strIndex);
                  indexValues.add(index);

                  if(index > max) {
                     max = index;
                  }
               }

               Color[] colors = new Color[max];

               for(int i : indexValues) {
                  // color indexing starts with 1 so anything below 1 is not valid
                  if(i < 1) {
                     continue;
                  }

                  attributes.put("index", i + "");
                  colors[i - 1] = cssDictionary.getForeground(
                     new CSSParameter("ChartPalette", null, null, new HashMap<>(attributes)));
               }

               // create a new palette and add it to available palettes
               CategoricalColorFrame frame = new CategoricalColorFrame();
               frame.setDefaultColors(colors);
               palettes.put(name, frame);
            }

            paletteMap.put(OrganizationManager.getInstance().getCurrentOrgID(), palettes);
         }
         catch(Exception ex) {
            LOG.error("Failed to load color palette", ex);
         }
      }
   }

   private static ColorPalettes singleton = new ColorPalettes();
   private HashMap<String, OrderedMap<String, CategoricalColorFrame>> paletteMap = new HashMap<>();
   private long last = 0;
   private String lastCSSLocation;
   private static final Logger LOG = LoggerFactory.getLogger(ColorPalettes.class);
}
