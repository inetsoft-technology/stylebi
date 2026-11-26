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
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.model.ws.ExpressionDialogModel;
import inetsoft.web.composer.model.ws.ExpressionDialogModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81656: saving a calculated column with the SQL option must get the same missing column,
 * self reference and cycle warnings as a script column.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SqlExpressionValidationTest {
   private static final String RUNTIME_ID = "ws1";
   private static final String TABLE = "T";

   private final Principal principal =
      new SRPrincipal(new IdentityID("bob", Organization.getDefaultOrganizationID()));
   private EmbeddedTableAssembly table;
   private ExpressionDialogService service;

   @BeforeEach
   void setUp() throws Exception {
      Worksheet worksheet = new Worksheet();
      table = new EmbeddedTableAssembly(worksheet, TABLE);
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER },
         new Object[][] { { "id", "b" }, { 1, 2 } }));
      worksheet.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getWorksheet(RUNTIME_ID, principal)).thenReturn(rws);
      service = new ExpressionDialogService(viewsheetService, null, null);
   }

   private void addColumn(String name, String expression, boolean sql) {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(expression);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      column.setSQL(sql);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   private String validate(String oldName, String newName, String expression, boolean sql)
      throws Exception
   {
      ExpressionDialogModel model = ExpressionDialogModel.builder()
         .tableName(TABLE).oldName(oldName).newName(newName).dataType(XSchema.INTEGER)
         .expression(expression).setSQL(sql).build();
      ExpressionDialogModelValidator v = service.validateExpression(RUNTIME_ID, model, principal);
      return v.invalidExpression();
   }

   @Test
   void sqlMissingColumnWarns() throws Exception {
      String msg = validate("", "a1", "field['nope']", true);
      assertNotNull(msg);
      assertTrue(msg.contains("nope"), msg);
   }

   @Test
   void sqlSelfReferenceWarns() throws Exception {
      String msg = validate("", "a1", "field['a1']+1", true);
      assertNotNull(msg);
      assertTrue(msg.contains("itself"), msg);
   }

   @Test
   void sqlMutualReferenceWarns() throws Exception {
      addColumn("a", "field['id']", true);
      addColumn("b2", "field['a']+1", true);
      String msg = validate("a", "a", "field['b2']+1", true);
      assertNotNull(msg);
      assertTrue(msg.contains("each other"), msg);
   }

   @Test
   void scriptColumnCyclingThroughSqlColumnWarns() throws Exception {
      addColumn("a", "field['id']", true);
      addColumn("b2", "field['a']+1", false);
      assertNotNull(validate("a", "a", "field['b2']+1", true));
   }

   @Test
   void validSqlColumnDoesNotWarn() throws Exception {
      addColumn("a", "field['id']", true);
      assertNull(validate("", "c", "field['a'] + field['b']", true));
   }

   @Test
   void referenceInsideScriptCommentIsIgnored() throws Exception {
      assertNull(validate("", "c", "field['id'] /*script field['nope'] script*/", true));
   }
}
