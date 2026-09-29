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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77155: a chart flyover request must cancel the superseded request's query before
 * waiting for the target's flyover lock, and a request superseded while waiting must not run.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ViewsheetSandboxFlyoverRequestTest {
   @BeforeEach
   void setUp() {
      Viewsheet vs = new Viewsheet();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "Flyover77155", null);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   @Test
   void supersededRequestDoesNotRun() {
      ViewsheetSandbox.FlyoverRequest first = box.startFlyoverRequest(TIP, CHART, "a");
      ViewsheetSandbox.FlyoverRequest second = box.startFlyoverRequest(TIP, CHART, "b");

      assertFalse(box.beginFlyoverRequest(TIP, first));
      assertTrue(box.beginFlyoverRequest(TIP, second));
   }

   @Test
   void changedRequestCancelsRunningQuery() {
      ViewsheetSandbox.FlyoverRequest first = box.startFlyoverRequest(TIP, CHART, "a");
      assertTrue(box.beginFlyoverRequest(TIP, first));
      Statement stmt = new Statement();
      box.getQueryManager(TIP).addPending(stmt);

      box.startFlyoverRequest(TIP, CHART, "b");

      assertTrue(stmt.cancelled);
      assertTrue(box.clearFlyoverCancelled(TIP));
      assertFalse(box.clearFlyoverCancelled(TIP));
   }

   @Test
   void unchangedRequestDoesNotCancelRunningQuery() {
      ViewsheetSandbox.FlyoverRequest first = box.startFlyoverRequest(TIP, CHART, "a");
      assertTrue(box.beginFlyoverRequest(TIP, first));
      Statement stmt = new Statement();
      box.getQueryManager(TIP).addPending(stmt);

      box.startFlyoverRequest(TIP, CHART, "a");

      assertFalse(stmt.cancelled);
      assertFalse(box.clearFlyoverCancelled(TIP));
   }

   @Test
   void changedRequestCancelsRunningQueryBehindQueuedRequest() {
      ViewsheetSandbox.FlyoverRequest first = box.startFlyoverRequest(TIP, CHART, "a");
      assertTrue(box.beginFlyoverRequest(TIP, first));
      Statement stmt = new Statement();
      box.getQueryManager(TIP).addPending(stmt);

      // an identical request waiting for the lock must not hide the running one
      box.startFlyoverRequest(TIP, CHART, "a");
      assertFalse(stmt.cancelled);
      box.startFlyoverRequest(TIP, CHART, "b");

      assertTrue(stmt.cancelled);
      assertTrue(box.clearFlyoverCancelled(TIP));
   }

   @Test
   void finishedRequestIsNotCancelled() {
      ViewsheetSandbox.FlyoverRequest first = box.startFlyoverRequest(TIP, CHART, "a");
      assertTrue(box.beginFlyoverRequest(TIP, first));
      box.endFlyoverRequest(TIP, first);
      // e.g. a query of a table flyover on the same target
      Statement stmt = new Statement();
      box.getQueryManager(TIP).addPending(stmt);

      box.startFlyoverRequest(TIP, CHART, "b");

      assertFalse(stmt.cancelled);
      assertFalse(box.clearFlyoverCancelled(TIP));
   }

   @Test
   void waitingRequestIsNotCancelled() {
      box.startFlyoverRequest(TIP, CHART, "a");
      // e.g. a query of a table flyover holding the target's flyover lock
      Statement stmt = new Statement();
      box.getQueryManager(TIP).addPending(stmt);

      box.startFlyoverRequest(TIP, CHART, "b");

      assertFalse(stmt.cancelled);
      assertFalse(box.clearFlyoverCancelled(TIP));
   }

   public static class Statement {
      public void cancel() {
         cancelled = true;
      }

      private volatile boolean cancelled;
   }

   private static final String TIP = "Crosstab1";
   private static final String CHART = "Chart1";
   private ViewsheetSandbox box;
}
