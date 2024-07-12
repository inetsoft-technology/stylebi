/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.surveymonkey;

import inetsoft.test.EndpointsSource;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonRuntime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SreeHome("../../target/config")
class SurveyMonkeyQueryTest {

   @ParameterizedTest(name = "{0}")
   @EndpointsSource(dataSource = SurveyMonkeyDataSource.class, query = SurveyMonkeyQuery.class)
   @Tag("endpoints")
   void testRunQuery(@SuppressWarnings("unused") String endpoint, SurveyMonkeyQuery query) {
      RestJsonRuntime runtime = new RestJsonRuntime();
      XTableNode results = runtime.runQuery(query, new VariableTable());
      assertNotNull(results);
      int rowCount = 0;

      while(results.next()) {
         ++rowCount;
      }

      assertTrue(rowCount > 0);
   }
}

// tests on hold because of strict rate limit
/*
        {
            "endpoint": "Questions",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Locale": "",
                "Search": "",
                "Custom": ""
            }
        },
        {
            "endpoint": "Survey Folders",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Survey Languages",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Survey ID": ""
            }
        },
        {
            "endpoint": "Survey Language",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Survey ID": "",
                "Language Code": "",
                "Enabled": "",
                "Translations": ""
            }
        },
        {
            "endpoint": "Contact Lists",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Contact List",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Contact List ID": ""
            }
        },
        {
            "endpoint": "Contact List Contact",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": "",
                "Status": "",
                "Search By": "",
                "Search": ""
            }
        },
        {
            "endpoint": "Bulk Contact",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": ""
            }
        },
        {
            "endpoint": "Contacts",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Status": "",
                "Search By": "",
                "Search": ""
            }
        },
        {
            "endpoint": "Bulk Contacts",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Contact",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Contact ID": ""
            }
        },
        {
            "endpoint": "Contact Fields",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Contact Field",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Contact Field ID": ""
            }
        },
        {
            "endpoint": "Survey Collectors",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Survey ID": "",
                "Name": "",
                "Start Date": "",
                "End Date": "",
                "Include": ""
            }
        },
        {
            "endpoint": "Collector",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Collector ID": ""
            }
        },
        {
            "endpoint": "Collector Messages",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Collector ID": ""
            }
        },
        {
            "endpoint": "Collector Message",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Collector ID": "",
                "Message ID": ""
            }
        },
        {
            "endpoint": "Message Recipients",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": "",
                "Include": ""
            }
        },
        {
            "endpoint": "Recipients",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": "",
                "Include": ""
            }
        },
        {
            "endpoint": "Recipient",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": "",
                "Include": ""
            }
        },
        {
            "endpoint": "Stats",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": ""
            }
        },
        {
            "endpoint": "Message Stats",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": ""
            }
        },
        {
            "endpoint": "Survey Responses",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": ""
            }
        },
        {
            "endpoint": "Collector Responses",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": "",
                "Start Created At": "",
                "End Created At": "",
                "Start Modified At": "",
                "End Modified At": "",
                "Status": "",
                "Email": "",
                "First Name": "",
                "Last Name": "",
                "IP": "",
                "Custom": "",
                "Total Time Max": "",
                "Total Time Min": "",
                "Total Time Units": ""
            }
        },
        {
            "endpoint": "Bulk Collector Responses",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": "",
                "Simple": "",
                "Collector IDs": "",
                "Start Created At": "",
                "End Created At": "",
                "Start Modified At": "",
                "End Modified At": "",
                "Status": "",
                "Email": "",
                "First Name": "",
                "Last Name": "",
                "IP": "",
                "Custom": "",
                "Total Time Max": "",
                "Total Time Min": "",
                "Total Time Units": "",
                "Page IDs": "",
                "Question IDs": ""
            }
        },
        {
            "endpoint": "Bulk Survey Responses",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": "",
                "Simple": "",
                "Collector IDs": "",
                "Start Created At": "",
                "End Created At": "",
                "Start Modified At": "",
                "End Modified At": "",
                "Status": "",
                "Email": "",
                "First Name": "",
                "Last Name": "",
                "IP": "",
                "Custom": "",
                "Total Time Max": "",
                "Total Time Min": "",
                "Total Time Units": "",
                "Page IDs": "",
                "Question IDs": ""
            }
        },
        {
            "endpoint": "Survey Response",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": ""
            }
        },
        {
            "endpoint": "Collector Response",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Collector ID": "",
                "ID": "",
                "Survey ID": "",
                "Recipient ID": "",
                "Total Time": "",
                "Custom Value": "",
                "Edit URL": "",
                "Analyze URL": "",
                "IP Address": "",
                "Custom Variables": "",
                "Logic Path": "",
                "Metadata": "",
                "Response Status": "",
                "Page Path": "",
                "Collection Mode": "",
                "Date Created": "",
                "Date Modified": ""
            }
        },
        {
            "endpoint": "Survey Response Details",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": ""
            }
        },
        {
            "endpoint": "Collector Response Details",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": "",
                "Page IDs": "",
                "Question IDs": ""
            }
        },
        {
            "endpoint": "Survey Rollups",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID]/rollups": "",
                "Start Created At": "",
                "End Created At": "",
                "Start Modified At": "",
                "End Modified At": "",
                "Status": "",
                "Email": "",
                "First Name": "",
                "Last Name": "",
                "IP": "",
                "Custom": "",
                "Total Time Max": "",
                "Total Time Min": "",
                "Total Time Units": ""
            }
        },
        {
            "endpoint": "Survey Page Rollups",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID]/pages/{ID": "",
                "Collector IDs": "",
                "Start Created At": "",
                "End Created At": "",
                "Start Modified At": "",
                "End Modified At": "",
                "Status": "",
                "Email": "",
                "First Name": "",
                "Last Name": "",
                "IP": "",
                "Custom": "",
                "Total Time Max": "",
                "Total Time Min": "",
                "Total Time Units": ""
            }
        },
        {
            "endpoint": "Survey Question Rollups",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID]/pages/{ID": "",
                "ID": "",
                "Collector IDs": "",
                "Start Created At": "",
                "End Created At": "",
                "Start Modified At": "",
                "End Modified At": "",
                "Status": "",
                "Email": "",
                "First Name": "",
                "Last Name": "",
                "IP": "",
                "Custom": "",
                "Total Time Max": "",
                "Total Time Min": "",
                "Total Time Units": ""
            }
        },
        {
            "endpoint": "Survey Trends",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID]/trends": "",
                "Last Respondent": "",
                "Trend By": ""
            }
        },
        {
            "endpoint": "Survey Page Trends",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID]/pages/{ID": "",
                "First Respondent": "",
                "Last Respondent": "",
                "Trend By": ""
            }
        },
        {
            "endpoint": "Survey Question Trends",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID]/pages/{ID": "",
                "ID": "",
                "First Respondent": "",
                "Last Respondent": "",
                "Trend By": ""
            }
        },
        {
            "endpoint": "Webhooks",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Webhook",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": ""
            }
        },
        {
            "endpoint": "Benchmark Bundles",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Country": ""
            }
        },
        {
            "endpoint": "Benchmark Bundle Questions",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": ""
            }
        },
        {
            "endpoint": "Benchmark Bundle Benchmarks",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Bundle ID": "",
                "Question IDs": "",
                "Percentile Start": "",
                "Percentile End": ""
            }
        },
        {
            "endpoint": "Survey Question Benchmark",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": "",
                "Percentile Start": "",
                "Percentile End": ""
            }
        },
        {
            "endpoint": "Workgroups",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Workgroup",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Workgroup ID": ""
            }
        },
        {
            "endpoint": "Workgroup Members",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Workgroup ID": ""
            }
        },
        {
            "endpoint": "Workgroup Member",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Workgroup ID": "",
                "Member ID": ""
            }
        },
        {
            "endpoint": "Workgroup Shares",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Workgroup ID": "",
                "Include": ""
            }
        },
        {
            "endpoint": "Workgroup Share",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Workgroup ID": ""
            }
        },
        {
            "endpoint": "Roles",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Errors",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Error",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "ID": ""
            }
        },
        {
            "endpoint": "User Workgroups",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "User ID": "142396625"
            }
        },
        {
            "endpoint": "Shared",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "User ID": "142396625",
                "Resource Type": "",
                "Resource ID": "",
                "Include": ""
            }
        },
        {
            "endpoint": "Groups",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Groups",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Group ID": ""
            }
        },
        {
            "endpoint": "Group Members",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Group ID": ""
            }
        },
        {
            "endpoint": "Group Members",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Group ID": "",
                "Member ID": ""
            }
        },
        {
            "endpoint": "Surveys",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Include": "",
                "Title": "",
                "Start Modified At": "",
                "End Modified At": "",
                "Folder ID": ""
            }
        },
        {
            "endpoint": "Surveys",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Survey ID": ""
            }
        },
        {
            "endpoint": "Survey Details",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Survey ID": ""
            }
        },
        {
            "endpoint": "Survey Categories",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Language": ""
            }
        },
        {
            "endpoint": "Survey Template",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Language": "",
                "Category": ""
            }
        },
        {
            "endpoint": "All Languages",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {

            }
        },
        {
            "endpoint": "Survey Pages",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Survey ID": ""
            }
        },
        {
            "endpoint": "Survey Page",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "Survey ID": "",
                "Page ID": ""
            }
        },
        {
            "endpoint": "Survey Questions",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": ""
            }
        },
        {
            "endpoint": "Survey Question",
            "jsonPath": "$",
            "expandArrays": false,
            "parameters": {
                "id": ""
            }
        }
 */