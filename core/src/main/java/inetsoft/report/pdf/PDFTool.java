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
package inetsoft.report.pdf;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

/**
 * PDFTool
 * Based on Adobe 1.7 spec, we are adding a Differences section
 * when we write the encoding in to the PDF for a font.
 * In order to properly subset the font, we must ensure the character
 * is included.
 * <p>
 * Originally created for the tools necessary to properly subset fonts in PDF
 * http://partners.adobe.com/public/developer/en/opentype/glyphlist.txt
 * http://en.wikipedia.org/wiki/Adobe_Glyph_List
 *
 * @author Inetsoft Technology
 * @version 12.0, 1/30/2015
 */
public class PDFTool {
   private static void initGlyphList() {
      InputStream glyphFileStream =
         PDFTool.class.getResourceAsStream("/inetsoft/report/pdf/glyphlist.properties");

      try {
         List<String> lines = IOUtils.readLines(glyphFileStream, "UTF-8");

         // @by stephenwebster
         // The glyphlist is semi-colon delimited.  Create a simple
         // map of the glyph name to the unicode character.
         for(String line : lines) {
            String[] ln = line.split(";");
            String glyphName = ln[0];

            if(ln[1].length() > 4) {
               //handle double byte later if necessary.
               continue;
            }

            Character unicodeValue = (char) Integer.parseInt(ln[1], 16);
            glyphList.put(glyphName, unicodeValue);
         }
      }
      catch(Exception fileReadException) {
         LOG.debug("Unable to initialize glyph listing.  " +
            "PDF font embedding may not work properly.", fileReadException);

      }
   }

   public static char getGlyph(String glyphName) {
      if(glyphList.get(glyphName) != null) {
         return glyphList.get(glyphName);
      }
      else if(glyphName != null && glyphName.startsWith("uni")) {
         // @by stephenwebster, For Bug #9331
         // Some special unicode characters are mapped to special glyph names
         // which are not in the glyphlist. @see PDFPrinter.newRange
         // In order to ensure the character is embedded properly, we simply
         // need to parse back the value which is in the format "uni<unicode value>"
         return (char) Integer.parseInt(glyphName.substring(3), 16);
      }
      else {
         return 0;
      }

   }

   private static final Logger LOG =
      LoggerFactory.getLogger(PDFTool.class);
   private static HashMap<String, Character> glyphList = new HashMap<>();

   static {
      initGlyphList();
   }
}
