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

import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.util.credential.CredentialType;

/**
 * Minimal, REAL (non-mock) tabular data source, exactly like
 * {@code TabularHandlerTest.TestTabularDataSource} — exists so {@link TabularCatalogService} tests
 * have a real {@link TabularDataSource} instance to pass through the SPI, without depending on any
 * actual connector module.
 */
public class FakeTabularDataSource extends TabularDataSource<FakeTabularDataSource> {
   public FakeTabularDataSource() {
      super("FakeCatalog", FakeTabularDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return null;
   }
}
