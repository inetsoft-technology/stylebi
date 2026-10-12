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
package inetsoft.report.script.graal;

import inetsoft.report.filter.SumFormula;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77910: a cell range summary whose column name resolution in
 * {@code PositionalCellRange.getCellRegion} hits a lost swap file reaches the caller as the
 * swap file read failure instead of a null summary. Other failures there still give null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ReportGraalSummarizeSwapLostTest {
   @Test
   void cellRegionLostSwapFileThrowsTheSwapFailure() {
      try(LostSwapFile lost = new LostSwapFile()) {
         HeaderTable table = new HeaderTable(() -> lost.read());
         // sanity: the range is summarized through getCellRegion, 1 + 2 + 3
         assertEquals(6.0, ((Number) sum(table)).doubleValue());

         table.failFrom = headerReadsBeforeTheRegion(table);
         SwapFileReadException ex = assertThrows(SwapFileReadException.class, () -> sum(table));
         assertEquals(lost.getFile(), ex.getFile());
      }
   }

   @Test
   void cellRegionOtherFailureIsStillANullSummary() {
      HeaderTable table = new HeaderTable(() -> {
         throw new IllegalStateException("broken header");
      });
      table.failFrom = headerReadsBeforeTheRegion(table);
      assertNull(sum(table));
   }

   private static Object sum(HeaderTable table) {
      table.headerReads = 0;
      return ReportGraalJavaScriptEngine.summarize(table, RANGE, "sum", new SumFormula(), null,
                                                   null);
   }

   // the header reads summarize makes before getCellRegion: its own lookup of the range string
   private static int headerReadsBeforeTheRegion(HeaderTable table) {
      int failFrom = table.failFrom;
      table.failFrom = Integer.MAX_VALUE;
      table.headerReads = 0;
      assertTrue(Util.findColumn(table, RANGE) < 0, "the range is not a column name");
      int reads = table.headerReads;
      table.failFrom = failFrom;
      assertTrue(reads > 0);
      return reads;
   }

   private static final String RANGE = "[1,val]:[3,val]";

   /**
    * A table whose header row reads fail from the {@code failFrom}-th read on.
    */
   public static class HeaderTable extends DefaultTableLens {
      HeaderTable(Runnable failure) {
         super(new Object[][]{ { "name", "val" }, { "a", 1 }, { "b", 2 }, { "c", 3 } });
         this.failure = failure;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 0 && headerReads++ >= failFrom) {
            failure.run();
         }

         return super.getObject(r, c);
      }

      private final Runnable failure;
      int failFrom = Integer.MAX_VALUE;
      int headerReads;
   }
}
