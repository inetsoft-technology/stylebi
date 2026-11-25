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
package inetsoft.uql.rest.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.json.SuffixTemplate;
import inetsoft.uql.rest.json.TemplateComponent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the suffix templates of the endpoints fixed for bug #81907 are well formed, using
 * the real {@link SuffixTemplate} builder. Reads endpoints.json directly, so no Spring context.
 */
class EndpointSuffixTemplatesTest {
   private static final String[][] ENDPOINTS = {
      { "hubspot", "Blog Posts" },
      { "hubspot", "Contacts Informations" },
      { "influxdb", "Installed Templates" },
      { "influxdb", "Sources" },
      { "jira", "Audit Records" },
      { "jira", "Screen Tab Fields" },
      { "linkedin", "Ad Account Users" },
      { "liveagent", "Phone numbers list" },
      { "xero", "Aged Receivables By Contact" },
      { "xero", "Contact Groups" },
      { "xero", "Quotes" },
      { "xero", "Repeating Invoices" },
      { "square", "Cash Drawer Shift Events" },
      { "insightly", "Contact File Attachments" },
      { "jive", "Direct Message Comments" },
      { "twilio", "Alerts" },
      { "adobeanalytics", "Report Suites" },
      { "box", "Search for Content" },
      { "box", "Trashed Items" },
      { "hubspot", "Line items Change Log" },
      { "pipedrive", "Deal Timeline" },
      { "quickbooks", "Transaction List By Vendor" },
      { "servicenow", "Aggregate Statistics" },
      { "servicenow", "Service Catalog Items" },
      { "pipelinecrm", "Company Custom Label Entries" },
      { "pipelinecrm", "Deal Custom Groups" },
      { "pipelinecrm", "Deal Custom Labels" },
      { "pipelinecrm", "Deal Loss Reasons" },
      { "pipelinecrm", "Deal Stages" },
      { "pipelinecrm", "Documents Tags" },
      { "pipelinecrm", "Event Categories" },
      { "pipelinecrm", "Lead Sources" },
      { "pipelinecrm", "Lead Statuses" },
      { "pipelinecrm", "Note Categories" },
      { "pipelinecrm", "Notes" },
      { "pipelinecrm", "Person Custom Groups" },
      { "pipelinecrm", "Person Custom Labels" },
      { "pipelinecrm", "Predefined Contact Tags" },
      { "pipelinecrm", "Users" },
      { "twitter", "Users by IDs" },
      { "zohocrm", "Variable Group" },
      { "pipelinecrm", "Companies" }
   };

   @Test
   void fixedEndpointSuffixesAreWellFormed() throws Exception {
      List<String> problems = new ArrayList<>();

      for(String[] e : ENDPOINTS) {
         String where = e[0] + "/" + e[1];
         String raw = suffix(e[0], e[1]);

         if(raw == null) {
            problems.add(where + ": endpoint not found");
            continue;
         }

         String bare = raw.replaceAll("[{][^}]*[}]", "V");

         if(bare.contains("?&") || bare.contains("&&") || bare.contains("//") ||
            bare.contains(" ") ||
            java.util.regex.Pattern.compile("\\s").matcher(bare).find() ||
            bare.chars().filter(c -> c == '?').count() > 1)
         {
            problems.add(where + ": malformed template " + raw);
         }

         if(bare.contains("{") || bare.contains("}")) {
            problems.add(where + ": unbalanced brace in " + raw);
         }

         if(e[0].equals("pipelinecrm") && bare.contains("?")) {
            for(String pair : bare.substring(bare.indexOf('?') + 1).split("&")) {
               String key = pair.substring(0, pair.indexOf('='));

               if(key.chars().filter(c -> c == '[').count() !=
                  key.chars().filter(c -> c == ']').count())
               {
                  problems.add(where + ": unbalanced [] in query key " + key);
               }
            }
         }

         java.util.regex.Matcher variables =
            java.util.regex.Pattern.compile("[{]([^}]*)[}]").matcher(raw);

         while(variables.find()) {
            String variable = variables.group(1);

            if(variable.contains("|") && !variable.contains(":")) {
               problems.add(where + ": option list without ':' in {" + variable + "}");
            }
         }

         if(bare.contains("?")) {
            for(String pair : bare.substring(bare.indexOf('?') + 1).split("&")) {
               if(pair.chars().filter(c -> c == '=').count() != 1) {
                  problems.add(where + ": query pair without exactly one '=': " + pair);
               }
            }
         }

         SuffixTemplate template = new SuffixTemplate(raw);
         List<TemplateComponent> components = new ArrayList<>(template.getPath());
         components.addAll(template.getQuery().values());

         for(TemplateComponent component : components) {
            if(component.isVariable()) {
               template.setVariable(component.getValue(), "1");
            }
         }

         String built = template.build();

         if(built.contains("{") || built.contains("}") || built.contains("?&") ||
            built.contains("&&") || built.contains("//") || built.contains(" ") ||
            built.chars().filter(c -> c == '?').count() > 1)
         {
            problems.add(where + ": built suffix is malformed: " + built);
         }
      }

      assertEquals(Collections.emptyList(), problems);
   }

