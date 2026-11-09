/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.mv.data.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79858: an MV dimension typed TIME_INSTANT whose JDBC values are java.time objects
 * (H2 TIMESTAMP WITH TIME ZONE returns OffsetDateTime) must keep its values in the dictionary.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVBuilderTimeInstantOffsetTest {
   @Test
   void offsetDateTimeValuesSurvive() {
      Object[] vals = {
         OffsetDateTime.parse("2020-01-01T00:00:00Z"),
         OffsetDateTime.parse("2020-01-02T10:30:00+08:00"),
         OffsetDateTime.parse("2020-01-03T12:00:00-05:00")
      };
      Set<Long> expected = new TreeSet<>();

      for(Object v : vals) {
         expected.add(((OffsetDateTime) v).toInstant().toEpochMilli());
      }

      assertEquals(expected, dictionaryMillis(vals));
   }

   @Test
   void localDateTimeValuesSurvive() {
      Object[] vals = { LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 1, 2, 10, 30) };
      Set<Long> expected = new TreeSet<>();

      for(Object v : vals) {
         expected.add(Timestamp.valueOf((LocalDateTime) v).getTime());
      }

      assertEquals(expected, dictionaryMillis(vals));
   }

   @Test
   void timestampControlStillWorks() {
      Object[] vals = { new Timestamp(1577836800000L), new Timestamp(1577961000000L) };
      assertEquals(new TreeSet<>(Arrays.asList(1577836800000L, 1577961000000L)),
                   dictionaryMillis(vals));
   }

   /** Builds an MV over one TIME_INSTANT dimension and returns the dictionary values (ms). */
   private Set<Long> dictionaryMillis(Object[] vals) {
      Object[][] data = new Object[vals.length + 1][];
      data[0] = new Object[] { "dim" };

      for(int i = 0; i < vals.length; i++) {
         data[i + 1] = new Object[] { vals[i] };
      }

      ColumnRef ref = new ColumnRef(new AttributeRef(null, "dim"));
      ref.setDataType(XSchema.TIME_INSTANT);
      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(ref, true));
      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);

      MVBuilder builder = new MVBuilder(new DefaultTableLens(data), def, false);
      Iterator<SubMV> it = builder.getSubMVs();
      Set<Long> result = new TreeSet<>();
      int mvs = 0;

      while(it.hasNext()) {
         it.next();
         mvs++;
      }

      XDimDictionary dict = builder.getMV().getDictionaries(0)[0];
      assertFalse(dict.containsNull(), "values were lost to null");

      for(int i = 0; i < dict.size(); i++) {
         Object v = dict.getValue(i);
         assertTrue(v instanceof Date, "dictionary value " + v);
         result.add(((Date) v).getTime());
      }

      assertTrue(mvs > 0);
      return result;
   }
}
