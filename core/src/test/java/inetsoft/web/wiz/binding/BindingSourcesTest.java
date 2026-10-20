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
package inetsoft.web.wiz.binding;

import inetsoft.uql.asset.SourceInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #78932: the source a write is establishing must reach named-group resolution. */
@Tag("core")
class BindingSourcesTest {
   @Test
   void usesTheModelSourceWhenTheAssemblyHasNoneYet() {
      SourceInfo result = BindingSources.effective(
         new SourceInfo(), BindingSources.assetSource("Customers"));

      assertEquals(SourceInfo.ASSET, result.getType());
      assertEquals("Customers", result.getSource());
   }

   @Test
   void usesTheModelSourceWhenTheAssemblySourceInfoIsNull() {
      assertEquals("Customers", BindingSources.effective(
         null, BindingSources.assetSource("Customers")).getSource());
   }

   @Test
   void keepsTheAssemblysOwnSourceWhenItIsBound() {
      SourceInfo bound = new SourceInfo(SourceInfo.ASSET, null, "Orders");

      assertSame(bound, BindingSources.effective(bound, BindingSources.assetSource("Customers")));
   }

   @Test
   void staysAsIsWhenNeitherSideHasASource() {
      SourceInfo empty = new SourceInfo();

      assertSame(empty, BindingSources.effective(empty, null));
      assertNull(BindingSources.effective(null, null));
   }
}
