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
package inetsoft.uql.odata;

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetRef;
import inetsoft.uql.tabular.TabularDatasetSchema;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Charter assertion B1: parsing a real {@code $metadata} document yields entity sets and columns
 * with named field-and-type assertions, not a weak "non-empty" check. Pure parsing test — no HTTP,
 * no Spring, no {@link ODataCatalogCache} — {@link ODataCatalog} takes the already-extracted
 * {@code <Schema>} node exactly as {@link ODataRuntime#getSchemaNode} would hand it over.
 */
class ODataCatalogTest {

   private Node schemaNode(String resource) throws Exception {
      try(InputStream in = getClass().getResourceAsStream(resource)) {
         Document doc = Tool.parseXML(in);
         NodeList edmx = doc.getElementsByTagName("edmx:Edmx");
         Node dataServices = Tool.getChildNodeByTagName(edmx.item(0), "edmx:DataServices");
         return Tool.getChildNodeByTagName(dataServices, "Schema");
      }
   }

   @Test
   void parseYieldsEntitySetsInDocumentOrder() throws Exception {
      ODataCatalogSnapshot snapshot = ODataCatalog.parse(schemaNode("catalog.metadata.xml"));

      List<TabularDatasetRef> datasets = snapshot.catalog().datasets();
      assertEquals(2, datasets.size());
      assertEquals("Products", datasets.get(0).id());
      assertEquals("Categories", datasets.get(1).id());
   }

   @Test
   void describeYieldsRealColumnsWithNameAndType() throws Exception {
      ODataCatalogSnapshot snapshot = ODataCatalog.parse(schemaNode("catalog.metadata.xml"));

      TabularDatasetSchema products = snapshot.schemasByEntitySet().get("Products");
      assertNotNull(products);
      assertEquals("Products", products.datasetId());

      List<TabularColumn> columns = products.columns();
      assertEquals(3, columns.size());
      assertEquals(new TabularColumn("ID", XSchema.LONG), columns.get(0));
      assertEquals(new TabularColumn("Name", XSchema.STRING), columns.get(1));
      assertEquals(new TabularColumn("Price", XSchema.DOUBLE), columns.get(2));

      assertEquals(List.of("ID"), products.keyColumns());
   }

   @Test
   void describeYieldsParamsKeyedByEntityQueryProperty() throws Exception {
      // Charter D2: the params key must be the ODataQuery bean property name ("entity", from
      // getEntity()/setEntity()), not the @Property(label="Entity") display text and not some
      // other name like "entitySet". If the implementation drifted to either of those, or forgot
      // to populate params at all, this fails on the exact Map equality below. The second entry
      // makes the build type the date/time columns from this schema; its key must be a bean
      // property of ODataQuery too, or the query contract rejects the described params.
      ODataCatalogSnapshot snapshot = ODataCatalog.parse(schemaNode("catalog.metadata.xml"));

      TabularDatasetSchema products = snapshot.schemasByEntitySet().get("Products");
      assertEquals(Map.of("entity", "Products", "typedBy", "metadata"), products.params());

      assertTrue(TabularUtil.getPropertyMap(ODataQuery.class).containsKey("entity"));
      assertTrue(TabularUtil.getPropertyMap(ODataQuery.class)
                    .containsKey(ODataCatalog.TYPED_BY_PARAM));
      assertEquals(ODataQuery.TYPED_BY_METADATA, products.params().get("typedBy"));
   }

   @Test
   void describeFlattensComplexTypesIntoDottedLeafColumns() throws Exception {
      ODataCatalogSnapshot snapshot =
         ODataCatalog.parse(schemaNode("catalogComplexDerived.metadata.xml"));

      TabularDatasetSchema suppliers = snapshot.schemasByEntitySet().get("Suppliers");
      assertNotNull(suppliers);
      // Address is expanded (nested Geo too) into the columns the build produces; a collection, a
      // type this schema does not declare and a type cycle each stay one column
      assertEquals(List.of(new TabularColumn("ID", XSchema.LONG),
                           new TabularColumn("Name", XSchema.STRING),
                           new TabularColumn("Address.Street", XSchema.STRING),
                           new TabularColumn("Address.Since", XSchema.TIME_INSTANT),
                           new TabularColumn("Address.Geo.Lat", XSchema.DOUBLE),
                           new TabularColumn("Address.Geo.Lon", XSchema.DOUBLE),
                           new TabularColumn("Address.Geo.Back", XSchema.STRING),
                           new TabularColumn("Tags", XSchema.STRING),
                           new TabularColumn("Previous", XSchema.STRING),
                           new TabularColumn("Foreign", XSchema.STRING),
                           new TabularColumn("Opened", XSchema.DATE)),
                   suppliers.columns());
   }

   @Test
   void describeUnionsBaseAndDerivedTypeColumns() throws Exception {
      ODataCatalogSnapshot snapshot =
         ODataCatalog.parse(schemaNode("catalogComplexDerived.metadata.xml"));

      // the entity set of the base type lists its own columns, then those of every derived type
      TabularDatasetSchema persons = snapshot.schemasByEntitySet().get("Persons");
      assertEquals(List.of(new TabularColumn("ID", XSchema.LONG),
                           new TabularColumn("Name", XSchema.STRING),
                           new TabularColumn("TotalExpense", XSchema.DOUBLE),
                           new TabularColumn("EmployeeID", XSchema.LONG),
                           new TabularColumn("HireDate", XSchema.TIME_INSTANT),
                           new TabularColumn("Salary", XSchema.DOUBLE)),
                   persons.columns());
      assertEquals(List.of("ID"), persons.keyColumns());
      // navigation properties are not columns
      assertFalse(persons.columns().stream().anyMatch(c -> c.name().equals("Detail")));
   }

   @Test
   void describeOfDerivedEntitySetIncludesBaseTypeColumnsAndKey() throws Exception {
      ODataCatalogSnapshot snapshot =
         ODataCatalog.parse(schemaNode("catalogComplexDerived.metadata.xml"));

      TabularDatasetSchema customers = snapshot.schemasByEntitySet().get("Customers");
      assertEquals(List.of(new TabularColumn("ID", XSchema.LONG),
                           new TabularColumn("Name", XSchema.STRING),
                           new TabularColumn("TotalExpense", XSchema.DOUBLE)),
                   customers.columns());
      assertEquals(List.of("ID"), customers.keyColumns());
   }

   @Test
   void describeCategoriesYieldsItsOwnColumns() throws Exception {
      ODataCatalogSnapshot snapshot = ODataCatalog.parse(schemaNode("catalog.metadata.xml"));

      TabularDatasetSchema categories = snapshot.schemasByEntitySet().get("Categories");
      assertNotNull(categories);
      assertEquals(List.of(new TabularColumn("ID", XSchema.LONG),
                           new TabularColumn("Name", XSchema.STRING)), categories.columns());
   }

   @Test
   void parseOfNullSchemaYieldsEmptyCatalogNotNull() {
      ODataCatalogSnapshot snapshot = ODataCatalog.parse(null);

      assertNotNull(snapshot.catalog());
      assertTrue(snapshot.catalog().datasets().isEmpty());
      assertTrue(snapshot.catalog().relationships().isEmpty());
      assertTrue(snapshot.schemasByEntitySet().isEmpty());
   }
}
