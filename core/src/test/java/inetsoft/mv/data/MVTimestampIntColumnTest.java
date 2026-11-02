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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79649: the unsigned 32-bit seconds range of an MV timestamp measure must round-trip
 * through setValue/getValue, including values after 2038-01-19 (2^31 seconds).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVTimestampIntColumnTest {
   private static final double MAX_UINT_SECONDS = 4294967295.0;

   private MVTimestampIntColumn newColumn() {
      return new MVTimestampIntColumn(null, 0, null, 8, true);
   }

   @Test
   void timestampAfter2038RoundTrips() {
      MVTimestampIntColumn col = newColumn();
      col.setValue(0, 4.0E12); // a timestamp in 2096, in ms

      assertEquals(4.0E12, col.getValue(0), 0.0);
   }

   @Test
   void valuesAroundSignedIntLimitRoundTrip() {
      MVTimestampIntColumn col = newColumn();
      double[] millis = {
         (Integer.MAX_VALUE - 1L) * 1000.0,
         Integer.MAX_VALUE * 1000.0,
         (Integer.MAX_VALUE + 1L) * 1000.0,
         (Integer.MAX_VALUE + 2L) * 1000.0
      };

      for(int i = 0; i < millis.length; i++) {
         col.setValue(i, millis[i]);
      }

      for(int i = 0; i < millis.length; i++) {
         assertEquals(millis[i], col.getValue(i), 0.0, "index " + i);
      }
   }

   @Test
   void unsignedUpperLimitRoundTrips() {
      MVTimestampIntColumn col = newColumn();
      col.setValue(0, MAX_UINT_SECONDS * 1000);

      assertEquals(MAX_UINT_SECONDS * 1000, col.getValue(0), 0.0);
   }

   @Test
   void timestampBefore2038StillRoundTrips() {
      MVTimestampIntColumn col = newColumn();
      col.setValue(0, 1.7E12);
      col.setValue(1, 1000.0);

      assertEquals(1.7E12, col.getValue(0), 0.0);
      assertEquals(1000.0, col.getValue(1), 0.0);
   }

   @Test
   void nullRoundTrips() {
      MVTimestampIntColumn col = newColumn();
      col.setValue(0, Tool.NULL_DOUBLE);

      assertEquals(Tool.NULL_DOUBLE, col.getValue(0));
   }
}
