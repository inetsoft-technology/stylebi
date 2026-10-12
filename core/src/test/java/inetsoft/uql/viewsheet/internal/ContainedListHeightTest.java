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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ContainedListHeightTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // seeded through the public creation path, so a marked list carries its tier inset
   private SelectionListVSAssemblyInfo list(String density, VizMark mark) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(mark);
      info.initDefaultFormat();
      info.setListHeight(6);
      return info;
   }

   @Test
   void aMarkedListsBodyIsItsRowsInsideItsInset() {
      assertEquals(6 * 28 + 32, list("comfortable", VizMark.MODERN_LIGHT).getListBodyHeight());
      assertEquals(6 * 24 + 24, list("compact", VizMark.MODERN_LIGHT).getListBodyHeight());
      assertEquals(6 * 20 + 16, list("dense", VizMark.MODERN_LIGHT).getListBodyHeight());
   }

   @Test
   void aMarkedListInAContainerFitsItsRows() {
      assertEquals(230, list("comfortable", VizMark.MODERN_LIGHT).getContainedListHeight());
      assertEquals(194, list("compact", VizMark.MODERN_LIGHT).getContainedListHeight());
      assertEquals(156, list("dense", VizMark.MODERN_LIGHT).getContainedListHeight());
   }

   @Test
   void anUnmarkedListKeepsTheLegacyContainedHeight() {
      assertEquals(6 * AssetUtil.defh + AssetUtil.defh,
                   list("comfortable", null).getContainedListHeight());
   }

   // the legacy formula reads defh, not the stored cell height
   @Test
   void anUnmarkedListWithAnAuthorCellHeightStillUsesDefhRows() {
      SelectionListVSAssemblyInfo info = list("comfortable", null);
      info.setCellHeight(25);
      info.setUserCellHeight(true);

      assertEquals(6 * AssetUtil.defh + AssetUtil.defh, info.getContainedListHeight());
   }

   @Test
   void aMarkedListHonoursAnAuthorCellHeight() {
      SelectionListVSAssemblyInfo info = list("comfortable", VizMark.MODERN_LIGHT);
      info.setCellHeight(25);
      info.setUserCellHeight(true);

      assertEquals(30 + 6 * 25 + 32, info.getContainedListHeight());
   }

   // the rows a contained height holds are the rows it was built from
   @Test
   void theFittingRowsInvertTheContainedHeight() {
      for(String density : new String[] { "comfortable", "compact", "dense" }) {
         SelectionListVSAssemblyInfo info = list(density, VizMark.MODERN_LIGHT);
         assertEquals(6, info.getFittingListHeight(info.getContainedListHeight()), density);
      }

      assertEquals(6, list("comfortable", null).getFittingListHeight(140));
   }

   @Test
   void aMarkedListFitsWholeRowsAndAtLeastOne() {
      SelectionListVSAssemblyInfo info = list("comfortable", VizMark.MODERN_LIGHT);

      assertEquals(6, info.getFittingListHeight(250), "188 / 28 drops the partial row");
      assertEquals(1, info.getFittingListHeight(40), "below the lane and inset still keeps one");
   }

   // what the composer has always stored for an unmarked list: no floor
   @Test
   void anUnmarkedListFitsDefhRowsExactly() {
      assertEquals(0, list("comfortable", null).getFittingListHeight(35));
   }
}
