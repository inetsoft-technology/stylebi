/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.admin.query;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;

import javax.management.openmbean.*;
import java.util.List;

@Component
@ManagedResource
public class QueryMonitorMBean {
   private final QueryService queryService;

   @Autowired()
   public QueryMonitorMBean(QueryService queryService) {
      this.queryService = queryService;
   }

   /**
    * Get the number of queries currently being executed.
    */
   @ManagedAttribute(description="Number of Queries currently being executed")
   public int getCount() {
      return queryService.getCount();
   }

   /**
    * Destroy an executing query.
    * @param id the unique identifier for the query.
    */
   @ManagedOperation(description="Destroy an existing query")
   @ManagedOperationParameters({
      @ManagedOperationParameter(name="id", description="The unique identifier for the query")})
   public void destroy(String id) throws Exception {
      queryService.destroy(id);
   }

   /**
    * Destroy some executing queries.
    * @param ids the unique identifier arrays for the query.
    */
   @ManagedOperation(description="Destroy some existing queries")
   @ManagedOperationParameters({
      @ManagedOperationParameter(name="id", description="Array of unique identifiers for each query")})
   public void destroy(String[] ids) throws Exception {
      queryService.destroy(ids);
   }

   /**
    * Get the detail infos of all excuting queries.
    * @return the query infos.
    */
   @ManagedAttribute
   public CompositeData[] getInfo() throws OpenDataException {
      String[] names = { "ID", "Thread", "Name", "Asset", "User", "Age", "Rows" };
      CompositeType itemType = new CompositeType(
         "QueryInfo", "Information about an executing query", names, names,
         new OpenType[] {
            SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
            SimpleType.STRING, SimpleType.STRING, SimpleType.STRING
         });
      List<QueryModel> queries = queryService.getQueries();
      CompositeData[] result = new CompositeData[queries.size()];

      for(int i = 0; i < queries.size(); i++) {
         QueryModel query = queries.get(i);
         result[i] = new CompositeDataSupport(itemType, names, new Object[] {
            query.id(), query.thread(), query.name(), query.asset(), query.user(), query.age(),
            query.rows()
         });
      }

      return result;
   }

   @ManagedAttribute
   public TabularData getExecutingQueries() throws OpenDataException {
      String[] names = { "Select", "ID", "Thread", "Name", "Asset", "User", "Rows", "Age" };
      CompositeType rowType = new CompositeType(
         "QueryInfo", "Information about an executing query", names, names, new OpenType[] {
         SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
         SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING
      });
      TabularType tableType = new TabularType(
         "QueryInfos", "Information about all executing queries", rowType, names);
      TabularDataSupport data = new TabularDataSupport(tableType);

      for(QueryModel query : queryService.getQueries()) {
         data.put(new CompositeDataSupport(rowType, names, new Object[] {
            "false", query.id(), query.thread(), query.name(), query.asset(), query.user(),
            query.rows(), query.age()
         }));
      }

      return data;
   }

   @ManagedAttribute
   public int getThroughput() {
      return queryService.getThroughput();
   }
}
