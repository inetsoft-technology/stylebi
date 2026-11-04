/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.TableDataPath;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChromeSlotTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private static VSFormat user(VSAssemblyInfo info, TableDataPath path) {
      VSCompositeFormat fmt = info.getFormatInfo().getFormat(path);

      if(fmt == null) {
         fmt = new VSCompositeFormat();
         info.getFormatInfo().setFormat(path, fmt);
      }

      return fmt.getUserDefinedFormat();
   }

   private static ChromeSlot find(List<ChromeSlot> slots, String key) {
      return slots.stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
   }

   private static ChromeSlot find(VSAssemblyInfo info, TableDataPath path,
                                  FormatChromeSlots.Property prop)
   {
      return find(info.chromeSlots(), FormatChromeSlots.key(path, prop));
   }

   private static TextVSAssemblyInfo text() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly assembly = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(assembly);
      return (TextVSAssemblyInfo) assembly.getVSAssemblyInfo();
   }

   private static TableVSAssemblyInfo table(String name) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly assembly = new TableVSAssembly(vs, name);
      vs.addAssembly(assembly);
      return (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();
   }

   @Test
   void eachStoredPathYieldsTheEightLookSlots() {
      TextVSAssemblyInfo info = text();
      List<ChromeSlot> slots = info.chromeSlots();

      for(FormatChromeSlots.Property prop : FormatChromeSlots.Property.values()) {
         ChromeSlot slot = find(slots, FormatChromeSlots.key(VSAssemblyInfo.OBJECTPATH, prop));
         assertTrue(slot.objectLevel());
      }
   }

   @Test
   void clearingASlotUndefinesOnlyThatProperty() {
      TextVSAssemblyInfo info = text();
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setForegroundValue("#ff0000");
      user.setAlignmentValue(StyleConstants.H_RIGHT, true);
      ChromeSlot fg = find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FOREGROUND);
      assertTrue(fg.isAuthorSet(info));
      assertEquals(Color.RED, fg.authorValue(info));
      fg.clearAuthor(info);
      assertFalse(fg.isAuthorSet(info));
      assertTrue(user.isAlignmentValueDefined());
   }

   @Test
   void numberFormatAlignmentAndWrapAreNotSlots() {
      TableVSAssemblyInfo info = table("Table1");
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setFormatValue("0.00", true);
      user.setAlignmentValue(StyleConstants.H_RIGHT, true);
      user.setWrappingValue(true, true);
      VSAssemblyInfo fresh = table("Fresh");

      for(ChromeSlot slot : info.chromeSlots()) {
         slot.reset(info, fresh);
      }

      assertTrue(user.isFormatValueDefined());
      assertTrue(user.isAlignmentValueDefined());
      assertTrue(user.isWrappingValueDefined());
   }

   @Test
   void anExpressionBoundForegroundIsFlagged() {
      TextVSAssemblyInfo info = text();
      user(info, VSAssemblyInfo.OBJECTPATH).setForegroundValue("=if(true,'red','blue')");
      ChromeSlot fg = find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FOREGROUND);
      assertTrue(fg.isAuthorSet(info));
      assertTrue(fg.isExpression(info));
   }

   @Test
   void aTitleFontResetsWhole() {
      TableVSAssemblyInfo info = table("Table1");
      VSFormat user = user(info, VSAssemblyInfo.TITLEPATH);
      user.setFontValue(new StyleFont("Arial", Font.BOLD, 16));
      ChromeSlot font = find(info, VSAssemblyInfo.TITLEPATH, FormatChromeSlots.Property.FONT);
      font.reset(info, table("Fresh"));
      assertFalse(user.isFontValueDefined());
   }

   @Test
   void anObjectFontKeepsSizeAndStyleAndTakesTheFreshFamily() {
      TextVSAssemblyInfo info = text();
      TextVSAssemblyInfo fresh = text();
      fresh.initDefaultFormat();
      Font freshFont = fresh.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getDefaultFormat().getFontValue();
      assertNotNull(freshFont);
      assertTrue(StyleFont.isDefaultFont(freshFont));
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setFontValue(new StyleFont("Courier New", Font.ITALIC, 20));
      ChromeSlot font = find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FONT);
      font.reset(info, fresh);
      Font result = user.getFontValue();
      assertTrue(user.isFontValueDefined());
      assertTrue(StyleFont.isDefaultFont(result));
      assertEquals(freshFont.getName(), result.getName());
      assertEquals(Font.ITALIC, result.getStyle());
      assertEquals(20, result.getSize());
   }

   @Test
   void anObjectFontKeepsUnderlineAndStrikethrough() {
      TextVSAssemblyInfo info = text();
      TextVSAssemblyInfo fresh = text();
      fresh.initDefaultFormat();
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setFontValue(new StyleFont("Courier New", Font.BOLD, 14,
                                      StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
      find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FONT).reset(info, fresh);
      StyleFont result = (StyleFont) user.getFontValue();
      assertEquals(StyleConstants.THIN_LINE, result.getUnderlineStyle());
      assertEquals(StyleConstants.THIN_LINE, result.getStrikelineStyle());
      assertEquals(14, result.getSize());
   }

   @Test
   void anUntouchedValueEqualsItsResolvedValue() {
      TextVSAssemblyInfo info = text();
      user(info, VSAssemblyInfo.OBJECTPATH).setForegroundValue("#ff0000");
      ChromeSlot fg = find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FOREGROUND);
      assertEquals(fg.authorValue(info), fg.resolvedValue(info));
   }

   @Test
   void aScriptColourNeverEchoesAsTheDesignValue() {
      TextVSAssemblyInfo info = text();
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setForegroundValue("#00ff00");
      user.setForeground(Color.RED);
      ChromeSlot fg = find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FOREGROUND);
      assertEquals(Color.GREEN, fg.authorValue(info));
      TextVSAssemblyInfo clone = (TextVSAssemblyInfo) info.clone();
      fg.clearAuthor(clone);
      assertNotEquals(Color.RED, fg.resolvedValue(clone));
   }


   @Test
   void clearingAnAuthorFontUndefinesBothTiers() {
      TextVSAssemblyInfo info = text();
      info.initDefaultFormat();
      VSCompositeFormat fmt = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      Font expected = fmt.getFont();
      assertNotNull(expected);
      Font author = new StyleFont("Arial", Font.BOLD, 16);
      VSFormat user = fmt.getUserDefinedFormat();
      // both tiers defined, as a loaded asset has them
      user.setFontValue(author);
      user.setFont(author, true);

      find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FONT).clearAuthor(info);

      assertFalse(user.isFontValueDefined());
      assertFalse(user.isFontDefined());
      assertEquals(expected, fmt.getFont(), "the default tier shows through at render");
   }

   @Test
   void clearingAnAuthorForegroundUndefinesBothTiers() {
      TextVSAssemblyInfo info = text();
      info.initDefaultFormat();
      VSCompositeFormat fmt = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      fmt.getDefaultFormat().setForegroundValue("0x0000ff");
      fmt.getDefaultFormat().setForeground(Color.BLUE);
      VSFormat user = fmt.getUserDefinedFormat();
      user.setForegroundValue("0xff0000", true);
      user.setForeground(Color.RED, true);

      find(info, VSAssemblyInfo.OBJECTPATH, FormatChromeSlots.Property.FOREGROUND)
         .clearAuthor(info);

      assertFalse(user.isForegroundValueDefined());
      assertFalse(user.isForegroundDefined());
      assertEquals(Color.BLUE, fmt.getForeground(), "the default tier shows through at render");
   }

   @Test
   void clearingUndefinesTheRuntimeTierOfEveryProperty() {
      TextVSAssemblyInfo info = text();
      VSFormat user = user(info, VSAssemblyInfo.OBJECTPATH);
      user.setForeground(Color.RED, true);
      user.setBackground(Color.RED, true);
      user.setGradientColor(new GradientColor(), true);
      user.setFont(new StyleFont("Arial", Font.BOLD, 16), true);
      user.setBorders(new Insets(1, 1, 1, 1), true);
      user.setBorderColors(new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED), true);
      user.setAlpha(50, true);
      user.setRoundCorner(5, true);

      for(FormatChromeSlots.Property prop : FormatChromeSlots.Property.values()) {
         find(info, VSAssemblyInfo.OBJECTPATH, prop).clearAuthor(info);
      }

      assertFalse(user.isForegroundDefined());
      assertFalse(user.isBackgroundDefined());
      assertFalse(user.isGradientColorDefined());
      assertFalse(user.isFontDefined());
      assertFalse(user.isBordersDefined());
      assertFalse(user.isBorderColorsDefined());
      assertFalse(user.isAlphaDefined());
      assertFalse(user.isRoundCornerDefined());
   }

   @Test
   void paddingSlotClearsUserPadding() {
      TextVSAssemblyInfo info = text();
      info.setUserPadding(true);
      find(info.chromeSlots(), "padding").reset(info, null);
      assertFalse(info.isUserPadding());
   }

   @Test
   void tableCellPathsAreNotResidueEligible() {
      TableVSAssemblyInfo info = table("Table1");
      TableDataPath[] cells = {
         new TableDataPath(-1, TableDataPath.HEADER),
         new TableDataPath(-1, TableDataPath.HIDDEN_SUMMARY) };

      for(TableDataPath cell : cells) {
         user(info, cell);

         for(FormatChromeSlots.Property prop : FormatChromeSlots.Property.values()) {
            // cells read whether these object values are set
            boolean cellsRead = prop == FormatChromeSlots.Property.FONT ||
               prop == FormatChromeSlots.Property.ALPHA ||
               prop == FormatChromeSlots.Property.BORDER_COLORS;
            assertFalse(find(info, cell, prop).residueEligible());
            assertEquals(!cellsRead,
                         find(info, VSAssemblyInfo.OBJECTPATH, prop).residueEligible());
         }
      }
   }

   @Test
   void tableStyleResetsToTheFreshValue() {
      TableVSAssemblyInfo info = table("Table1");
      TableVSAssemblyInfo fresh = table("Fresh");
      info.setTableStyleValue("X");
      String style = fresh.getTableStyleValue();
      assertNotEquals("X", style);
      find(info.chromeSlots(), "table:style").reset(info, fresh);
      assertEquals(style, info.getTableStyleValue());
   }

   @Test
   void shapesAndImagesHaveNoSlots() {
      Viewsheet vs = new Viewsheet();
      RectangleVSAssembly rect = new RectangleVSAssembly(vs, "Rect1");
      ImageVSAssembly image = new ImageVSAssembly(vs, "Image1");

      for(VSAssemblyInfo info : new VSAssemblyInfo[] {
         rect.getVSAssemblyInfo(), image.getVSAssemblyInfo() })
      {
         user(info, VSAssemblyInfo.OBJECTPATH).setForegroundValue("#ff0000");
         assertTrue(info.chromeSlots().isEmpty());
      }
   }
}
