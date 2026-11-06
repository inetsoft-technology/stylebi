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
import inetsoft.report.TableDataPath;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.DataSpace;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Insets;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reset to modern defaults and Keep my formatting leave a card looking like a new assembly of
 * its type: the card tint and no frame, or no card inside a selection container.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernizeCardSurfaceTest {
   @BeforeEach
   void setUp() throws Exception {
      removeFormatCss();
   }

   @AfterEach
   void reset() throws Exception {
      removeFormatCss();
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private static void gateOn(boolean dark) {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", Boolean.toString(dark));
   }

   private static void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   // reset before deleting, so the dictionary's file listener cannot recreate it
   private static void removeFormatCss() throws Exception {
      CSSDictionary.resetDictionaryCache();
      DataSpace space = DataSpace.getDataSpace();

      if(space.exists("portal", "format.css")) {
         space.delete("portal", "format.css");
      }

      CSSDictionary.resetDictionaryCache();
   }

   private static final List<BiFunction<Viewsheet, String, VSAssembly>> CARD_TYPES = List.of(
      ChartVSAssembly::new, TableVSAssembly::new, SelectionListVSAssembly::new,
      CurrentSelectionVSAssembly::new);

   private static VSAssemblyInfo add(Viewsheet vs, VSAssembly assembly) {
      assembly.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(assembly);
      return assembly.getVSAssemblyInfo();
   }

   /** One assembly of each card type, created under the current gate. */
   private static List<VSAssemblyInfo> addCards(Viewsheet vs) {
      return CARD_TYPES.stream()
         .map(type -> add(vs, type.apply(vs, "Card" + CARD_TYPES.indexOf(type))))
         .toList();
   }

   /** A selection list grouped into a selection container, as GroupingService does. */
   private static VSAssemblyInfo addGroupedList(Viewsheet vs) {
      add(vs, new CurrentSelectionVSAssembly(vs, "Container1"));
      VSAssemblyInfo list = add(vs, new SelectionListVSAssembly(vs, "Child1"));
      ((CurrentSelectionVSAssembly) vs.getAssembly("Container1"))
         .setAssemblies(new String[] { "Child1" });
      CurrentSelectionVSAssemblyInfo.setChildBorder(list);
      list.seedCardSurface(VizContext.of(list));
      return list;
   }

   /** What a render shows of the card at the object path. */
   private static List<Object> card(VSAssemblyInfo info) {
      VSCompositeFormat fmt = info.getFormat();
      return Arrays.asList(fmt.getBackground(), fmt.getAlpha(), fmt.getBorders(),
                           fmt.getBorderColors(), fmt.getRoundCorner());
   }

   private static VSFormat user(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getUserDefinedFormat();
   }

   private static void authorCard(VSAssemblyInfo info) {
      VSFormat user = user(info);
      user.setBackgroundValue("0x00ff00");
      user.setAlphaValue(60);
      user.setBordersValue(new Insets(StyleConstants.MEDIUM_LINE, StyleConstants.MEDIUM_LINE,
                                      StyleConstants.MEDIUM_LINE, StyleConstants.MEDIUM_LINE));
      user.setBorderColorsValue(new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED));
   }

   private static void assertLikeNew(List<VSAssemblyInfo> infos, List<VSAssemblyInfo> fresh) {
      for(int i = 0; i < infos.size(); i++) {
         VSAssemblyInfo info = infos.get(i);
         String type = info.getClass().getSimpleName();
         assertTrue(fresh.get(i).getFormat().isDefaultCardTint(),
                    "premise: a new " + type + " is a tinted card");
         assertTrue(info.getFormat().isDefaultCardTint(), type + " takes the tint");
         assertEquals(card(fresh.get(i)), card(info), type);
      }
   }

   @ParameterizedTest(name = "dark={0}")
   @ValueSource(booleans = { false, true })
   void aResetCardWithNoAuthorLookMatchesANewOne(boolean dark) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      List<VSAssemblyInfo> cards = addCards(vs);
      assertFalse(cards.get(0).getFormat().isDefaultCardTint(), "premise: a classic chart");

      gateOn(dark);
      VizModernizeUtil.resetToModern(vs);

      assertLikeNew(cards, addCards(new Viewsheet()));
   }

   @ParameterizedTest(name = "dark={0}")
   @ValueSource(booleans = { false, true })
   void aResetCardDropsTheAuthorLookAndMatchesANewOne(boolean dark) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      List<VSAssemblyInfo> cards = addCards(vs);
      cards.forEach(ModernizeCardSurfaceTest::authorCard);

      gateOn(dark);
      VizModernizeUtil.resetToModern(vs);

      assertLikeNew(cards, addCards(new Viewsheet()));
   }

   @Test
   void aResetCardOnAMarkedSheetMatchesANewOne() {
      gateOn(false);
      Viewsheet vs = new Viewsheet();
      List<VSAssemblyInfo> cards = addCards(vs);
      cards.forEach(ModernizeCardSurfaceTest::authorCard);

      VizModernizeUtil.resetToModern(vs);

      assertLikeNew(cards, addCards(new Viewsheet()));
   }

   @ParameterizedTest(name = "dark={0}")
   @ValueSource(booleans = { false, true })
   void aResetContainerChildMatchesANewChild(boolean dark) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      VSAssemblyInfo child = addGroupedList(vs);
      authorCard(child);

      gateOn(dark);
      VizModernizeUtil.resetToModern(vs);

      VSAssemblyInfo fresh = addGroupedList(new Viewsheet());
      assertFalse(fresh.getFormat().isDefaultCardTint(), "premise: the container is the card");
      assertFalse(child.getFormat().isDefaultCardTint());
      assertEquals(card(fresh), card(child));
   }

   @ParameterizedTest(name = "dark={0}")
   @ValueSource(booleans = { false, true })
   void keepGivesAClassicCardWithNoAuthorBackgroundTheNewCard(boolean dark) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      List<VSAssemblyInfo> cards = addCards(vs);

      gateOn(dark);
      VizModernizeUtil.modernize(vs);

      assertLikeNew(cards, addCards(new Viewsheet()));
   }

   @ParameterizedTest(name = "dark={0}")
   @ValueSource(booleans = { false, true })
   void keepLeavesAnAuthorBackgroundOpaque(boolean dark) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      List<VSAssemblyInfo> cards = addCards(vs);
      cards.forEach(info -> user(info).setBackgroundValue("0x00ff00"));

      gateOn(dark);
      VizModernizeUtil.modernize(vs);

      for(VSAssemblyInfo info : cards) {
         String type = info.getClass().getSimpleName();
         assertEquals(new Color(0x00ff00), info.getFormat().getBackground(), type);
         assertEquals(100, info.getFormat().getAlpha(), type + " drops the tint's alpha");
         assertTrue(user(info).isBackgroundValueDefined(), type + " keeps the author value");
      }
   }

   @ParameterizedTest(name = "dark={0}")
   @ValueSource(booleans = { false, true })
   void keepGivesAClassicContainerChildNoCard(boolean dark) {
      gateOff();
      Viewsheet vs = new Viewsheet();
      VSAssemblyInfo child = addGroupedList(vs);

      gateOn(dark);
      VizModernizeUtil.modernize(vs);

      assertEquals(card(addGroupedList(new Viewsheet())), card(child));
   }

   @Test
   void theResetLeavesNoTintOnTheTitleOrCells() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      TableVSAssemblyInfo table = (TableVSAssemblyInfo) add(vs, new TableVSAssembly(vs, "T1"));

      gateOn(false);
      VizModernizeUtil.resetToModern(vs);
      Viewsheet other = new Viewsheet();
      TableVSAssemblyInfo fresh = (TableVSAssemblyInfo) add(other, new TableVSAssembly(other, "T2"));

      for(TableDataPath path : new TableDataPath[] {
         VSAssemblyInfo.TITLEPATH, new TableDataPath(-1, TableDataPath.DETAIL) })
      {
         assertEquals(fresh.getFormatInfo().getFormat(path, false).getBackground(),
                      table.getFormatInfo().getFormat(path, false).getBackground(), path.toString());
         assertEquals(fresh.getFormatInfo().getFormat(path, false).getAlpha(),
                      table.getFormatInfo().getFormat(path, false).getAlpha(), path.toString());
      }
   }
}
