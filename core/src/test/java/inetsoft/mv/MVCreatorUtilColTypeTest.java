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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The type an MV gives a number column (bug #81572). A column declared as Integer, Short or Byte
 * can hold numbers of a wider type, since a formula column keeps the script result when the
 * declared type cannot hold it (bug #81200). When the values of the sample are numbers of
 * different types the column type used to be the declared one, so the dictionary of the MV
 * converted the wider values back to it and saturated them, e.g. 7.0E9 became 2147483647.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVCreatorUtilColTypeTest {
   /**
    * Integers and a double out of the range of an integer: the widest type is used.
    */
   @Test
   void mixedIntegerAndDoubleUsesDouble() {
      assertEquals(Double.class,
                   colType(XSchema.INTEGER, 1, 2, 7000000007.0, 3));
   }

   @Test
   void mixedIntegerAndLongUsesLong() {
      assertEquals(Long.class, colType(XSchema.INTEGER, 1, 5000000000L, 2));
   }

   @Test
   void mixedShortAndByteDeclaredTypesWiden() {
      assertEquals(Double.class, colType(XSchema.SHORT, (byte) 1, 70000.5));
      assertEquals(Double.class, colType(XSchema.BYTE, 1, 300.5));
   }

   /**
    * The widest type does not depend on the order of the values.
    */
   @Test
   void widestTypeDoesNotDependOnTheOrder() {
      assertEquals(Double.class, colType(XSchema.INTEGER, 7000000007.0, 1, 2));
      assertEquals(Double.class, colType(XSchema.INTEGER, 1, 7000000007.0, 2));
   }

   /**
    * A column whose values are all of one type is not changed.
    */
   @Test
   void sameTypeKeepsTheDataType() {
      assertEquals(Integer.class, colType(XSchema.INTEGER, 1, 2, 3));
      // the type of the data when all of it is wider than the declared type (bug #57217)
      assertEquals(Double.class, colType(XSchema.INTEGER, 1.5, 2.5, 3.5));
   }

   /**
    * A declared type that is already the widest stays.
    */
   @Test
   void widerDeclaredTypeStays() {
      assertEquals(Double.class, colType(XSchema.DOUBLE, 1, 2.5, 3));
      assertEquals(Double.class, colType(XSchema.DOUBLE, 1, 2, 3));
   }

   /**
    * The aggregate output of an aggregated MV keeps the integral type of its data instead of
    * the double declared by the formula, as the live query returns it (bug #82319). Decimals
    * and mixed values keep the declared type.
    */
   @Test
   void aggregateOutputKeepsTheIntegralType() {
      assertEquals(Integer.class, aggregateColType(XSchema.DOUBLE, 1, 2, 3));
      assertEquals(Long.class, aggregateColType(XSchema.DOUBLE, 1L, 5000000000L));
      assertEquals(Double.class, aggregateColType(XSchema.DOUBLE, 1, 2.5, 3));
      assertEquals(Double.class,
                   aggregateColType(XSchema.DOUBLE, new java.math.BigDecimal("12101")));
   }

   /**
    * Values that are not all numbers keep the declared type as before.
    */
   @Test
   void mixedNonNumbersKeepTheDeclaredType() {
      assertEquals(Integer.class, colType(XSchema.INTEGER, 1, "abc", 7000000007.0));
   }

   /**
    * Nulls and blanks are skipped.
    */
   @Test
   void nullsAreSkipped() {
      assertEquals(Double.class, colType(XSchema.INTEGER, null, 1, null, 7000000007.0, ""));
   }

   // the type the MV gives the column of the values declared as the data type
   private static Class<?> colType(String dataType, Object... values) {
      Object[][] data = new Object[values.length + 1][];
      data[0] = new Object[] { "num" };

      for(int i = 0; i < values.length; i++) {
         data[i + 1] = new Object[] { values[i] };
      }

      ColumnRef ref = new ColumnRef(new AttributeRef(null, "num"));
      ref.setDataType(dataType);
      return MVCreatorUtil.getColType(new DefaultTableLens(data), new MVColumn(ref, false), 0);
   }

   // the type the MV gives an aggregate output column of an aggregated MV
   private static Class<?> aggregateColType(String dataType, Object... values) {
      Object[][] data = new Object[values.length + 1][];
      data[0] = new Object[] { "num" };

      for(int i = 0; i < values.length; i++) {
         data[i + 1] = new Object[] { values[i] };
      }

      ColumnRef ref = new ColumnRef(new AttributeRef(null, "num"));
      ref.setDataType(dataType);
      return MVCreatorUtil.getColType(new DefaultTableLens(data), new MVColumn(ref, false), 0,
                                      true);
   }
}
