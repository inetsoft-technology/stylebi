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

package inetsoft.sree.internal;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Tier: [unit] - a failed cycle store must reach the caller instead of being logged and
 * swallowed (Bug #78775). Uses a real DataCycleManager over a failing IndexedStorage.
 */
@Tag("core")
class DataCycleManagerStorageFailureTest {
   private static final String ORG = "host-org";
   private static final String CYCLE = "Cycle1";

   private IndexedStorage storage;
   private DataCycleManager manager;

   @BeforeEach
   void setUp() throws Exception {
      storage = mock(IndexedStorage.class);
      manager = new DataCycleManager(
         mock(ScheduleManager.class), storage, null, null, null, null, null);
      doThrow(new IOException("disk full")).when(storage).putXMLSerializable(anyString(), any());
   }

   @Test
   void setConditionsListPropagatesStorageFailure() {
      IOException e = assertThrows(
         IOException.class,
         () -> manager.setConditions(CYCLE, ORG, List.of(TimeCondition.at(1, 0, 0))));
      assertEquals("disk full", e.getMessage());
   }

   @Test
   void setConditionsVectorPropagatesStorageFailure() {
      Vector<inetsoft.sree.schedule.ScheduleCondition> conds = new Vector<>();
      conds.add(TimeCondition.at(1, 0, 0));
      assertThrows(IOException.class, () -> manager.setConditions(CYCLE, ORG, conds));
   }

   @Test
   void addConditionPropagatesStorageFailure() {
      assertThrows(IOException.class,
                   () -> manager.addCondition(CYCLE, ORG, TimeCondition.at(1, 0, 0)));
   }

   @Test
   void setCycleInfoPropagatesStorageFailure() {
      assertThrows(IOException.class,
                   () -> manager.setCycleInfo(CYCLE, ORG,
                                              new DataCycleManager.CycleInfo(CYCLE, ORG)));
   }

   @Test
   void setEnablePropagatesStorageFailure() throws Exception {
      when(storage.contains(anyString(), eq(ORG))).thenReturn(true);
      when(storage.getXMLSerializable(anyString(), any(), eq(ORG)))
         .thenReturn(new DataCycleManager.DataCycleAsset());

      assertThrows(IOException.class, () -> manager.setEnable(CYCLE, ORG, false));
   }

   @Test
   void removeDataCycleThrowsWhenRemoveFailsAndCycleIsStillStored() {
      when(storage.contains(anyString(), eq(ORG))).thenReturn(true);
      when(storage.remove(anyString())).thenReturn(false);

      assertThrows(IOException.class, () -> manager.removeDataCycle(CYCLE, ORG));
   }

   @Test
   void removeDataCycleIgnoresFailedRemoveWhenCycleIsAlreadyGone() throws Exception {
      when(storage.contains(anyString(), eq(ORG))).thenReturn(true, false);
      when(storage.remove(anyString())).thenReturn(false);

      assertDoesNotThrow(() -> manager.removeDataCycle(CYCLE, ORG));
   }

   @Test
   void removeDataCycleSucceedsWhenRemoveSucceeds() throws Exception {
      when(storage.contains(anyString(), eq(ORG))).thenReturn(true);
      when(storage.remove(anyString())).thenReturn(true);

      assertDoesNotThrow(() -> manager.removeDataCycle(CYCLE, ORG));
      verify(storage).remove(anyString());
   }
}
