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

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link FakeNamedConnectorQuery} plus a {@code Report} endpoint whose parameters carry the
 * placeholder shapes the real endpoint catalogues use ({@code {Name?:Cash|Accrual}} and its
 * look-alikes), for the closed-choice enforcement tests of bug #82261. The parameters are built
 * fresh on every call, like {@code EndpointJsonQuery.getParameters()}, with the placeholder set the
 * way {@code RestParameter.fromToken} sets it.
 *
 * <p>{@code @View} is re-declared (annotations on a class are not inherited) for the same reason as
 * on {@link FakeColumnProducingNamedConnectorQuery}.</p>
 */
@View(vertical = true, value = {
   @View1("endpoint"),
   @View1("parameters"),
   @View1("requestType"),
   @View1("suffix"),
   @View1("jsonPath"),
   @View1("expanded"),
   @View1("expandedPath"),
   @View1("lookupExpanded"),
   @View1("lookupTopLevelOnly"),
   @View1("lookupEndpoint0"),
   @View1("lookupEndpoint1"),
   @View1("additionalParameters"),
})
public class FakeChoiceEndpointQuery extends FakeNamedConnectorQuery {
   /** Parameter name to placeholder, in declaration order. */
   static final Map<String, String> REPORT_PLACEHOLDERS = new LinkedHashMap<>();

   static {
      REPORT_PLACEHOLDERS.put("Agency ID", "1|2");
      REPORT_PLACEHOLDERS.put("Accounting Method", "Cash|Accrual");
      REPORT_PLACEHOLDERS.put("Include", "snapshots|metadata|uncommittedblobs|copy");
      REPORT_PLACEHOLDERS.put("Flag", "true | false");
      REPORT_PLACEHOLDERS.put("Start Date", "yyyy-MM-dd|yyyy-MM-ddTHH:mm:ssZ");
      REPORT_PLACEHOLDERS.put("Language", "en|en-gb|es|...");
      REPORT_PLACEHOLDERS.put("Utm", "utm-[campaigns|contents|mediums|sources]");
      REPORT_PLACEHOLDERS.put("Since", "YYYY-MM-DD");
      REPORT_PLACEHOLDERS.put("Note", null);
   }

   @Override
   public String[][] getEndpoints() {
      List<String[]> all = new ArrayList<>(List.of(super.getEndpoints()));
      all.add(new String[] { "Report", "Report" });
      return all.toArray(new String[0][]);
   }

   @Override
   @Property(label = "Parameters", required = true)
   @PropertyEditor(dependsOn = "endpoint")
   public RestParameters getParameters() {
      if(!"Report".equals(getEndpoint())) {
         return super.getParameters();
      }

      RestParameters fresh = new RestParameters();
      fresh.setEndpoint(getEndpoint());
      List<RestParameter> list = new ArrayList<>();

      for(Map.Entry<String, String> e : REPORT_PLACEHOLDERS.entrySet()) {
         RestParameter rp = new RestParameter();
         rp.setName(e.getKey());
         rp.setRequired("Agency ID".equals(e.getKey()));
         rp.setPlaceholder(e.getValue());
         rp.setValue(held == null ? null : held.getKnownParameterValue(e.getKey()));
         list.add(rp);
      }

      fresh.setParameters(list);
      return fresh;
   }

   @Override
   public void setParameters(RestParameters parameters) {
      held = parameters;
      super.setParameters(parameters);
   }

   private RestParameters held;
}
