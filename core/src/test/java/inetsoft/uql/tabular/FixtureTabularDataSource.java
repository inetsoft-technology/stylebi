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
package inetsoft.uql.tabular;

import inetsoft.util.credential.CredentialType;

/**
 * A data source reduced to the structures {@link TabularSchemaExtractor#extractDataSourceConfig}
 * cares about: one plain connection field, one credential field. Real {@code TabularDataSource}
 * subclasses live in connector plugins and are not on core's classpath -- same reasoning as
 * {@code TabularSchemaExtractorTest.FixtureQuery} for query-side tests. A standalone, public
 * top-level class (rather than a nested one on a single test class) so both
 * {@code TabularSchemaExtractorTest} and {@code WizTabularControllerTest} can share it, matching
 * {@code inetsoft.web.wiz.service.FakeTabularDataSource}'s equivalent role for that package.
 */
public class FixtureTabularDataSource extends TabularDataSource<FixtureTabularDataSource> {
   public FixtureTabularDataSource() {
      super("Fixture", FixtureTabularDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return null;
   }

   @Property(label = "URL")
   public String getUrl() {
      return url;
   }

   public void setUrl(String url) {
      this.url = url;
   }

   @Property(label = "API Key", password = true)
   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   private String url;
   private String apiKey;
}
