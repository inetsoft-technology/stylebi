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
package inetsoft.uql.tabular;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Choosing an editor type only classifies the property type, so it must not run the type's
 * static initializer (Bug #77448).
 */
@Tag("core")
class TabularUtilEditorTypeTest {
   // kept outside the sentinel class, reading a field of the sentinel would initialize it
   private static boolean sentinelInitialized;

   @Test
   void classifiesWithoutInitializing() {
      assertEquals(TabularEditor.Type.DOUBLE,
                   TabularUtil.getEditorTypeFromClassName(InitTrackingNumber.class.getName()));
      assertFalse(sentinelInitialized);
   }

   @Test
   void classifiesTypeWhoseInitializerFails() {
      assertEquals(TabularEditor.Type.DOUBLE,
                   TabularUtil.getEditorTypeFromClassName(FailingInitNumber.class.getName()));
   }

   @ParameterizedTest
   @CsvSource({
      "java.lang.String, TEXT",
      "java.lang.Boolean, BOOLEAN",
      "java.lang.Integer, INT",
      "java.lang.Long, LONG",
      "java.lang.Double, DOUBLE",
      "java.math.BigDecimal, DOUBLE",
      "java.util.Date, DATE",
      "java.sql.Timestamp, DATE",
      "java.io.File, FILE",
      "java.util.ArrayList, LIST",
      "'[Ljava.lang.String;', LIST",
      "'[Linetsoft.uql.tabular.ColumnDefinition;', COLUMN",
      "inetsoft.uql.tabular.QueryParameter, PARAMETER",
      "java.util.concurrent.TimeUnit, TEXT",
      "no.such.Clazz, TEXT"
   })
   void classificationUnchanged(String className, TabularEditor.Type expected) {
      assertEquals(expected, TabularUtil.getEditorTypeFromClassName(className));
   }

   static class InitTrackingNumber extends Number {
      static {
         sentinelInitialized = true;
      }

      @Override
      public int intValue() {
         return 0;
      }

      @Override
      public long longValue() {
         return 0;
      }

      @Override
      public float floatValue() {
         return 0;
      }

      @Override
      public double doubleValue() {
         return 0;
      }
   }

   static class FailingInitNumber extends Number {
      static {
         if(true) {
            throw new IllegalStateException("static initializer must not run");
         }
      }

      @Override
      public int intValue() {
         return 0;
      }

      @Override
      public long longValue() {
         return 0;
      }

      @Override
      public float floatValue() {
         return 0;
      }

      @Override
      public double doubleValue() {
         return 0;
      }
   }
}
