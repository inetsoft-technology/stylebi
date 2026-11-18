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

/**
 * A named-connector stand-in whose {@code getSuffix()} override carries no {@code @Property}, as
 * {@code QuickbooksReportsQuery}, {@code SalesforceReportsQuery} and {@code FirebaseQuery} do. The
 * {@code suffix} property is therefore absent from {@code TabularUtil.getPropertyMap}, although the
 * superclass getter is annotated (Bug #81740).
 */
public class FakeUnannotatedSuffixEndpointQuery extends FakeLegacyEndpointQuery {
   @Override
   public String getSuffix() {
      return super.getSuffix();
   }
}
