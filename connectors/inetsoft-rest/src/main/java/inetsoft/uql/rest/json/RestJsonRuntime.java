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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.AbstractRestQuery;
import inetsoft.uql.rest.AbstractRestRuntime;
import inetsoft.uql.rest.RestJsonQueryRunner;
import inetsoft.uql.rest.QueryRunner;
import inetsoft.uql.rest.json.lookup.LookupService;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;

public class RestJsonRuntime extends AbstractRestRuntime {
   public RestJsonRuntime() {
      this.transformer = new JsonTransformer();
      this.parser = new HttpResponseParameterParser(transformer);
   }

   @Override
   protected QueryRunner getQueryRunner(AbstractRestQuery q) {
      final RestJsonQuery query = (RestJsonQuery) q;
      final JsonRestDataIteratorStrategyFactory factory = new JsonRestDataIteratorStrategyFactory(transformer, parser);
      final LookupService lookupService = new LookupService();
      return new RestJsonQueryRunner(query, factory, lookupService, transformer);
   }

   protected String generateMetadata(AbstractRestQuery query) {
      QueryRunner queryRunner = getQueryRunner(query);
      return queryRunner.generateMetadata();
   }

   private final JsonTransformer transformer;
   private final HttpResponseParameterParser parser;
}
