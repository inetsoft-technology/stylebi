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

import inetsoft.sree.internal.DataCycleManager.CycleInfo;
import org.junit.jupiter.api.*;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tier: [unit] - value semantics of DataCycleManager.CycleInfo (Bug #77191).
 */
@Tag("core")
class DataCycleManagerCycleInfoTest {
   @Test
   void distinctInstancesWithSameValuesAreEqual() {
      CycleInfo a = createInfo();
      CycleInfo b = createInfo();

      assertNotSame(a, b);
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertEquals(a, a.clone());
   }

   @Test
   void runtimeFieldsTakePartInEquality() {
      assertDiffers(i -> i.setStartNotify(false));
      assertDiffers(i -> i.setStartEmail("x@example.com"));
      assertDiffers(i -> i.setEndNotify(false));
      assertDiffers(i -> i.setEndEmail("x@example.com"));
      assertDiffers(i -> i.setFailureNotify(false));
      assertDiffers(i -> i.setFailureEmail("x@example.com"));
      assertDiffers(i -> i.setExceedNotify(false));
      assertDiffers(i -> i.setExceedEmail("x@example.com"));
      assertDiffers(i -> i.setThreshold(99));
      assertDiffers(i -> i.setName("other"));
      assertDiffers(i -> i.setOrgId("other-org"));
   }

   @Test
   void auditFieldsDoNotTakePartInEquality() {
      CycleInfo a = createInfo();
      CycleInfo b = createInfo();
      b.setLastModified(12345L);
      b.setLastModifiedBy("someone");
      b.setCreated(67890L);
      b.setCreatedBy("creator");

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   void notEqualToNullOrOtherType() {
      CycleInfo a = createInfo();

      assertNotEquals(null, a);
      assertNotEquals("c1", a);
   }

   private static void assertDiffers(Consumer<CycleInfo> change) {
      CycleInfo a = createInfo();
      CycleInfo b = createInfo();
      change.accept(b);
      assertNotEquals(a, b);
   }

   private static CycleInfo createInfo() {
      CycleInfo info = new CycleInfo("c1", "host-org");
      info.setStartNotify(true);
      info.setStartEmail("start@example.com");
      info.setEndNotify(true);
      info.setEndEmail("end@example.com");
      info.setFailureNotify(true);
      info.setFailureEmail("fail@example.com");
      info.setExceedNotify(true);
      info.setExceedEmail("exceed@example.com");
      info.setThreshold(10);
      return info;
   }
}
