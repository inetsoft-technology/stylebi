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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A card's bottom inset travels with the table's last row: a last row that fits without it,
 * but not with it, moves to the next page with its band.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PrintTableCardBottomTest {
   @Test
   void aLastRowThatFitsOnlyWithoutTheInsetMovesToTheNextPage() {
      // a header and 34 rows fill 700 of the 710 below the band's top; 716 does not fit
      List<TablePaintable> regions = new PrintTableFixture().inset(16, 16, 16).rows(34).regions();

      assertEquals(List.of(33, 1), heights(regions));
   }

   @Test
   void withoutAnInsetTheLastRowStaysOnThePage() {
      assertEquals(List.of(34), heights(new PrintTableFixture().rows(34).regions()));
   }

   @Test
   void fitNextCountsTheBottomInsetOnTheLastRegion() {
      TableElementDef table = laidOut(new PrintTableFixture().inset(16, 16, 16).rows(34));

      assertEquals(-1, table.fitNext(700), "700 holds the rows but not the 16pt bottom inset");
      assertEquals(1, table.fitNext(716));
   }

   @Test
   void withoutAnInsetFitNextCountsTheRowsOnly() {
      assertEquals(1, laidOut(new PrintTableFixture().rows(34)).fitNext(700));
   }

   // an 800pt area holds every row and the inset in one region, which is then the last
   private static TableElementDef laidOut(PrintTableFixture fixture) {
      TableElementDef table = fixture.element();
      ReportSheet report = table.getReport();
      report.printBox = new Rectangle(56, 46, 400, 800);
      report.printHead = new Position(0, 0);
      report.frames = new Rectangle[] { report.printBox };
      report.npframes = null;
      report.currFrame = 0;
      table.validate(new StylePage(new Dimension(612, 900)), report, null);
      return table;
   }

   private static List<Integer> heights(List<TablePaintable> regions) {
      return regions.stream().map(r -> r.getTableRegion().height).toList();
   }
}
