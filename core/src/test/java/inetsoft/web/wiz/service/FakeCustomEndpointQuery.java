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

import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.RestParameter;

import java.util.Arrays;

/**
 * {@link FakeNamedConnectorQuery} plus the {@code CUSTOM} endpoint every real
 * {@code EndpointJsonQuery} adds to its endpoint list, with the same suffix behaviour: a
 * {@code CUSTOM} endpoint's suffix is its {@code customEndpt} (an {@code IllegalArgumentException}
 * when that was never set) with the additional parameters appended as a query string.
 */
public class FakeCustomEndpointQuery extends FakeNamedConnectorQuery {
   @Override
   public String[][] getEndpoints() {
      String[][] base = super.getEndpoints();
      String[][] all = Arrays.copyOf(base, base.length + 1);
      all[base.length] = new String[] { "Custom Endpoint", "CUSTOM" };
      return all;
   }

   @Property(label = "Custom Suffix")
   public String getCustomEndpt() {
      return customEndpt;
   }

   public void setCustomEndpt(String customEndpt) {
      this.customEndpt = customEndpt;
   }

   @Override
   @Property(label = "Suffix")
   public String getSuffix() {
      if(!"CUSTOM".equals(getEndpoint())) {
         return withAdditionalParameters(super.getSuffix());
      }

      if(customEndpt == null || customEndpt.isEmpty()) {
         throw new IllegalArgumentException("Invalid suffix template: " + customEndpt);
      }

      return withAdditionalParameters(customEndpt);
   }

   private String withAdditionalParameters(String suffix) {
      HttpParameter[] extra = getAdditionalParameters();

      if(suffix == null || extra == null || extra.length == 0) {
         return suffix;
      }

      StringBuilder out = new StringBuilder(suffix);

      for(HttpParameter p : extra) {
         out.append(out.indexOf("?") < 0 ? '?' : '&').append(p.getName()).append('=')
            .append(p.getValue());
      }

      return out.toString();
   }

   private String customEndpt;
}
