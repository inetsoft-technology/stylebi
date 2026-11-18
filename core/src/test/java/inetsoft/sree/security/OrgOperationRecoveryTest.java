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
package inetsoft.sree.security;

/*
 * Bug #79875: a clone or id change of an organization that is interrupted by the death of its
 * node is recorded in a journal, and finished (id change) or undone (clone) by the next create or
 * edit of an organization.
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrgOperationJournal.Entry;
import inetsoft.sree.security.OrgOperationJournal.State;
import inetsoft.sree.security.OrgOperationJournal.Type;
import inetsoft.storage.KeyValueEngine;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OrgOperationRecoveryTest {
   private static final String DEAD_NODE = "node-that-left";

   private final Map<String, Entry> map = new LinkedHashMap<>();
   private OrgOperationJournal journal;
   private final List<String> calls = new ArrayList<>();

   @BeforeEach
   void setUp() {
      journal = new OrgOperationJournal(new OrgOperationJournal.Store() {
         @Override
         public Entry get(String key) {
            return map.get(key);
         }

         @Override
         public void put(String key, Entry entry) {
            map.put(key, entry);
         }

         @Override
         public void remove(String key) {
            map.remove(key);
         }

         @Override
         public Collection<Entry> values() {
            return new ArrayList<>(map.values());
         }
      });
   }

   private Entry entry(Type type, String from, String to, String node, State state) {
      Entry e = new Entry("op-" + from + "-" + to, type, from, to, node, 1L, state, "{}",
                          List.of("portal/" + to + "/x.xml"), null);
      map.put(e.getOpId(), e);
      return e;
   }

   private OrgOperationRecovery.Actions actions() {
      return new OrgOperationRecovery.Actions() {
         @Override
         public void rollForwardRename(Entry entry) {
            calls.add("forward " + entry.getFromId() + ">" + entry.getToId());
         }

         @Override
         public void rollBackClone(Entry entry) {
            calls.add("back " + entry.getToId());
         }
      };
   }

   @Test
   void renameOfADeadNodeIsRolledForwardAndRemoved() {
      entry(Type.RENAME, "b79875a", "b79875b", DEAD_NODE, State.RUNNING);

      int count = OrgOperationRecovery.recover(journal, actions(), OrgOperationRecovery::isAbandoned);

      assertEquals(1, count);
      assertEquals(List.of("forward b79875a>b79875b"), calls);
      assertTrue(journal.list().isEmpty());
   }

   @Test
   void cloneOfADeadNodeIsRolledBack() {
      entry(Type.CLONE, "b79875c", "b79875d", DEAD_NODE, State.RUNNING);

      OrgOperationRecovery.recover(journal, actions(), OrgOperationRecovery::isAbandoned);

      assertEquals(List.of("back b79875d"), calls);
      assertTrue(journal.list().isEmpty());
   }

   @Test
   void operationOfALiveNodeIsLeftAlone() {
      String local = Cluster.getInstance().getLocalNodeId();
      entry(Type.RENAME, "b79875e", "b79875f", local, State.RUNNING);

      int count = OrgOperationRecovery.recover(journal, actions(), OrgOperationRecovery::isAbandoned);

      assertEquals(0, count);
      assertTrue(calls.isEmpty());
      assertEquals(1, journal.list().size());
   }

   @Test
   void failedOperationIsRecoveredWhileItsNodeRuns() {
      String local = Cluster.getInstance().getLocalNodeId();
      entry(Type.RENAME, "b79875g", "b79875h", local, State.FAILED);

      assertEquals(1, OrgOperationRecovery.recover(journal, actions(),
                                                   OrgOperationRecovery::isAbandoned));
      assertEquals(1, calls.size());
   }

   @Test
   void liveChangeOfTheOrganizationHoldsTheRecoveryBack() {
      entry(Type.RENAME, "b79875i", "b79875j", DEAD_NODE, State.RUNNING);
      String opId = OrgIdChangeGuard.begin("b79875i", "b79875j");

      try {
         assertEquals(0, OrgOperationRecovery.recover(journal, actions(),
                                                      OrgOperationRecovery::isAbandoned));
         assertTrue(calls.isEmpty());
      }
      finally {
         OrgIdChangeGuard.end(opId, "b79875i", false);
      }

      assertEquals(1, OrgOperationRecovery.recover(journal, actions(),
                                                   OrgOperationRecovery::isAbandoned));
   }

   @Test
   void failedRecoveryKeepsTheRecordAndIsRetried() {
      Entry e = entry(Type.RENAME, "b79875k", "b79875l", DEAD_NODE, State.RUNNING);
      boolean[] fail = { true };
      OrgOperationRecovery.Actions failing = new OrgOperationRecovery.Actions() {
         @Override
         public void rollForwardRename(Entry entry) throws Exception {
            if(fail[0]) {
               throw new Exception("step failed");
            }

            calls.add("forward");
         }

         @Override
         public void rollBackClone(Entry entry) {
         }
      };

      assertEquals(0, OrgOperationRecovery.recover(journal, failing,
                                                   OrgOperationRecovery::isAbandoned));
      assertEquals(State.FAILED, journal.get(e.getOpId()).getState());

      fail[0] = false;
      assertEquals(1, OrgOperationRecovery.recover(journal, failing,
                                                   OrgOperationRecovery::isAbandoned));
      assertEquals(List.of("forward"), calls);
      assertTrue(journal.list().isEmpty());
   }

   @Test
   void emptyJournalDoesNothing() {
      assertEquals(0, OrgOperationRecovery.recover(journal, actions(), e -> true));
   }

   @Test
   void beginRefusesAnOrganizationThatIsInAnOperation() {
      Entry e = journal.begin(Type.RENAME, "b79875m", "b79875n", "{}", List.of(), null);
      assertEquals(State.RUNNING, e.getState());
      assertThrows(IllegalStateException.class,
                   () -> journal.begin(Type.CLONE, "other", "B79875N", null, List.of(), null));
      journal.markFailed(e.getOpId());
      assertEquals(State.FAILED, journal.get(e.getOpId()).getState());
      journal.complete(e.getOpId());
      assertNull(journal.get(e.getOpId()));
      // free again
      journal.complete(journal.begin(Type.CLONE, "other", "b79875n", null, List.of(), null).getOpId());
   }

   @Test
   void abandonedRecordNeverBlocksARetry() {
      // the stale record of a clone that recovery could not undo (dead owner)
      entry(Type.CLONE, "b79875q", "b79875r", DEAD_NODE, State.RUNNING);

      Entry e = journal.begin(Type.CLONE, "b79875q", "b79875r", null, List.of(), null);

      assertEquals(1, journal.list().size());
      assertEquals(e.getOpId(), journal.list().get(0).getOpId());
      assertNotEquals(DEAD_NODE, e.getNode());
   }

   @Test
   void failedRecordOfARunningNodeNeverBlocksARetry() {
      String local = Cluster.getInstance().getLocalNodeId();
      entry(Type.RENAME, "b79875s", "b79875t", local, State.FAILED);

      assertDoesNotThrow(
         () -> journal.begin(Type.RENAME, "b79875s", "b79875t", "{}", List.of(), null));
   }

   // the lookup that the recovery runs, on a security provider like the enterprise one: not an
   // AbstractSecurityProvider, its authentication provider is a chain
   @Test
   void editableProviderIsFoundThroughTheAuthenticationChain() {
      EditableAuthenticationProvider readOnly = mock(EditableAuthenticationProvider.class);
      EditableAuthenticationProvider first = mock(EditableAuthenticationProvider.class);
      EditableAuthenticationProvider second = mock(EditableAuthenticationProvider.class);
      when(readOnly.getProviderName()).thenReturn("readonly");
      when(first.getProviderName()).thenReturn("first");
      when(second.getProviderName()).thenReturn("second");
      AuthenticationProvider plain = mock(AuthenticationProvider.class);
      when(plain.getProviderName()).thenReturn("plain");

      AuthenticationChain chain = new AuthenticationChain() {
         @Override
         void initialize() {
         }
      };
      chain.getProviderList().addAll(List.of(plain, first, second));
      SecurityProvider security = mock(SecurityProvider.class);
      when(security.getAuthenticationProvider()).thenReturn(chain);

      // the helper that the first version of the fix used gives nothing for such a provider
      assertNull(inetsoft.sree.internal.SUtil.getEditableAuthenticationProvider(security));
      assertSame(first, OrgOperationRecovery.findEditableProvider(security, null));
      assertSame(first, OrgOperationRecovery.findEditableProvider(security, "unknown"));
      assertSame(second, OrgOperationRecovery.findEditableProvider(security, "second"));

      // a single provider, not a chain
      SecurityProvider single = mock(SecurityProvider.class);
      when(single.getAuthenticationProvider()).thenReturn(readOnly);
      assertSame(readOnly, OrgOperationRecovery.findEditableProvider(single, null));

      // nothing editable
      AuthenticationChain none = new AuthenticationChain() {
         @Override
         void initialize() {
         }
      };
      none.getProviderList().add(plain);
      when(security.getAuthenticationProvider()).thenReturn(none);
      assertNull(OrgOperationRecovery.findEditableProvider(security, null));
   }

   @Test
   void recordIsStorable() throws Exception {
      Entry e = new Entry("op", Type.CLONE, "a", "b", "node", 5L, State.FAILED, "p",
                          List.of("portal/b/x"), "prov");
      var mapper = KeyValueEngine.createObjectMapper();
      Entry back = mapper.readValue(mapper.writeValueAsString(e), Entry.class);

      assertEquals("op", back.getOpId());
      assertEquals(Type.CLONE, back.getType());
      assertEquals(State.FAILED, back.getState());
      assertEquals(List.of("portal/b/x"), back.getTargets());
      assertEquals("node", back.getNode());
      assertEquals("prov", back.getProviderName());
   }

   @Test
   void resumeModelIsStorable() throws Exception {
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name("n").oldName("o").id("b79875z").status(true).locale("").build();
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      EditOrganizationPaneModel back =
         mapper.readValue(mapper.writeValueAsString(model), EditOrganizationPaneModel.class);

      assertEquals("b79875z", back.id());
      assertEquals("n", back.name());
   }

   @Test
   void preExistingFilesUnderTheNewIdStillFailTheCheckAndNoRecordIsWritten() throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      String dir = "portal/b79875pre";
      space.withOutputStream(dir, "dashboard-registry.xml", out -> out.write(1));

      try {
         assertThrows(MessageException.class,
                      () -> OrganizationIdRules.checkRename("b79875old", "b79875pre"));
         assertTrue(journal.list().isEmpty());
      }
      finally {
         space.delete(null, dir);
      }
   }
}
