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
package inetsoft.util.css;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
public class CSSDictionaryTest {
   @BeforeEach
   void setup() {
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void simpleCssVariables() {
      CSSDictionary cssDictionary = getCSSDictionary("CSSDictionaryTest.variables.css");
      CSSParameter cssParameter = new CSSParameter("test", "", "simple-variables", null);

      assertEquals(Color.RED, cssDictionary.getBackground(cssParameter));
      assertEquals(Color.BLUE, cssDictionary.getForeground(cssParameter));

      StyleFont font = new StyleFont("Courier", Font.BOLD, 20);
      assertEquals(font, cssDictionary.getFont(cssParameter));

      int align = StyleConstants.H_RIGHT | StyleConstants.V_CENTER;
      assertEquals(align, cssDictionary.getAlignment(cssParameter));

      Insets borderStyle = new Insets(StyleConstants.DOUBLE_LINE, StyleConstants.DOUBLE_LINE,
                                      StyleConstants.DOUBLE_LINE, StyleConstants.DOUBLE_LINE);
      assertEquals(borderStyle, cssDictionary.getBorders(cssParameter));

      BorderColors borderColors = new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED);
      assertEquals(borderColors, cssDictionary.getBorderColors(cssParameter));

      assertEquals(50, cssDictionary.getAlpha(cssParameter));
      assertTrue(cssDictionary.isWrapping(cssParameter));
      assertEquals(10, cssDictionary.getBorderRadius(cssParameter));

      Insets padding = new Insets(2, 4, 2, 4);
      assertEquals(padding, cssDictionary.getPadding(cssParameter));

      assertEquals(200, cssDictionary.getWidth(cssParameter));
      assertEquals(250, cssDictionary.getHeight(cssParameter));
      assertFalse(cssDictionary.isVisible(cssParameter));
   }

   @Test
   void variableOverride() {
      CSSDictionary cssDictionary = getCSSDictionary("CSSDictionaryTest.variable-override.css");
      CSSParameter cssParameter = new CSSParameter("test", "", "variable-override", null);
      Insets borderStyle = new Insets(StyleConstants.THIN_LINE, StyleConstants.DOUBLE_LINE,
                                      StyleConstants.THIN_LINE, StyleConstants.DOUBLE_LINE);
      assertEquals(borderStyle, cssDictionary.getBorders(cssParameter));

      BorderColors borderColors = new BorderColors(Color.RED, Color.RED, Color.GRAY, Color.BLUE);
      assertEquals(borderColors, cssDictionary.getBorderColors(cssParameter));
   }

   @Test
   void variableNotOverridden() {
      CSSDictionary cssDictionary = getCSSDictionary("CSSDictionaryTest.variable-override.css");
      // css param without the class set
      CSSParameter cssParameter = new CSSParameter("test", "", "", null);
      Insets borderStyle = new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                                      StyleConstants.THIN_LINE, StyleConstants.THIN_LINE);
      assertEquals(borderStyle, cssDictionary.getBorders(cssParameter));

      BorderColors borderColors = new BorderColors(Color.RED, Color.RED, Color.GRAY, Color.GRAY);
      assertEquals(borderColors, cssDictionary.getBorderColors(cssParameter));
   }

   @Test
   void shouldMatchClassOneAndTwo() {
      CSSDictionary cssDictionary = getCSSDictionary("CSSDictionaryTest.multiple-classes.css");
      CSSParameter cssParameter = new CSSParameter("", "", "class-one,class-two", null);

      assertEquals(Color.BLUE, cssDictionary.getBackground(cssParameter));
      assertEquals(500, cssDictionary.getWidth(cssParameter));
      assertEquals(Color.RED, cssDictionary.getForeground(cssParameter));
   }

   @Test
   void shouldNotMatchClassThree() {
      CSSDictionary cssDictionary = getCSSDictionary("CSSDictionaryTest.multiple-classes.css");
      CSSParameter cssParameter = new CSSParameter("", "", "class-three", null);
      assertNotEquals(Color.MAGENTA, cssDictionary.getForeground(cssParameter));
      assertNotEquals(Color.MAGENTA, cssDictionary.getBackground(cssParameter));
   }

   private CSSDictionary getCSSDictionary(String name) {
      DataSpace space = DataSpace.getDataSpace();

      try {
         space.withOutputStream("css", name, out ->
            Tool.fileCopy(CSSDictionaryTest.class.getResourceAsStream(name), out));
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to copy the css file", e);
      }

      return CSSDictionary.getDictionary("css", name, false);
   }
}
