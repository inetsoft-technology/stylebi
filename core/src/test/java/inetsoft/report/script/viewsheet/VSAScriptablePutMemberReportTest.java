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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81633: a failed property assignment must reach the script author when the product has
 * a clear error text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSAScriptablePutMemberReportTest {
   private TextVSAScriptable scriptable;

   @BeforeEach
   void setUp() {
      Viewsheet viewsheet = new Viewsheet();
      TextVSAssembly assembly = new TextVSAssembly();
      assembly.getVSAssemblyInfo().setName("Text1");
      viewsheet.addAssembly(assembly);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);

      scriptable = new TextVSAScriptable(box);
      scriptable.setAssembly("Text1");
      Tool.getUserMessage();
   }

   @Test
   void invalidPopLocationReachesAuthor() {
      scriptable.putMember("popLocation", "x");

      UserMessage msg = Tool.getUserMessage();
      assertNotNull(msg);
      assertTrue(msg.getMessage().contains("popLocation"));
      assertTrue(msg.getMessage().contains("Invalid PopLocation"));
   }

   @Test
   void rawNullPointerStaysInLogOnly() {
      scriptable.putMember("position", null);

      assertNull(Tool.getUserMessage());
   }
}
