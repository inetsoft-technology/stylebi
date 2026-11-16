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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.TextSizeLimitTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.QueryManager;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81374: closing a worksheet tab did not cancel its in-flight queries. Every lens cached
 * by AssetQuerySandbox is wrapped in a TextSizeLimitTableLens, so cancelTableLens() never saw a
 * CancellableTableLens, and closing never called QueryManager.cancel() as the Cancel button does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CloseWorksheetCancelTest {
   @Test
   void cancelTableLensCancelsALensUnderTheSizeLimitWrapper() throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      Cancellable inner = new Cancellable();
      cache(box, new TextSizeLimitTableLens(inner, 100));

      box.cancelTableLens();

      assertEquals(1, inner.cancels.get(), "the wrapped lens must be cancelled");
   }

   @Test
   void cancelTableLensStillCancelsAnUnwrappedLens() throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      Cancellable inner = new Cancellable();
      cache(box, inner);

      box.cancelTableLens();

      assertEquals(1, inner.cancels.get());
   }

   @Test
   void closeWorksheetCancelsPendingQueriesOfTheQueryManager() throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      Cancellable pending = new Cancellable();
      box.setQueryManager(new QueryManager());
      box.getQueryManager().addPending(pending);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      ViewsheetService engine = mock(ViewsheetService.class);
      Principal user = mock(Principal.class);
      when(engine.getWorksheet("ws1", user)).thenReturn(rws);
      AbstractAssetEngine rep = mock(AbstractAssetEngine.class);

      CloseWorksheetService service = new CloseWorksheetService(
         engine, mock(RuntimeViewsheetManager.class), mock(DataSourceRegistry.class));

      try(MockedStatic<VSEventUtil> util = mockStatic(VSEventUtil.class);
          MockedStatic<AssetUtil> assets = mockStatic(AssetUtil.class))
      {
         assets.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(rep);
         service.closeWorksheet("ws1", user);
      }

      assertEquals(1, pending.cancels.get(), "close must cancel the pending query");
   }

   @SuppressWarnings("unchecked")
   private static void cache(AssetQuerySandbox box, TableLens lens) throws Exception {
      Field f = AssetQuerySandbox.class.getDeclaredField("tmap");
      f.setAccessible(true);
      Map<Object, TableLens> tmap = (Map<Object, TableLens>) f.get(box);
      tmap.put("t", lens); // erased key type; cancelTableLens only reads the values
   }

   public static final class Cancellable extends DefaultTableLens implements CancellableTableLens {
      public Cancellable() {
         super(1, 1);
      }

      @Override
      public void cancel() {
         cancels.incrementAndGet();
      }

      @Override
      public boolean isCancelled() {
         return cancels.get() > 0;
      }

      final AtomicInteger cancels = new AtomicInteger();
   }
}
