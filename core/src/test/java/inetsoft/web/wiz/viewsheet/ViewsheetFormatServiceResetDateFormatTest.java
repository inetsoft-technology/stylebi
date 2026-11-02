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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79999: a {@code data} reset removes a column's own format, so under a whole-table date
 * format the column would inherit it and render its numbers as 1970-01-01. Needs the SREE context
 * because resolving the object format reads a runtime value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceResetDateFormatTest {
   private static FormatInfo objectDateAndOwnDecimal(String header) {
      FormatInfo formatInfo = new FormatInfo();
      VSCompositeFormat own = new VSCompositeFormat();
      own.getUserDefinedFormat().setFormatValue("DecimalFormat");
      own.getUserDefinedFormat().setFormatExtentValue("#,##0");
      formatInfo.setFormat(new TableDataPath(-1, TableDataPath.DETAIL, XSchema.DOUBLE,
                                             new String[]{ header }), own);
      VSCompositeFormat object = new VSCompositeFormat();
      object.getUserDefinedFormat().setFormatValue(XConstants.DATE_FORMAT);
      formatInfo.setFormat(VSAssemblyInfo.OBJECTPATH, object);
      return formatInfo;
   }

   private static RuntimeViewsheet rvs() {
      ColumnSelection selection = new ColumnSelection();
      selection.addAttribute(column("Order Date", XSchema.DATE));
      selection.addAttribute(column("Revenue", XSchema.DOUBLE));
      TableVSAssembly table = mock(TableVSAssembly.class);
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(table.getColumnSelection()).thenReturn(selection);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(info.getFormatInfo()).thenReturn(objectDateAndOwnDecimal("Revenue"));
      return rvsWith("Table1", table);
   }

   private static ViewsheetFormatService.FormatRequest reset(String field) {
      return new ViewsheetFormatService.FormatRequest(List.of("Table1"), null, true, "data",
                                                      field);
   }

   @Test
   void refusesResettingAColumnFormatThatShieldsItFromAnObjectDateFormat() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      ViewsheetSessionService sessions = sessionsFor(rvs());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service(sessions, painter, null).setFormat("tok", principal(), reset("Revenue"),
                                                          ""));

      assertTrue(thrown.getMessage().contains("Revenue"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("1970"), thrown.getMessage());
      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      verifyNoInteractions(painter);
   }

   /**
    * The reset guard must not fire for a date column. (The mocked table has no rendered rows, so
    * the write itself is refused later for an unrelated reason; only the guard is asserted.)
    */
   @Test
   void doesNotRefuseResettingTheFormatOfADateColumnUnderAnObjectDateFormat() {
      FormatPainterService painter = mock(FormatPainterService.class);

      try {
         service(sessionsFor(rvs()), painter, null).setFormat("tok", principal(),
                                                              reset("Order Date"), "");
      }
      catch(Exception e) {
         assertFalse(String.valueOf(e.getMessage()).contains("inherit the whole-table"),
                     e.getMessage());
      }
   }
}
