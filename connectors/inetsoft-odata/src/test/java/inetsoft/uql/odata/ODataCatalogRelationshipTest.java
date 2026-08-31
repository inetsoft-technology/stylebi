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

import inetsoft.uql.tabular.TabularRelationship;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Charter assertion B6: {@code <NavigationProperty>} relationships that carry a
 * {@code <ReferentialConstraint>} appear in the catalog; the ones that cannot be expressed as a
 * relationship are honestly dropped rather than faked with empty column lists (§7.3 of the SPI
 * design — an edge with no columns would be stored as an unoverwritable, permanently unusable
 * {@code declared} edge, which is worse than no edge).
 */
class ODataCatalogRelationshipTest {

   private Node schemaNode() throws Exception {
      try(InputStream in =
             getClass().getResourceAsStream("catalogWithRelationships.metadata.xml")) {
         Document doc = Tool.parseXML(in);
         NodeList edmx = doc.getElementsByTagName("edmx:Edmx");
         Node dataServices = Tool.getChildNodeByTagName(edmx.item(0), "edmx:DataServices");
         return Tool.getChildNodeByTagName(dataServices, "Schema");
      }
   }

   private Optional<TabularRelationship> find(List<TabularRelationship> relationships,
                                               String name)
   {
      return relationships.stream().filter(r -> r.name().equals(name)).findFirst();
   }

   @Test
   void navigationPropertyWithReferentialConstraintProducesExactlyOneRelationship()
      throws Exception
   {
      List<TabularRelationship> relationships =
         ODataCatalog.parse(schemaNode()).catalog().relationships();

      TabularRelationship rel = find(relationships, "Products_Category")
         .orElseThrow(() -> new AssertionError("Products_Category relationship not found"));

      assertEquals("Products", rel.fromDataset());
      assertEquals("Categories", rel.toDataset());
      assertEquals(List.of("CategoryID"), rel.fromColumns());
      assertEquals(List.of("ID"), rel.toColumns());
   }

   @Test
   void navigationPropertyWithNoReferentialConstraintProducesNoEdge() throws Exception {
      List<TabularRelationship> relationships =
         ODataCatalog.parse(schemaNode()).catalog().relationships();

      // Category.Products is the Partner side of Products.Category and carries no
      // <ReferentialConstraint> of its own -- it must not appear, AND must not cause
      // Products_Category to be duplicated in the reverse direction either.
      assertTrue(find(relationships, "Categories_Products").isEmpty());
      assertEquals(1, relationships.stream()
         .filter(r -> (r.fromDataset().equals("Products") && r.toDataset().equals("Categories")) ||
                      (r.fromDataset().equals("Categories") && r.toDataset().equals("Products")))
         .count(), "a Partner pair must produce exactly one edge, not two");
   }

   @Test
   void compositeReferentialConstraintProducesPairedMultiColumnLists() throws Exception {
      List<TabularRelationship> relationships =
         ODataCatalog.parse(schemaNode()).catalog().relationships();

      TabularRelationship rel = find(relationships, "OrderDetails_Order")
         .orElseThrow(() -> new AssertionError("OrderDetails_Order relationship not found"));

      assertEquals("OrderDetails", rel.fromDataset());
      assertEquals("Orders", rel.toDataset());
      assertEquals(List.of("OrderID", "OrderYear"), rel.fromColumns());
      assertEquals(List.of("ID", "Year"), rel.toColumns());
   }

   @Test
   void navigationPropertyToATypeWithNoEntitySetIsDropped() throws Exception {
      List<TabularRelationship> relationships =
         ODataCatalog.parse(schemaNode()).catalog().relationships();

      assertTrue(find(relationships, "Suppliers_PrivateNote").isEmpty());
   }

   @Test
   void containmentNavigationIsDroppedEvenWithAConstraint() throws Exception {
      List<TabularRelationship> relationships =
         ODataCatalog.parse(schemaNode()).catalog().relationships();

      assertTrue(find(relationships, "Warehouses_Bins").isEmpty());
   }

   @Test
   void exactlyTheExpressibleRelationshipsAppearAndNothingElse() throws Exception {
      List<TabularRelationship> relationships =
         ODataCatalog.parse(schemaNode()).catalog().relationships();

      assertEquals(2, relationships.size(),
         "only Products_Category and OrderDetails_Order can be expressed from this fixture");
   }
}
