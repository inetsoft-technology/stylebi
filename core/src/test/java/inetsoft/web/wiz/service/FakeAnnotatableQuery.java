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

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.AnnotatableQuery;
import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.TabularQuery;

/**
 * Minimal, REAL (non-mock) stand-in for a {@link AnnotatableQuery}-implementing connector such as
 * {@code ODataQuery}/{@code SAPTableQuery} -- exists so {@code MetadataApiService.getTabularTableMeta}
 * (and {@code TabularQueryParamsSchemaBuilder}) have something to reflect over without depending on
 * the {@code inetsoft-odata}/{@code inetsoft-sap} connector modules (core does not depend on them).
 * Mirrors {@link FakeBrowsableQuery}'s existing pattern for {@code BrowsableQuery}.
 *
 * {@code target}'s value drives {@link #getOutputColumns()} so a test can exercise both the
 * populated-columns and the empty-columns cases without any StyleBI runtime: {@code "empty"}
 * answers zero columns, any other non-null value answers one column named after it.
 */
public class FakeAnnotatableQuery extends TabularQuery implements AnnotatableQuery {
   public FakeAnnotatableQuery() {
      super("FakeAnnotatable");
   }

   @Property(label = "Target")
   @PropertyEditor(tagsMethod = "getTargetNames")
   public String getTarget() {
      return target;
   }

   public void setTarget(String target) {
      this.target = target;
   }

   /** The zero-arg tagsMethod {@code @PropertyEditor} above names -- a fixed, self-contained list. */
   public String[] getTargetNames() {
      return new String[] { "Alpha", "Beta" };
   }

   @Override
   public String getAnnotationTargetProperty() {
      return annotationTargetProperty;
   }

   /** Lets a test simulate an AnnotatableQuery/connector mismatch (a name with no matching @Property). */
   public void setAnnotationTargetProperty(String annotationTargetProperty) {
      this.annotationTargetProperty = annotationTargetProperty;
   }

   @Override
   public XTypeNode[] getOutputColumns() {
      if(target == null) {
         return null;
      }

      if("empty".equals(target)) {
         return new XTypeNode[0];
      }

      XTypeNode idColumn = XSchema.createPrimitiveType(XSchema.STRING);
      idColumn.setName(target + "_id");
      XTypeNode dateColumn = XSchema.createPrimitiveType(XSchema.DATE);
      dateColumn.setName(target + "_created");

      return new XTypeNode[] { idColumn, dateColumn };
   }

   private String target;
   private String annotationTargetProperty = "target";
}
