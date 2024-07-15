/*
 * inetsoft-sharepoint-online - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.sharepoint;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.*;
import inetsoft.uql.tabular.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class SharepointOnlineRuntime extends TabularRuntime {
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      try {
         SharepointOnlineQuery q = (SharepointOnlineQuery) query;
         SharepointOnlineDataSource ds = (SharepointOnlineDataSource) query.getDataSource();
         GraphServiceClient client = getClient(ds, true);
         return new SharepointListTableNode(client, q.getSite(), q.getList(), q.getOutputColumns());
      }
      catch(Exception e) {
         return handleError(params, e, () -> null);
      }
   }

   @Override
   public void testDataSource(TabularDataSource dataSource, VariableTable params) {
      SharepointOnlineDataSource ds = (SharepointOnlineDataSource) dataSource;

      withClassLoader(() -> {
         GraphServiceClient client = getClient(ds, false);
         client.sites("root").buildRequest().get();
         return null;
      });
   }

   static String[][] getSites(SharepointOnlineDataSource dataSource) {
      return withClassLoader(() -> doGetSites(dataSource));
   }

   private static String[][] doGetSites(SharepointOnlineDataSource dataSource) {
      List<String[]> result = new ArrayList<>();
      GraphServiceClient client = getClient(dataSource, true);
      String rootSiteId = "root";

      try {
         Site site = client.sites("root").buildRequest().get();
         rootSiteId = getSiteId(site);
         result.add(new String[]{ site.displayName, rootSiteId });
         result.addAll(getChildSites(client, site, rootSiteId));
      }
      catch(Exception e) {
         // this could happen if the user does not have the Sites.Read.All permission
         LOG.warn("Failed to get the root site", e);
      }

      try {
         GroupCollectionPage groups = client.groups().buildRequest().get();

         while(groups != null) {
            for(Group group : groups.getCurrentPage()) {
               Site site = client.groups(group.id).sites("root").buildRequest().get();
               result.add(new String[]{ site.displayName, getSiteId(site) });
               result.addAll(getChildSites(client, site, rootSiteId));
            }

            if(groups.getNextPage() == null) {
               groups = null;
            }
            else {
               groups = groups.getNextPage().buildRequest().get();
            }
         }
      }
      catch(Exception e) {
         // this could happen if the user does not have the Group.Read.All permission
         LOG.warn("Failed to list the group sites", e);
      }

      return result.toArray(new String[0][]);
   }

   private static List<String[]> getChildSites(GraphServiceClient client, Site parent,
                                               String rootSiteId)
   {
      List<String[]> result = new ArrayList<>();
      SiteCollectionPage sites = client.sites(getSiteId(parent)).sites().buildRequest().get();

      while(sites != null) {
         for(Site site : sites.getCurrentPage()) {
            String siteId = getSiteId(site);

            if(!siteId.equals(rootSiteId)) {
               result.add(new String[]{ site.displayName, siteId });
            }
         }

         if(sites.getNextPage() == null) {
            sites = null;
         }
         else {
            sites = sites.getNextPage().buildRequest().get();
         }
      }

      return result;
   }

   static String[][] getLists(SharepointOnlineDataSource dataSource, String siteId) {
      return withClassLoader(() -> doGetLists(dataSource, siteId));
   }

   private static String[][] doGetLists(SharepointOnlineDataSource dataSource, String siteId) {
      List<String[]> result = new ArrayList<>();

      if(siteId == null || siteId.isEmpty()) {
         siteId = "root";
      }

      GraphServiceClient client = getClient(dataSource, true);
      ListCollectionPage lists = client.sites(siteId).lists().buildRequest().get();

      while(lists != null) {
         for(com.microsoft.graph.models.List list : lists.getCurrentPage()) {
            result.add(new String[]{ list.displayName, list.id });
         }

         if(lists.getNextPage() == null) {
            lists = null;
         }
         else {
            lists = lists.getNextPage().buildRequest().get();
         }
      }

      return result.toArray(new String[0][]);
   }

   static String[] getColumnNames(SharepointOnlineDataSource dataSource, String siteId,
                                  String listId)
   {
      return withClassLoader(() -> doGetColumnNames(dataSource, siteId, listId));
   }

   private static String[] doGetColumnNames(SharepointOnlineDataSource dataSource, String siteId,
                                            String listId)
   {
      if(siteId == null) {
         siteId = "root";
      }

      if(listId == null) {
         String[][] lists = doGetLists(dataSource, siteId);

         if(lists.length == 0) {
            return new String[0];
         }

         listId = lists[0][1];
      }

      return Arrays.stream(doGetListColumns(dataSource, siteId, listId))
         .map(XTypeNode::getName)
         .toArray(String[]::new);
   }

   static XTypeNode[] getListColumns(SharepointOnlineDataSource dataSource, String siteId,
                                     String listId)
   {
      return withClassLoader(() -> doGetListColumns(dataSource, siteId, listId));
   }

   private static XTypeNode[] doGetListColumns(SharepointOnlineDataSource dataSource, String siteId,
                                               String listId)
   {
      List<XTypeNode> result = new ArrayList<>();

      GraphServiceClient client = getClient(dataSource, true);
      ColumnDefinitionCollectionPage columns =
         client.sites(siteId).lists(listId).columns().buildRequest().get();

      while(columns != null) {
         for(com.microsoft.graph.models.ColumnDefinition column : columns.getCurrentPage()) {
            XTypeNode node = null;

            if(column.text != null || column.personOrGroup != null || column.choice != null) {
               node = new StringType(column.name);
            }
            else if(column.number != null) {
               if("none".equals(column.number.decimalPlaces)) {
                  node = new LongType(column.name);
               }
               else {
                  node = new DoubleType(column.name);
               }
            }
            else if(column.msgraphBoolean != null) {
               node = new BooleanType(column.name);
            }
            else if(column.dateTime != null) {
               if("dateOnly".equals(column.dateTime.format)) {
                  node = new DateType(column.name);
               }
               else {
                  node = new TimeInstantType(column.name);
               }
            }
            else if(column.currency != null) {
               node = new DoubleType(column.name);
            }

            if(node != null) {
               node.setAttribute("alias", column.displayName);
               result.add(node);
            }
         }

         if(columns.getNextPage() == null) {
            columns = null;
         }
         else {
            columns = columns.getNextPage().buildRequest().get();
         }
      }

      return result.toArray(new XTypeNode[0]);
   }

   private static String getSiteId(Site site) {
      String id = site.id;
      int index = id.indexOf(',');

      if(index >= 0) {
         id = id.substring(0, index);
      }

      return id;
   }

   private static GraphServiceClient getClient(SharepointOnlineDataSource dataSource,
                                                boolean saveTokens)
   {
      return GraphServiceClient
         .builder()
         .authenticationProvider(getAuthentication(dataSource, saveTokens))
         .buildClient();
   }

   private static IAuthenticationProvider getAuthentication(SharepointOnlineDataSource dataSource,
                                                            boolean saveTokens)
   {
      return new SharepointAuthenticator(dataSource, saveTokens);
   }

   private static <T> T withClassLoader(Supplier<T> fn) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(SharepointOnlineRuntime.class.getClassLoader());

      try {
         return fn.get();
      }
      finally {
         Thread.currentThread().setContextClassLoader(loader);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(SharepointOnlineRuntime.class);
}
