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
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.RadioButtonVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80443 sub-gap 3: {@code set_format} target {@code optionText} writes a CheckBox/
 * RadioButton's shared option-label format path ({@code TableDataPath(-1, DETAIL)} — the same
 * shape a plain Table's {@code data} target and a Selection List's {@code cell} target already
 * write to).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceOptionTextTargetTest {
   private static CheckBoxVSAssembly checkBox() {
      CheckBoxVSAssembly assembly = mock(CheckBoxVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(mock(CheckBoxVSAssemblyInfo.class));
      return assembly;
   }

   private static RadioButtonVSAssembly radioButton() {
      RadioButtonVSAssembly assembly = mock(RadioButtonVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(mock(RadioButtonVSAssemblyInfo.class));
      return assembly;
   }

   private static RuntimeViewsheet rvsWith(String name, VSAssembly assembly) {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(name)).thenReturn(assembly);
      return rvs;
   }

   private static VSObjectFormatInfoModel red() {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#FF0000");
      return format;
   }

   private static ViewsheetFormatService.FormatRequest request(String assembly) {
      return request(assembly, "optionText");
   }

   private static ViewsheetFormatService.FormatRequest request(String assembly, String target) {
      return new ViewsheetFormatService.FormatRequest(List.of(assembly), red(), false, target,
                                                      null, null);
   }

   private static FormatVSObjectEvent write(RuntimeViewsheet rvs, FormatPainterService painter,
                                            String assembly) throws Exception
   {
      new ViewsheetFormatService(sessionsFor(rvs), painter, mock(CalcTableService.class), null)
         .setFormat("tok", principal(), request(assembly), "");
      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(java.security.Principal.class),
                                any(), anyString());
      return captor.getValue();
   }

   @Test
   void optionTextWritesTheSharedDetailPathOnACheckBox() throws Exception {
      FormatVSObjectEvent event =
         write(rvsWith("CheckBox1", checkBox()), mock(FormatPainterService.class), "CheckBox1");

      assertNotNull(event.getData(), "optionText must populate event.getData()");
      assertEquals(1, event.getData().size());
      assertArrayEquals(new TableDataPath[]{ new TableDataPath(-1, TableDataPath.DETAIL) },
                        event.getData().get(0));
   }

   @Test
   void optionTextWritesTheSharedDetailPathOnARadioButton() throws Exception {
      FormatVSObjectEvent event =
         write(rvsWith("Radio1", radioButton()), mock(FormatPainterService.class), "Radio1");

      assertArrayEquals(new TableDataPath[]{ new TableDataPath(-1, TableDataPath.DETAIL) },
                        event.getData().get(0));
   }

   @Test
   void refusesOptionTextOnANonOptionAssemblyBeforeMutating() throws Exception {
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getVSAssemblyInfo()).thenReturn(mock(TableVSAssemblyInfo.class));
      ViewsheetSessionService sessions = sessionsFor(rvsWith("Table1", table));
      FormatPainterService painter = mock(FormatPainterService.class);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessions, painter, mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("Table1"), ""));

      assertTrue(e.getMessage().contains("'optionText' only applies to a CheckBox or RadioButton") &&
                 e.getMessage().contains("'Table1'") &&
                 e.getMessage().contains("TableVSAssembly") &&
                 e.getMessage().contains("not applied"), e.getMessage());
      verify(sessions, never()).mutate(anyString(), any(java.security.Principal.class), any());
      verifyNoInteractions(painter);
   }

   @Test
   void refusesAMissingAssembly() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(rvsWith("CheckBox1", null)),
                                          mock(FormatPainterService.class),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("CheckBox1"), ""));
      assertTrue(e.getMessage().contains("no assembly named 'CheckBox1'"), e.getMessage());
   }

   @Test
   void unknownTargetErrorListsOptionText() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> new ViewsheetFormatService(sessionsFor(null), mock(FormatPainterService.class),
                                          mock(CalcTableService.class), null)
            .setFormat("tok", principal(), request("CheckBox1", "bogus"), ""));
      assertTrue(e.getMessage().contains("'optionText'"), e.getMessage());
   }
}
