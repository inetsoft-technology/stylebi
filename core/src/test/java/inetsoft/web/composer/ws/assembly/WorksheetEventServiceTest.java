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
package inetsoft.web.composer.ws.assembly;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Whether opening a worksheet creates a runtime or attaches to one that already exists, and
 * whether it links the new/attached worksheet's sandbox back to an originating viewsheet.
 *
 * <p>An agent tool ({@code open_base_worksheet}) opens a worksheet runtime server-side and tells
 * the browser to attach to it. If the browser opens its own runtime instead, both ends report
 * success and diverge silently: the agent edits one worksheet, the user watches another, and
 * nothing surfaces until a save overwrites one with the other.
 *
 * <p>{@code existingRuntimeId} (the attach target) and {@code vsId} (the viewsheet to link the
 * sandbox to) are independent parameters -- bug #76409 collapsed them into the same slot when
 * #76408 added {@code vsId} without adding its own parameter, so a supplied {@code runtimeId} was
 * silently read as a {@code vsId} and the attach branch never fired. Every case below exercises
 * both parameters together to guard against that collision recurring.
 */
@Tag("core")
class WorksheetEventServiceTest {
   /**
    * The attach. Creating a second runtime here is the whole defect, so this asserts the negative
    * ({@code engine.openWorksheet} never called) as well as the positive -- delegating with the
    * right id while ALSO opening a stray runtime would satisfy the positive alone.
    */
   @Test
   void attachesToTheSuppliedRuntimeInsteadOfOpeningASecondOne() throws Exception {
      Fixture f = new Fixture();

      f.service.openWorksheet(f.user, f.entry, false, false, "ws-server-1", null, f.dispatcher);

      verify(f.engine, never()).openWorksheet(any(AssetEntry.class), any(Principal.class));
      verify(f.proxy).openWorksheet(eq("ws-server-1"), eq(f.user), eq(f.entry), eq(false),
                                    eq(false), eq((String) null), eq(f.dispatcher));
   }

   /**
    * The normal path, which every Composer user takes. The attach branch must not capture it:
    * with no runtime supplied the server still opens one of its own.
    */
   @Test
   void opensANewRuntimeWhenNoneIsSupplied() throws Exception {
      Fixture f = new Fixture();
      when(f.engine.openWorksheet(f.entry, f.user)).thenReturn("ws-fresh-1");

      f.service.openWorksheet(f.user, f.entry, false, false, null, null, f.dispatcher);

      verify(f.engine).openWorksheet(f.entry, f.user);
      verify(f.proxy).openWorksheet(eq("ws-fresh-1"), eq(f.user), eq(f.entry), eq(false),
                                    eq(false), eq((String) null), eq(f.dispatcher));
   }

   /**
    * Regression test for bug #76409: attaching to a server-opened runtime (the
    * {@code open_base_worksheet} agent flow) and linking the new worksheet's sandbox to an
    * originating viewsheet are independent and must both take effect when both are supplied --
    * neither parameter may shadow or overwrite the other.
    */
   @Test
   void attachesToSuppliedRuntimeAndLinksVsIdSimultaneously() throws Exception {
      Fixture f = new Fixture();

      f.service.openWorksheet(f.user, f.entry, false, false, "ws-server-1", "vs-origin-1",
                              f.dispatcher);

      verify(f.engine, never()).openWorksheet(any(AssetEntry.class), any(Principal.class));
      verify(f.proxy).openWorksheet(eq("ws-server-1"), eq(f.user), eq(f.entry), eq(false),
                                    eq(false), eq("vs-origin-1"), eq(f.dispatcher));
   }

   @SuppressWarnings("unchecked")
   private static final class Fixture {
      final ViewsheetService engine = mock(ViewsheetService.class);
      final WorksheetEventServiceProxy proxy = mock(WorksheetEventServiceProxy.class);
      final ObjectProvider<WorksheetEventServiceProxy> provider = mock(ObjectProvider.class);
      final AssetEntry entry = mock(AssetEntry.class);
      final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      final Principal user = () -> "alice~;~host-org";
      final WorksheetEventService service;

      Fixture() {
         when(provider.getIfAvailable()).thenReturn(proxy);
         service = new WorksheetEventService(engine, provider);
      }
   }
}
