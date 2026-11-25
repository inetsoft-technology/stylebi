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
package inetsoft.mv;

import inetsoft.mv.data.DefaultTableBlock;
import inetsoft.mv.data.SubMV;
import inetsoft.mv.data.SubMVQuery;
import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.XBlockSystem;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #82324: when the sub-MV query of a block threw, {@link SubMVTask#run} swallowed the
 * exception (unless the default organization was globally visible) and returned a result
 * with null data, which the reducer then turned into NPEs and a misleading
 * "Reduce task is not completed" error, or into silently missing rows. The failure must
 * instead propagate, so the map task pool reports it through the job's failure channel
 * with the original cause.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SubMVTaskQueryFailureTest {
   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void aFailingSubQueryIsPropagatedWhenTheDefaultOrgIsNotGloballyVisible() throws Exception {
      DefaultTableBlock.ColumnNotFoundException original =
         new DefaultTableBlock.ColumnNotFoundException("Column[Column [234]] not found in []");
      SubMVTask task = newTask(original, null);

      try(MockedStatic<SubMV> subMV = mockStatic(SubMV.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS))
      {
         subMV.when(() -> SubMV.get(any())).thenReturn(mock(SubMV.class));
         sutil.when(SUtil::isDefaultVSGloballyVisible).thenReturn(false);

         Exception thrown = assertThrows(Exception.class, () -> task.run(blockSystem()),
            "a failing sub-MV query must not be returned as a successful result");
         assertTrue(causeChainContains(thrown, original),
                    "the original exception must be in the cause chain: " + thrown);
      }
   }

   @Test
   void aFailingRetryAsTheDefaultOrgKeepsTheFirstException() throws Exception {
      DefaultTableBlock.ColumnNotFoundException first =
         new DefaultTableBlock.ColumnNotFoundException("Column[A] not found in []");
      DefaultTableBlock.ColumnNotFoundException retry =
         new DefaultTableBlock.ColumnNotFoundException("Column[B] not found in []");
      SubMVTask task = newTask(first, retry);

      XPrincipal user = new XPrincipal(new IdentityID("user1", getOrgId()));
      user.setOrgId(getOrgId());
      ThreadContext.setContextPrincipal(user);

      try(MockedStatic<SubMV> subMV = mockStatic(SubMV.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS))
      {
         subMV.when(() -> SubMV.get(any())).thenReturn(mock(SubMV.class));
         sutil.when(SUtil::isDefaultVSGloballyVisible).thenReturn(true);

         Exception thrown = assertThrows(Exception.class, () -> task.run(blockSystem()));
         assertTrue(causeChainContains(thrown, retry),
                    "the retry's exception must be in the cause chain: " + thrown);
         assertTrue(Arrays.asList(retry.getSuppressed()).contains(first),
                    "the first exception must be kept as suppressed on the retry's");
      }

      assertSame(user, ThreadContext.getContextPrincipal(),
                 "the retry must restore the caller's principal");
   }

   private SubMVTask newTask(RuntimeException first, RuntimeException second) throws Exception {
      SubMVQuery query = mock(SubMVQuery.class);

      if(second == null) {
         when(query.execute(any())).thenThrow(first);
      }
      else {
         when(query.execute(any())).thenThrow(first).thenThrow(second);
      }

      SubMVTask task = new SubMVTask();
      task.setXBlock("mvfile_" + getOrgId() + "-0");
      task.setQuery(query);
      return task;
   }

   private static XBlockSystem blockSystem() {
      XBlockSystem sys = mock(XBlockSystem.class);
      BlockFile file = mock(BlockFile.class);
      when(sys.getFile(anyString())).thenReturn(file);
      return sys;
   }

   private static String getOrgId() {
      String orgId = Organization.getDefaultOrganizationID();
      String[] orgs = SecurityEngine.getSecurity().getSecurityProvider().getOrganizationIDs();
      assertTrue(Arrays.asList(orgs).contains(orgId),
                 "the test provider must list " + orgId + ": " + Arrays.toString(orgs));
      return orgId;
   }

   private static boolean causeChainContains(Throwable thrown, Throwable expected) {
      for(Throwable t = thrown; t != null; t = t.getCause()) {
         if(t == expected) {
            return true;
         }
      }

      return false;
   }

   private Principal savedPrincipal;
}
