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
package inetsoft.report.composition;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug #81431: {@code getLockOwner} only looked at the runtime sheets of its own node, so two users
 * editing the same asset on different nodes could both save. It now also asks the other nodes, and
 * the holder is the editable sheet that was opened first on any node.
 *
 * <p>The engine runs on a one-node mock cluster; the other nodes are replaced by a mocked
 * {@link SheetLockLookup} where a test needs a second node.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("core")
class WorksheetEngineLockOwnerTest {
   @Autowired ViewsheetService service;

   private WorksheetEngine engine;
   private SheetLockLookup original;
   private final List<String> ids = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      engine = (WorksheetEngine) service;
      original = (SheetLockLookup) lookupField().get(engine);
   }

   @AfterEach
   void tearDown() throws Exception {
      lookupField().set(engine, original);

      for(String id : ids) {
         engine.closeSheet(id, null);
      }

      ids.clear();
   }

   // ---- the lock of this node, unchanged ----

   @Test
   void theEditableSheetOfAnotherUserHoldsTheLock() {
      put("lock-3", "u1", true);

      assertEquals(name("u1"), engine.getLockOwner(entry("w")));
   }

   @Test
   void aSheetThatIsNotEditableDoesNotHoldTheLock() {
      put("lock-3", "u1", false);

      assertNull(engine.getLockOwner(entry("w")));
   }

   @Test
   void sheetsOfOtherAssetsAreIgnored() {
      put("lock-3", "u1", true, entry("other"));

      assertNull(engine.getLockOwner(entry("w")));
   }

   @Test
   void theSheetOpenedFirstHoldsTheLockWhateverTheOrderOfInsertion() {
      put("lock-9", "u2", true);
      put("lock-5", "u1", true);

      assertEquals(name("u1"), engine.getLockOwner(entry("w")));
   }

   @Test
   void aSheetWithoutUserHoldsTheLockForNobody() {
      put("lock-3", null, true);

      assertNull(engine.getLockOwner(entry("w")));
   }

   @Test
   void savingTheHomeBookmarkCountsSheetsThatAreNotEditableAndClearsTheMark() {
      put("lock-3", "u1", false);
      AssetEntry saving = entry("w");
      saving.setProperty("homeBookmarkSaved", "true");

      assertEquals(name("u1"), engine.getLockOwner(saving));
      assertNull(saving.getProperty("homeBookmarkSaved"), "the mark is for one check only");

      assertNull(engine.getLockOwner(entry("w")), "without the mark it is not editable");
   }

   @Test
   void theHomeBookmarkMarkIsClearedWhenNoSheetMatches() {
      AssetEntry saving = entry("w");
      saving.setProperty("homeBookmarkSaved", "true");

      assertNull(engine.getLockOwner(saving));
      assertNull(saving.getProperty("homeBookmarkSaved"));
   }

   // ---- the other nodes ----

   @Test
   void aSheetOfAnotherNodeThatWasOpenedFirstHoldsTheLock() throws Exception {
      put("lock-9", "u2", true);
      useRemote(new SheetLockHolder(name("u1"), "lock-5"));

      assertEquals(name("u1"), engine.getLockOwner(entry("w")));
   }

   @Test
   void theLocalSheetHoldsTheLockWhenItWasOpenedBeforeTheOneOfAnotherNode() throws Exception {
      put("lock-3", "u1", true);
      useRemote(new SheetLockHolder(name("u2"), "lock-5"));

      assertEquals(name("u1"), engine.getLockOwner(entry("w")));
   }

   @Test
   void aSheetOfAnotherNodeHoldsTheLockWhenThisNodeHasNone() throws Exception {
      useRemote(new SheetLockHolder(name("u1"), "lock-5"));

      assertEquals(name("u1"), engine.getLockOwner(entry("w")));
   }

   @Test
   void theLocalAnswerIsUsedWhenTheOtherNodesCouldNotBeAsked() throws Exception {
      put("lock-9", "u2", true);
      useRemote(null);

      assertEquals(name("u2"), engine.getLockOwner(entry("w")));
   }

   @Test
   void theOtherNodesAreAskedWhetherReadOnlySheetsCount() throws Exception {
      SheetLockLookup remote = useRemote(null);
      AssetEntry saving = entry("w");
      saving.setProperty("homeBookmarkSaved", "true");

      engine.getLockOwner(saving);
      verify(remote).findRemote(any(), eq(true));

      engine.getLockOwner(entry("w"));
      verify(remote).findRemote(any(), eq(false));
   }

   // ---- what another node runs for the lookup ----

   @Test
   void theTaskReportsTheLockHolderOfTheNodeItRunsOn() {
      put("lock-9", "u2", true);
      put("lock-5", "u1", true);

      SheetLockHolder holder = new SheetLockLookup.Task(entry("w"), false, "another-node").call();

      assertNotNull(holder);
      assertEquals(name("u1"), holder.getUser());
      assertEquals("lock-5", holder.getSheetId());
   }

   @Test
   void theTaskReportsNothingWhenNoSheetOfTheNodeHoldsTheLock() {
      put("lock-9", "u2", false);

      assertNull(new SheetLockLookup.Task(entry("w"), false, "another-node").call());
   }

   @Test
   void theTaskDoesNotRepeatTheScanOfTheCallerNode() {
      put("lock-9", "u2", true);

      SheetLockLookup.Task task = new SheetLockLookup.Task(
         entry("w"), false, engine.cluster.getLocalMember());

      assertNull(task.call(), "the caller scans its own sheets itself");
   }

   // ---- helpers ----

   private SheetLockLookup useRemote(SheetLockHolder holder) throws Exception {
      SheetLockLookup remote = mock(SheetLockLookup.class);
      when(remote.findRemote(any(), anyBoolean())).thenReturn(holder);
      lookupField().set(engine, remote);
      return remote;
   }

   private static Field lookupField() throws Exception {
      Field field = WorksheetEngine.class.getDeclaredField("lockLookup");
      field.setAccessible(true);
      return field;
   }

   private void put(String id, String user, boolean editable) {
      put(id, user, editable, entry("w"));
   }

   private void put(String id, String user, boolean editable, AssetEntry entry) {
      Principal principal = user == null ? null : principal(user);
      RuntimeWorksheet rws = new RuntimeWorksheet(entry, new Worksheet(), principal);
      rws.setEditable(editable);
      engine.putRuntimeSheet(id, rws);
      ids.add(id);
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null);
   }

   private static Principal principal(String user) {
      return new XPrincipal(new IdentityID(user, "host-org"));
   }

   private static String name(String user) {
      return principal(user).getName();
   }
}
