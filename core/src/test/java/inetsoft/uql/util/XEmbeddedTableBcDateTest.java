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

package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;

/**
 * A BC date in an embedded table must reload as the same BC date, not the AD date with the
 * same year (Bug #77442).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XEmbeddedTableBcDateTest {
   @Test
   void bcDatesSurviveDataRoundTrip() throws Exception {
      java.sql.Date date = new java.sql.Date(bc(44, 3, 15, 0, 0, 0));
      Timestamp ts = new Timestamp(bc(1, 1, 1, 10, 30, 5));
      XEmbeddedTable original = new XEmbeddedTable(
         new String[] { XSchema.DATE, XSchema.TIME_INSTANT },
         new Object[][] { { "date", "ts" }, { date, ts } });

      // piece data is the format EmbeddedTableAssembly persists, full data the other one
      for(boolean piece : new boolean[] { true, false }) {
         original.reset();
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         original.writeData(new DataOutputStream(buf), piece);
         XEmbeddedTable table = new XEmbeddedTable();
         table.parseData(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())),
                         piece, true);
         Assertions.assertEquals(date.getTime(), ((Date) table.getObject(1, 0)).getTime());
         Assertions.assertEquals(ts.getTime(), ((Date) table.getObject(1, 1)).getTime());
      }
   }

   // BC dates are created through the ERA field, the hybrid Julian/Gregorian calendar the
   // persistent date formats use
   private static long bc(int year, int month, int day, int hour, int min, int sec) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(Calendar.ERA, GregorianCalendar.BC);
      cal.set(year, month - 1, day, hour, min, sec);
      return cal.getTimeInMillis();
   }
}
