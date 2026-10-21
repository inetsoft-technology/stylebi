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
package inetsoft.uql.asset;

import inetsoft.uql.XRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79097: a raw-embed {@code $(@name)} placeholder in a WHERE/HAVING condition made
 * {@code getAllVariables} throw a NullPointerException, because the condition fix looked the
 * placeholder up with the "@" still in its name and then dereferenced the missing variable.
 */
@WizAgentTestSupport
class SQLBoundTableAssemblyAtVariable79097Test {
   private static SQLBoundTableAssembly table(XFilterNode where, XFilterNode having) {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly assembly = new SQLBoundTableAssembly(ws, "PC");
      JDBCQuery query = new JDBCQuery();
      UniformSQL sql = new UniformSQL();
      sql.addField(new XField("company_name", "COMPANY_NAME", "c", XSchema.STRING));
      sql.setWhere(where);
      sql.setHaving(having);
      query.setSQLDefinition(sql);
      ((SQLBoundTableAssemblyInfo) assembly.getInfo()).setQuery(query);
      return assembly;
   }

   private static XBinaryCondition condition(String op, String value) {
      return new XBinaryCondition(new XExpression("c.COMPANY_NAME", XExpression.FIELD),
                                  new XExpression(value, XExpression.VALUE), op);
   }

   private static UserVariable[] collect(SQLBoundTableAssembly assembly, UserVariable var) throws Exception {
      XRepository service = mock(XRepository.class);
      when(service.getQueryParameters(any(), any(), anyBoolean()))
         .thenReturn(new UserVariable[] { var });

      AssetRepository assets = mock(AssetRepository.class);

      try(MockedStatic<XRepository> repo = mockStatic(XRepository.class);
          MockedStatic<AssetUtil> util = mockStatic(AssetUtil.class))
      {
         util.when(() -> AssetUtil.getAssetRepository(anyBoolean())).thenReturn(assets);
         repo.when(XRepository::getRepository).thenReturn(service);
         return assembly.getAllVariables();
      }
   }

   private static UserVariable find(UserVariable[] vars, String name) {
      return Arrays.stream(vars).filter(v -> name.equals(v.getName())).findFirst().orElse(null);
   }

   @Test
   void quotedAtPlaceholderDoesNotThrowAndKeepsDeclaredType() throws Exception {
      UserVariable var = new UserVariable("v");
      var.setTypeNode(XSchema.createPrimitiveType(XSchema.INTEGER));
      UserVariable[] vars = collect(table(condition("=", "'$(@v)'"), null), var);

      assertNotNull(find(vars, "v"));
      assertEquals(XSchema.INTEGER, find(vars, "v").getTypeNode().getType());
   }

   @Test
   void atPlaceholderInInIsNotMadeMultiSelect() throws Exception {
      UserVariable[] vars = collect(
         table(condition("IN", "($(@v))"), null), new UserVariable("v"));

      assertFalse(find(vars, "v").isMultipleSelection());
   }

   @Test
   void atPlaceholderInHavingDoesNotThrow() throws Exception {
      UserVariable[] vars = collect(
         table(null, condition("=", "$(@v)")), new UserVariable("v"));

      assertNotNull(find(vars, "v"));
   }

   @Test
   void placeholderWithSpacesDoesNotThrowAndIsTyped() throws Exception {
      UserVariable[] vars = collect(
         table(condition("=", "$( v )"), null), new UserVariable("v"));

      assertEquals(XSchema.STRING, find(vars, "v").getTypeNode().getType());
   }

   @Test
   void uncollectedPlaceholderDoesNotThrow() throws Exception {
      assertDoesNotThrow(() -> collect(
         table(condition("=", "$(?vpm)"), null), new UserVariable("other")));
   }

   @Test
   void plainPlaceholderStillInfersTypeFromColumn() throws Exception {
      UserVariable var = new UserVariable("v");
      var.setTypeNode(XSchema.createPrimitiveType(XSchema.INTEGER));
      UserVariable[] vars = collect(table(condition("=", "'$(v)'"), null), var);

      assertEquals(XSchema.STRING, find(vars, "v").getTypeNode().getType());
   }

   @Test
   void plainPlaceholderInInIsMultiSelect() throws Exception {
      UserVariable[] vars = collect(
         table(condition("IN", "($(v))"), null), new UserVariable("v"));

      assertTrue(find(vars, "v").isMultipleSelection());
   }
}
