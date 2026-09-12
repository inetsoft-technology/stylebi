/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.uql.erm;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the script-driven branch of {@link HiddenColumns#evaluate}. These
 * run a real VPM script through the GraalJS engine, so they need the sree home
 * / Spring context (unlike the lightweight {@link HiddenColumnsTest}).
 *
 * <p>Regression coverage for #75582: a hidden-columns script that returns the
 * {@code hiddenColumns} variable without reassigning it hands back the
 * {@link inetsoft.uql.script.StringArray} scope object under GraalJS (Rhino used
 * to unwrap it via {@code Wrapper.unwrap()}). {@code evaluate} must unwrap it to
 * a {@code String[]} instead of failing the "should be a string array" check.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HiddenColumnsScriptTest {

   /**
    * #75582: the script returns the unmodified {@code hiddenColumns} object
    * (its role check does not match), so evaluate must fall back to the
    * GUI-defined hidden columns rather than throwing.
    */
   @Test
   void evaluateScriptReturningUnmodifiedHiddenColumnsUsesGuiColumns() throws Exception {
      HiddenColumns hiddenColumns = new HiddenColumns();
      hiddenColumns.addHiddenColumn(new AttributeRef("employees", "salary"));
      hiddenColumns.setScript(
         "var rtmp;\n" +
         "for(var i = 0; i < roles.length; i++) {\n" +
         "   if(roles[i] == 'Special') { rtmp = true; }\n" +
         "}\n" +
         "if(rtmp == true) {\n" +
         "   hiddenColumns = ['employees.bonus'];\n" +
         "}\n" +
         "hiddenColumns;");

      // user without the 'Special' role -> script leaves hiddenColumns untouched
      XPrincipal user = makeUnknownUser(new IdentityID[]{ new IdentityID("viewer", null) });

      String[] result = hiddenColumns.evaluate(
         new String[]{ "employees" }, new String[]{ "salary", "bonus", "name" },
         new VariableTable(), user, true, null);

      assertEquals(1, result.length);
      assertEquals(new AttributeRef("employees", "salary").toView(), result[0]);
   }

   /**
    * When the script reassigns {@code hiddenColumns} to a JS array literal the
    * script-provided columns are returned (the previously working path must
    * keep working after the fix).
    */
   @Test
   void evaluateScriptReassigningHiddenColumnsUsesScriptColumns() throws Exception {
      HiddenColumns hiddenColumns = new HiddenColumns();
      hiddenColumns.addHiddenColumn(new AttributeRef("employees", "salary"));
      hiddenColumns.setScript(
         "hiddenColumns = ['employees.bonus'];\n" +
         "hiddenColumns;");

      XPrincipal user = makeUnknownUser(new IdentityID[]{ new IdentityID("viewer", null) });

      String[] result = hiddenColumns.evaluate(
         new String[]{ "employees" }, new String[]{ "salary", "bonus", "name" },
         new VariableTable(), user, true, null);

      assertEquals(1, result.length);
      assertEquals("employees.bonus", result[0]);
   }

   /**
    * Creates an XPrincipal whose name resolves as "unknown_user" so that
    * XUtil.getUserRoles() returns the embedded roles array without calling
    * out to the security engine.
    */
   private XPrincipal makeUnknownUser(IdentityID[] roles) {
      IdentityID id = new IdentityID("unknown_user", null);
      return new XPrincipal(id, roles, new String[0], null);
   }
}
