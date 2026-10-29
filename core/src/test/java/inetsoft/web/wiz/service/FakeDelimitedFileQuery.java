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
package inetsoft.web.wiz.service;

import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.ColumnDefinition;
import inetsoft.uql.tabular.DataType;
import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.SelectableTabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

import java.io.IOException;

/**
 * Real (non-mock) stand-in for a file connector such as {@code ServerFileQuery}: it extends
 * {@link SelectableTabularQuery}, loads its column definitions from {@code header} split on
 * {@code delimiter}, and -- like the real connector -- builds its output columns purely from the
 * cached definitions, never from the source again. An empty {@code header} models an unreadable
 * file ({@code loadColumns} returns null and {@code loadOutputColumns} throws).
 */
@View(vertical = true, value = {
   @View1("delimiter"),
   @View1("header"),
})
public class FakeDelimitedFileQuery extends SelectableTabularQuery {
   public FakeDelimitedFileQuery() {
      super("FakeDelimitedFile");
   }

   @Property(label = "Delimiter")
   public String getDelimiter() {
      return delimiter;
   }

   public void setDelimiter(String delimiter) {
      this.delimiter = delimiter;
   }

   @Property(label = "Header")
   public String getHeader() {
      return header;
   }

   public void setHeader(String header) {
      this.header = header;
   }

   @Override
   protected ColumnDefinition[] loadColumns() {
      if(header == null || header.isEmpty()) {
         return null;
      }

      String[] names = header.split(java.util.regex.Pattern.quote(delimiter));
      ColumnDefinition[] defs = new ColumnDefinition[names.length];

      for(int i = 0; i < names.length; i++) {
         defs[i] = new ColumnDefinition();
         defs[i].setName(names[i]);
         defs[i].setType(DataType.STRING);
         defs[i].setSelected(true);
      }

      return defs;
   }

   @Override
   public void loadOutputColumns(VariableTable vtable) throws Exception {
      ColumnDefinition[] defs = getColumns();

      if(defs == null) {
         throw new IOException("No columns could be read for this file");
      }

      XTypeNode[] cols = new XTypeNode[defs.length];

      for(int i = 0; i < defs.length; i++) {
         cols[i] = XSchema.createPrimitiveType(defs[i].getName(), String.class);
      }

      setOutputColumns(cols);
   }

   /** No-op: the real revalidate() needs the Spring-backed DataSourceRegistry, absent here. */
   @Override
   public void revalidate() {
   }

   private String delimiter = ",";
   private String header = "";
}
