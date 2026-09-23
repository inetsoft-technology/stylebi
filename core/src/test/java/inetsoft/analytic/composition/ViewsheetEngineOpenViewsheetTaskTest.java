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
package inetsoft.analytic.composition;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.GroupedThread;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #76920's fix #2: {@code ViewsheetEngine.OpenViewsheetTask
 * .doOpenViewsheet()} used to capture the "old" principal via {@code ThreadContext.getPrincipal()}
 * (a plain {@code ThreadLocal}) but restore it via {@code ThreadContext.setContextPrincipal()}
 * (which is {@code GroupedThread}-aware). On a {@code GroupedThread} -- every STOMP-driven
 * viewsheet open runs on one, see {@code WebSocketConfig.eventThreadFactory()} -- the real
 * per-message principal lives on the {@code GroupedThread}'s own field, not the static
 * {@code ThreadLocal}, so the captured "old" value was (almost always) {@code null}, and the
 * {@code finally} block then nulled out the {@code GroupedThread}'s real principal for the
 * remainder of the message.
 *
 * <p>{@code doOpenViewsheet()} is private and needs a fully bootstrapped
 * {@code ViewsheetEngine}/{@code AssetRepository} to actually open a sheet -- disproportionate
 * scaffolding for what this test needs to demonstrate. Instead it invokes the private method
 * directly via reflection with {@code engine == null}, which makes {@code engine.openSheet(...)}
 * throw a {@code NullPointerException} immediately -- deterministically forcing the
 * {@code try/finally}'s exception path -- and exercises exactly the principal capture/restore
 * logic this fix touches, independent of what real work {@code openSheet()} would otherwise do.
 * (The pre-fix bug affects the success path just as much as the exception path -- the restored
 * value doesn't depend on how the try block exited -- so forcing an exception this way is not a
 * narrower test than exercising the success path would be.)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetEngineOpenViewsheetTaskTest {

   @Test
   void doOpenViewsheet_restoresTheGroupedThreadsRealPrincipal() throws Exception {
      Principal sessionPrincipal = new XPrincipal(new IdentityID("sessionUser", "host-org"));
      Principal openAsUser = new XPrincipal(new IdentityID("openAsUser", "host-org"));

      Class<?> taskClass =
         Class.forName("inetsoft.analytic.composition.ViewsheetEngine$OpenViewsheetTask");
      Method doOpenViewsheet = taskClass.getDeclaredMethod(
         "doOpenViewsheet", ViewsheetEngine.class, AssetEntry.class, Principal.class, String.class);
      doOpenViewsheet.setAccessible(true);

      AtomicReference<Throwable> thrown = new AtomicReference<>();
      AtomicReference<Principal> afterCall = new AtomicReference<>();

      // GroupedThread's own principal field is the real, per-message principal -- what
      // MessageScopeInterceptor sets for a STOMP message. The static ThreadContext ThreadLocal is
      // left unset, matching normal production state.
      GroupedThread thread = new GroupedThread(() -> {
         try {
            doOpenViewsheet.invoke(null, (ViewsheetEngine) null, null, openAsUser, "id");
         }
         catch(InvocationTargetException ex) {
            thrown.set(ex.getCause());
         }
         catch(Exception ex) {
            thrown.set(ex);
         }
         finally {
            afterCall.set(ThreadContext.getContextPrincipal());
         }
      }, sessionPrincipal);

      thread.start();
      thread.join(20_000);

      assertFalse(thread.isAlive(), "test thread did not finish in time");
      assertNotNull(thrown.get(), "engine == null must make openSheet(...) throw immediately");
      assertInstanceOf(NullPointerException.class, thrown.get());
      assertSame(sessionPrincipal, afterCall.get(),
         "doOpenViewsheet() must restore the GroupedThread's real prior principal (captured via " +
         "getContextPrincipal()), not null -- the bug: it used to capture the \"old\" principal " +
         "via the plain-ThreadLocal getPrincipal(), which is null on a GroupedThread, and then " +
         "restore that null, wiping out the thread's real principal for the rest of the message");
   }
}
