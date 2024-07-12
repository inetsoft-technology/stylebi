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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
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
         return singleton.palettes.keySet();
      }
   }

   /**
    * Get the name of the color palettes for reports.
    */
   public static Collection<String> getPaletteNames(String cssLocation) {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes(cssLocation, true);
         return singleton.palettes.keySet();
      }
   }

   /**
    * Get the named color palette.
    */
   public static CategoricalColorFrame getPalette(String name) {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes();
         return singleton.palettes.get(name);
      }
   }

   /**
    * Get the named color palette for reports.
    */
   public static CategoricalColorFrame getPalette(String name, String cssLocation) {
      synchronized(ColorPalettes.class) {
         singleton.loadPalettes(cssLocation, true);
         return singleton.palettes.get(name);
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

      if(palettes == null || cssDictionary.getLastModifiedTime() > last ||
         !Tool.equals(cssLocation, lastCSSLocation))
      {
         if(!report) {
            try {
               // if colorpalettes.xml exists then convert it to css and
               // append it to format.css in portal
               if(DataSpace.getDataSpace().exists("portal", "colorpalettes.xml")) {
                  convertXMLColorPaletteToCSS();
                  cssDictionary.changeListener.dataChanged(null);
               }
            }
            catch(Exception ex) {
               LOG.error(
                  "Failed to convert colorpalettes.xml to css", ex);
            }
         }

         try {
            // holds the last modified time of the css file so we know
            // to update the palette if the file changes
            last = cssDictionary.getLastModifiedTime();
            lastCSSLocation = cssLocation;
            // get all palette names defined in the css file
            Set<String> paletteNames = cssDictionary.getCSSAttributeValues(
               "ChartPalette", null, "name");
            palettes = new OrderedMap<>();

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
         }
         catch(Exception ex) {
            LOG.error("Failed to load color palette", ex);
         }
      }
   }

   /**
    * Converts palettes in colorpalettes.xml to css based formatting
    */
   private void convertXMLColorPaletteToCSS() throws Exception {
      DataSpace dataspace = DataSpace.getDataSpace();

      try(DataSpace.Transaction tx = dataspace.beginTransaction();
         OutputStream out = tx.newStream("portal", "format.css.new"))
      {

         // if format.css exists then copy the contents to the output stream
         if(dataspace.exists("portal", "format.css")) {
            try(InputStream fInput = dataspace.getInputStream("portal", "format.css")) {
               Tool.copyTo(fInput, out);
            }
         }

         // parse the contents of colorpalettes.xml
         Document doc;

         try(InputStream cpInput = dataspace.getInputStream("portal", "colorpalettes.xml")) {
            doc = Tool.parseXML(cpInput);
         }

         Element elem = Tool.getChildNodeByTagName(doc, "palettes");
         NodeList nodes = Tool.getChildNodesByTagName(elem, "palette");
         PrintWriter writer = new PrintWriter(out);

         for(int i = 0; i < nodes.getLength(); i++) {
            Element pnode = (Element) nodes.item(i);
            String name = Tool.getAttribute(pnode, "name");
            CategoricalColorFrameWrapper frame = new CategoricalColorFrameWrapper();
            frame.parseXML(pnode);
            int colorCount = ((CategoricalColorFrame) frame.getVisualFrame()).getColorCount();

            // write out the colors in the css format
            for(int j = 0; j < colorCount; j++) {
               Color color = frame.getColor(j);
               writer.println("ChartPalette[name='" + name + "']" + "[index='" + (j + 1) + "'] {");
               writer.format("  color: #%02x%02x%02x;%n",color.getRed(), color.getGreen(),
                             color.getBlue());
               writer.println("}\n");
            }
         }

         // release the streams
         writer.flush();
         writer.flush();
         tx.commit();
      }

      // delete existing format.css and colorpalettes.xml
      if(dataspace.exists("portal", "format.css")) {
         dataspace.delete("portal", "format.css");
      }

      dataspace.delete("portal", "colorpalettes.xml");

      // rename format.css.new to format.css
      dataspace.rename(dataspace.getPath("portal", "format.css.new"),
         dataspace.getPath("portal", "format.css"));
   }

   private static ColorPalettes singleton = new ColorPalettes();
   private OrderedMap<String, CategoricalColorFrame> palettes;
   private long last = 0;
   private String lastCSSLocation;
   private static final Logger LOG = LoggerFactory.getLogger(ColorPalettes.class);
}
