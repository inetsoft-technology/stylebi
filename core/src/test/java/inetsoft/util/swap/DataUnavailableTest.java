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
package inetsoft.util.swap;

import inetsoft.test.*;
import inetsoft.util.stall.LockStallException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77910: a lock stall or a lost swap file in a failure's cause chain is found and
 * rethrown as it is, the stall first; any other failure is not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataUnavailableTest {
   @Test
   void lostSwapFileIsFoundInTheCauseChain() {
      try(LostSwapFile lost = new LostSwapFile()) {
         SwapFileReadException swap = assertThrows(SwapFileReadException.class, lost::read);
         RuntimeException wrapped = new RuntimeException("wrapped", swap);

         assertSame(swap, DataUnavailable.find(wrapped));
         assertSame(swap, assertThrows(SwapFileReadException.class,
                                       () -> DataUnavailable.rethrow(wrapped)));
      }
   }

   @Test
   void stallIsFoundBeforeLostSwapFile() {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      // a swap file read failure whose read stalled
      Throwable both = new SwapFileReadException(new File("lost.tdat"), stall);

      assertSame(stall, DataUnavailable.find(both));
      assertSame(stall, assertThrows(LockStallException.class,
                                     () -> DataUnavailable.rethrow(both)));
   }

   @Test
   void otherFailureIsNotFound() {
      IllegalStateException other = new IllegalStateException("read failed");

      assertNull(DataUnavailable.find(other));
      assertNull(DataUnavailable.find(null));
      assertDoesNotThrow(() -> DataUnavailable.rethrow(other));
   }
}
