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
package inetsoft.report;

import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.script.formula.FormulaFunctions;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.CalcGroup;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78375: the manual sort order of a Freehand table group cell (OrderInfo.manualOrder)
 * saved before Bug #77605 on a th_TH server holds Buddhist years. LayoutTool passes it to
 * toList() as the manualvalues option, where it is compared with the Gregorian group values,
 * so it is converted when date.legacy.buddhist.compat is on and the group values are dates.
 * Values of other types, date part levels, named group names and the others label are kept.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegacyBuddhistManualOrderTest {
   @AfterEach
   void restore() {
      SreeEnv.remove(COMPAT_PROPERTY);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void dateManualOrder(boolean on) {
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      // designed order 2026, 2024, 2025 saved with Buddhist years
      List<String> manual = List.of(
         "2569-01-01 00:00:00", "2567-01-01 00:00:00", "2568-01-01 00:00:00");
      OrderInfo order = manualOrder(manual, XConstants.YEAR_DATE_GROUP);
      String opt = createOption(XSchema.DATE, order);

      Object[] years = (Object[]) FormulaFunctions.toList(new Object[] {
         new Timestamp(date(2024, 3, 5)), new Timestamp(date(2025, 7, 1)),
         new Timestamp(date(2026, 2, 2)), new Timestamp(date(2024, 11, 11)) }, opt);

      // the designed order is lost and the values fall back to ascending when not converted
      assertEquals(on ? List.of(2026, 2024, 2025) : List.of(2024, 2025, 2026), years(years));
      // the order info itself is not changed
      assertEquals(manual, order.getManualOrder());
   }

   // a string column holding Buddhist date text is compared with the raw manual order
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void stringManualOrderUnchanged(boolean on) {
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      List<String> manual = List.of("2569-01-01", "2567-01-01", "2568-01-01");
      // a new OrderInfo has the day date option even for a string column
      OrderInfo order = manualOrder(manual, XConstants.DAY_DATE_GROUP);
      String opt = createOption(XSchema.STRING, order);

      assertTrue(opt.contains("manualvalues=2569-01-01;2567-01-01;2568-01-01"), opt);
      Object[] values = (Object[]) FormulaFunctions.toList(
         new Object[] { "2567-01-01", "2568-01-01", "2569-01-01" }, opt);
      assertArrayEquals(manual.toArray(), values);
   }

   // date part values are numbers, not dates
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void datePartManualOrderUnchanged(boolean on) {
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      OrderInfo order = manualOrder(List.of("2567", "1"), XConstants.QUARTER_OF_YEAR_DATE_GROUP);

      assertTrue(createOption(XSchema.DATE, order).contains("manualvalues=2567;1"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void namedGroupNamesUnchanged(boolean on) {
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      OrderInfo order = namedGroupOrder();

      assertTrue(createOption(XSchema.DATE, order).contains(
         "manualvalues=2567 Target;" + (on ? "2025" : "2568") + "-01-01 00^_^00^_^00;2569 Others"));
   }

   // the crosstab optimized calc table expression has its own manualvalues option
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void crosstabManualOrder(boolean on) throws Exception {
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      OrderInfo order = manualOrder(List.of(
         "2569-01-01 00:00:00", "2567-01-01 00:00:00", "2568-01-01 00:00:00"),
         XConstants.YEAR_DATE_GROUP);
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "col");
      cell.setBType(TableCellBinding.GROUP);
      cell.setOrderInfo(order);
      GroupRef group = group(XSchema.DATE, order);

      Method method = LayoutTool.class.getDeclaredMethod(
         "createCrosstabCalcExpression", TableLens.class, List.class, CalcGroup[].class,
         String[].class, TableCellBinding.class, String.class, boolean.class, boolean.class);
      method.setAccessible(true);
      String exp = (String) method.invoke(null, null, List.of(cell), new CalcGroup[] { group },
                                          new String[] { "col" }, cell, "data", true, false);
      Object[] years = (Object[]) FormulaFunctions.toList(new Object[] {
         new Date(date(2024, 1, 1)), new Date(date(2025, 1, 1)), new Date(date(2026, 1, 1)) },
         option(exp));

      assertEquals(on ? List.of(2026, 2024, 2025) : List.of(2024, 2025, 2026), years(years));
   }

   // the manual order sent to the client with a cell binding (VSTableLayoutService) and given
   // to the manual order dialog values (VSDataService) takes the type of the source column
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void cellManualOrder(boolean on) {
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      OrderInfo order = namedGroupOrder();
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("date", XSchema.DATE));
      columns.addAttribute(column("text", XSchema.STRING));
      List<String> manual = order.getManualOrder();

      List<String> result = LayoutTool.getGregorianManualOrder(
         order, columns, "date", XConstants.YEAR_DATE_GROUP);
      assertEquals(List.of("2567 Target", on ? "2025-01-01 00:00:00" : "2568-01-01 00:00:00",
                           "2569 Others"), result);
      assertSame(manual, order.getManualOrder());

      // the client sorts the dialog values (Gregorian) by the cell's manual order, as
      // calc-group-option.component.ts getSortedManualOrders() does, a value not in the
      // manual order would be moved before the named group
      List<String> dialog = new ArrayList<>(List.of("2567 Target", "2025-01-01 00:00:00"));
      dialog.sort(Comparator.comparingInt(result::indexOf));
      assertEquals(on ? List.of("2567 Target", "2025-01-01 00:00:00") :
                      List.of("2025-01-01 00:00:00", "2567 Target"), dialog);

      // a string column, a date part level or a column that is not found is not converted
      assertSame(manual, LayoutTool.getGregorianManualOrder(
         order, columns, "text", XConstants.YEAR_DATE_GROUP));
      assertSame(manual, LayoutTool.getGregorianManualOrder(
         order, columns, "date", XConstants.MONTH_OF_YEAR_DATE_GROUP));
      assertSame(manual, LayoutTool.getGregorianManualOrder(
         order, columns, "other", XConstants.YEAR_DATE_GROUP));
      assertSame(manual, LayoutTool.getGregorianManualOrder(
         order, null, "date", XConstants.YEAR_DATE_GROUP));
   }

   @Test
   void gregorianManualOrderUnchanged() {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");
      OrderInfo order = manualOrder(
         List.of("2026-01-01 00:00:00", "2024-01-01 00:00:00"), XConstants.YEAR_DATE_GROUP);

      assertSame(order.getManualOrder(), LayoutTool.getGregorianManualOrder(order, true));
   }

   private static OrderInfo manualOrder(List<String> values, int option) {
      OrderInfo order = new OrderInfo();
      order.setOrder(XConstants.SORT_SPECIFIC);
      order.setInterval(1, option);
      order.setManualOrder(new ArrayList<>(values));
      return order;
   }

   // a named group name and the others label in the same list as an ungrouped date value
   private static OrderInfo namedGroupOrder() {
      OrderInfo order = manualOrder(
         List.of("2567 Target", "2568-01-01 00:00:00", "2569 Others"), XConstants.YEAR_DATE_GROUP);
      SNamedGroupInfo groups = new SNamedGroupInfo();
      groups.addGroupName("2567 Target");
      order.setNamedGroupInfo(groups);
      order.setOtherLabel("2569 Others");
      return order;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      return column;
   }

   private static GroupRef group(String type, OrderInfo order) {
      GroupRef group = new GroupRef(column("col", type));
      group.setOrderInfo(order);
      return group;
   }

   // the options of the toList() expression of a group cell, which hold no quote or comma
   private static String createOption(String type, OrderInfo order) {
      String exp = LayoutTool.createGroupExpression(
         new CalcGroup[] { group(type, order) }, new String[] { "col" }, 0, null, "data", null,
         null, null, null, null, null, null, false);
      return option(exp);
   }

   private static String option(String exp) {
      int idx = exp.lastIndexOf(",'");

      assertTrue(idx > 0 && exp.endsWith("')"), exp);
      return exp.substring(idx + 2, exp.length() - 2);
   }

   private static List<Integer> years(Object[] values) {
      List<Integer> years = new ArrayList<>();

      for(Object value : values) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) value);
         years.add(cal.get(Calendar.YEAR));
      }

      return years;
   }

   private static long date(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTimeInMillis();
   }

   private static final String COMPAT_PROPERTY = "date.legacy.buddhist.compat";
}