   @Test
   void twilioAlertsUsesTheRealEndDateKey() throws Exception {
      String raw = suffix("twilio", "Alerts");
      assertTrue(raw.contains("&EndDate={"), raw);
   }

   @Test
   void asanaTagsRequiresTheWorkspace() throws Exception {
      String raw = suffix("asana", "Tags");
      assertTrue(raw.contains("workspace={Workspace}"), raw);
      assertFalse(raw.contains("{Workspace?}"), raw);
   }

   @Test
   void twitterUsersByIdsNamesTheIdsParameter() throws Exception {
      assertTrue(suffix("twitter", "Users by IDs").contains("/2/users?ids={User IDs}"));
   }

   @Test
   void zohoVariableGroupUsesTheApiPath() throws Exception {
      assertEquals("/crm/v2/settings/variable_groups/{Variable Group ID}",
                   suffix("zohocrm", "Variable Group"));
   }

   @Test
   void pipelineCrmCompaniesUsesSnakeCaseConditionKey() throws Exception {
      String raw = suffix("pipelinecrm", "Companies");
      assertTrue(raw.contains("conditions[company_name]={Company Name?}"), raw);
   }

   @Test
   void serviceNowSuffixesStartWithSlashAndUseRealStatsParameters() throws Exception {
      List<String> problems = new ArrayList<>();

      try(InputStream in = EndpointSuffixTemplatesTest.class.getResourceAsStream(
         "/inetsoft/uql/rest/datasource/servicenow/endpoints.json"))
      {
         JsonNode root = new ObjectMapper().readTree(in);
         JsonNode list = root.isArray() ? root : root.get("endpoints");

         for(JsonNode node : list) {
            String raw = node.get("suffix").asText();

            if(!raw.startsWith("/") || raw.startsWith("/api/")) {
               problems.add(node.path("name").asText() + ": bad prefix " + raw);
            }
         }
      }

      assertEquals(Collections.emptyList(), problems);

      String stats = suffix("servicenow", "Aggregate Statistics");
      assertTrue(stats.contains("&sysparm_avg_fields={"), stats);
      assertTrue(stats.contains("&sysparm_sum_fields={"), stats);
      assertTrue(!stats.contains("&sysparm_avg={") && !stats.contains("&sysparm_sum={"), stats);
   }

   private static String suffix(String dataSource, String endpoint) throws Exception {
      try(InputStream in = EndpointSuffixTemplatesTest.class.getResourceAsStream(
         "/inetsoft/uql/rest/datasource/" + dataSource + "/endpoints.json"))
      {
         JsonNode root = new ObjectMapper().readTree(in);
         JsonNode list = root.isArray() ? root : root.get("endpoints");

         for(JsonNode node : list) {
            if(endpoint.equals(node.path("name").asText())) {
               return node.get("suffix").asText();
            }
         }
      }

      return null;
   }
}
