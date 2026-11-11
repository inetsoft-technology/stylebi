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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug 81017: the paired-runtime keep-alive.
 *
 * [live local]     a runtime whose heartbeat is stale (>180 s) is no longer timed out afterwards
 * [idle clock]     the call does not reset the idle clock, so the idle limit still applies
 * [missing]        a runtime that is not local answers SESSION_EXPIRED and is never loaded via
 *                  the cache-reloading getSheetDirect
 * [session]        unknown / foreign session token answers SESSION_EXPIRED, runtime untouched
 * [no slide]       the keep-alive does not extend the pairing session's TTL
 * [type]           a runtime of the wrong type answers SESSION_EXPIRED
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SheetKeepAliveServiceTest {
   private static final Principal ALICE = TestPrincipals.user("alice", "org");
   private static final String OWNER = PairingUtil.identityKey(ALICE);

   private final SheetDirectAccessor ws = mock(SheetDirectAccessor.class);
   private final SheetDirectAccessor vs = mock(SheetDirectAccessor.class);
   private final SheetSessionService sessions = new SheetSessionService();
   private final SheetKeepAliveService service =
      new SheetKeepAliveService(sessions, new SheetRuntimeAccess(ws, vs));

   private static RuntimeWorksheet realWorksheet() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws81017", null);
      return new RuntimeWorksheet(entry, new Worksheet(),
                                  new XPrincipal(new IdentityID("alice", "org")));
   }

   private static void setField(RuntimeSheet rs, String name, long value) throws Exception {
      Field f = RuntimeSheet.class.getDeclaredField(name);
      f.setAccessible(true);
      f.setLong(rs, value);
   }

   @Test
   void staleHeartbeatIsRefreshedAndNoLongerTimedOut() throws Exception {
      RuntimeWorksheet rws = realWorksheet();
      setField(rws, "heartbeat", System.currentTimeMillis() - 181_000L);
      assertTrue(rws.isTimeout(), "precondition: 181 s without a heartbeat is a timeout");
      when(ws.getSheetLocal("rt-1")).thenReturn(rws);
      JoinSession s = sessions.open("rt-1", OWNER, SheetType.WORKSHEET, null, null, null);

      service.keepAlive(s.sessionToken(), ALICE);

      assertFalse(rws.isTimeout());
      verify(ws, never()).getSheetDirect(anyString());
   }

   @Test
   void doesNotResetTheIdleClock() throws Exception {
      RuntimeWorksheet rws = realWorksheet();
      long longAgo = System.currentTimeMillis() - 3_600_000L;
      setField(rws, "accessed", longAgo);
      when(ws.getSheetLocal("rt-1")).thenReturn(rws);
      JoinSession s = sessions.open("rt-1", OWNER, SheetType.WORKSHEET, null, null, null);

      service.keepAlive(s.sessionToken(), ALICE);

      assertEquals(longAgo, rws.getLastAccessed());
      assertTrue(rws.isTimeout(), "idle limit must still reclaim an unused runtime");
   }

   @Test
   void missingRuntimeIsNotFoundAndNeverReloaded() {
      when(vs.getSheetLocal("rt-2")).thenReturn(null);
      JoinSession s = sessions.open("rt-2", OWNER, SheetType.VIEWSHEET, null, null, null);

      PairingException e = assertThrows(PairingException.class,
                                        () -> service.keepAlive(s.sessionToken(), ALICE));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, e.getKind());
      assertTrue(e.getMessage().contains("Viewsheet runtime not found or expired"));
      verify(vs, never()).getSheetDirect(anyString());
      verify(vs).getSheetLocal("rt-2");
   }

   @Test
   void wrongRuntimeTypeIsNotFound() {
      when(vs.getSheetLocal("rt-3")).thenReturn(mock(RuntimeWorksheet.class));
      JoinSession s = sessions.open("rt-3", OWNER, SheetType.VIEWSHEET, null, null, null);

      PairingException e = assertThrows(PairingException.class,
                                        () -> service.keepAlive(s.sessionToken(), ALICE));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, e.getKind());
   }

   @Test
   void unknownOrForeignSessionIsNotFoundAndRuntimeUntouched() {
      JoinSession s = sessions.open("rt-4", OWNER, SheetType.WORKSHEET, null, null, null);
      Principal bob = TestPrincipals.user("bob", "org");

      assertThrows(PairingException.class, () -> service.keepAlive("nope", ALICE));
      assertThrows(PairingException.class, () -> service.keepAlive(s.sessionToken(), bob));
      verifyNoInteractions(ws, vs);
   }

   @Test
   void doesNotSlideThePairingSessionTtl() throws Exception {
      AtomicLong now = new AtomicLong(1_000_000L);
      SheetSessionService timed = new SheetSessionService(now::get);
      SheetKeepAliveService svc = new SheetKeepAliveService(timed, new SheetRuntimeAccess(ws, vs));
      when(ws.getSheetLocal("rt-5")).thenReturn(mock(RuntimeWorksheet.class));
      JoinSession s = timed.open("rt-5", OWNER, SheetType.WORKSHEET, null, null, null);

      now.addAndGet(SheetSessionService.TTL_MILLIS - 60_000L);
      svc.keepAlive(s.sessionToken(), ALICE);
      now.addAndGet(120_000L);

      assertNull(timed.resolve(s.sessionToken(), OWNER),
                 "a keep-alive must not extend the pairing session's lifetime");
   }

   @Test
   void expiredPairingSessionIsNotFoundAndRuntimeUntouched() {
      AtomicLong now = new AtomicLong(1_000_000L);
      SheetSessionService timed = new SheetSessionService(now::get);
      SheetKeepAliveService svc = new SheetKeepAliveService(timed, new SheetRuntimeAccess(ws, vs));
      JoinSession s = timed.open("rt-6", OWNER, SheetType.WORKSHEET, null, null, null);

      now.addAndGet(SheetSessionService.TTL_MILLIS + 1);

      PairingException e = assertThrows(PairingException.class,
                                        () -> svc.keepAlive(s.sessionToken(), ALICE));
      assertEquals(PairingException.Kind.SESSION_EXPIRED, e.getKind());
      verifyNoInteractions(ws, vs);
   }
}
