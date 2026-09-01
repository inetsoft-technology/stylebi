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
package inetsoft.uql.sharepoint;

import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;

import org.mockito.MockedStatic;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Shared plumbing for the SPI tests below. {@code SharepointOnlineRuntime.getClient(...)} is
 * {@code private static} and builds a real {@code GraphServiceClient} with no seam — per the
 * feasibility check recorded in 04-build.md, that is worked around here by intercepting the
 * static entry point {@code GraphServiceClient.builder()} itself with {@code mockStatic}, so
 * {@code getClient(...)} ends up handing production code the caller-supplied mock client without
 * any production code change.
 *
 * Real Graph SDK model/page objects are used wherever possible (plain POJOs, or a public
 * {@code (List<T>, RequestBuilder)} page constructor) — mocking is used only for the
 * request-builder/request fluent chain, which has no other way to be constructed.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class SharepointGraphTestSupport {
   private SharepointGraphTestSupport() {
   }

   /**
    * Opens (but does not close) a {@link MockedStatic} that makes every
    * {@code GraphServiceClient.builder()...buildClient()} call inside the mocked static's scope
    * return {@code client}. Callers must close the returned handle (try-with-resources) once done.
    */
   static MockedStatic<GraphServiceClient> mockClientFactory(GraphServiceClient client) {
      MockedStatic<GraphServiceClient> mockedStatic = mockStatic(GraphServiceClient.class);
      GraphServiceClient.Builder builder = mock(GraphServiceClient.Builder.class);
      when(builder.authenticationProvider(any())).thenReturn(builder);
      when(builder.buildClient()).thenReturn(client);
      mockedStatic.when(GraphServiceClient::builder).thenReturn(builder);
      return mockedStatic;
   }

   /**
    * A real {@code new SharepointOnlineDataSource()} calls {@code createCredential}, which needs a
    * live Spring application context (none exists in this test tree) — mock instead. None of the
    * SPI path under test inspects the data source beyond passing it to
    * {@code SharepointAuthenticator}'s constructor, which only stores the reference.
    */
   static SharepointOnlineDataSource fakeDataSource() {
      return mock(SharepointOnlineDataSource.class);
   }

   // ----- Graph model fixtures (plain POJOs — no mocking needed) -----

   static Site site(String id, String displayName) {
      Site site = new Site();
      site.id = id;
      site.displayName = displayName;
      return site;
   }

   static com.microsoft.graph.models.List spList(String id, String displayName) {
      com.microsoft.graph.models.List list = new com.microsoft.graph.models.List();
      list.id = id;
      list.displayName = displayName;
      return list;
   }

   static Group group(String id, String displayName) {
      Group group = new Group();
      group.id = id;
      group.displayName = displayName;
      return group;
   }

   static ColumnDefinition column(String name, Consumer<ColumnDefinition> fn) {
      ColumnDefinition c = new ColumnDefinition();
      c.name = name;
      c.displayName = name + " (display)";
      fn.accept(c);
      return c;
   }

   // ----- Graph fluent-chain stubbing -----

   static SiteRequestBuilder siteBuilder(GraphServiceClient client, String siteId) {
      SiteRequestBuilder builder = mock(SiteRequestBuilder.class);
      when(client.sites(siteId)).thenReturn(builder);
      return builder;
   }

   static void stubSiteGet(SiteRequestBuilder builder, Site site) {
      SiteRequest request = mock(SiteRequest.class);
      when(builder.buildRequest()).thenReturn(request);
      when(request.get()).thenReturn(site);
   }

   static void stubSiteGetThrows(SiteRequestBuilder builder, RuntimeException failure) {
      SiteRequest request = mock(SiteRequest.class);
      when(builder.buildRequest()).thenReturn(request);
      when(request.get()).thenThrow(failure);
   }

   static void stubChildren(SiteRequestBuilder builder, List<Site> children) {
      SiteCollectionRequestBuilder childBuilder = mock(SiteCollectionRequestBuilder.class);
      SiteCollectionRequest childRequest = mock(SiteCollectionRequest.class);
      when(builder.sites()).thenReturn(childBuilder);
      when(childBuilder.buildRequest()).thenReturn(childRequest);
      when(childRequest.get()).thenReturn(new SiteCollectionPage(children, null));
   }

   static void stubChildrenThrows(SiteRequestBuilder builder, RuntimeException failure) {
      SiteCollectionRequestBuilder childBuilder = mock(SiteCollectionRequestBuilder.class);
      SiteCollectionRequest childRequest = mock(SiteCollectionRequest.class);
      when(builder.sites()).thenReturn(childBuilder);
      when(childBuilder.buildRequest()).thenReturn(childRequest);
      when(childRequest.get()).thenThrow(failure);
   }

   static void stubLists(SiteRequestBuilder builder, List<com.microsoft.graph.models.List> lists) {
      ListCollectionRequestBuilder listsBuilder = mock(ListCollectionRequestBuilder.class);
      ListCollectionRequest listsRequest = mock(ListCollectionRequest.class);
      when(builder.lists()).thenReturn(listsBuilder);
      when(listsBuilder.buildRequest()).thenReturn(listsRequest);
      when(listsRequest.get()).thenReturn(new ListCollectionPage(lists, null));
   }

   static void stubListsThrows(SiteRequestBuilder builder, RuntimeException failure) {
      ListCollectionRequestBuilder listsBuilder = mock(ListCollectionRequestBuilder.class);
      ListCollectionRequest listsRequest = mock(ListCollectionRequest.class);
      when(builder.lists()).thenReturn(listsBuilder);
      when(listsBuilder.buildRequest()).thenReturn(listsRequest);
      when(listsRequest.get()).thenThrow(failure);
   }

   static ListRequestBuilder stubColumns(SiteRequestBuilder builder, String listId,
                                        List<ColumnDefinition> columns)
   {
      ListRequestBuilder listBuilder = mock(ListRequestBuilder.class);
      ColumnDefinitionCollectionRequestBuilder columnsBuilder =
         mock(ColumnDefinitionCollectionRequestBuilder.class);
      ColumnDefinitionCollectionRequest columnsRequest = mock(ColumnDefinitionCollectionRequest.class);
      when(builder.lists(listId)).thenReturn(listBuilder);
      when(listBuilder.columns()).thenReturn(columnsBuilder);
      when(columnsBuilder.buildRequest()).thenReturn(columnsRequest);
      when(columnsRequest.get()).thenReturn(new ColumnDefinitionCollectionPage(columns, null));
      return listBuilder;
   }

   static void stubGroups(GraphServiceClient client, List<Group> groups) {
      GroupCollectionRequestBuilder groupsBuilder = mock(GroupCollectionRequestBuilder.class);
      GroupCollectionRequest groupsRequest = mock(GroupCollectionRequest.class);
      when(client.groups()).thenReturn(groupsBuilder);
      when(groupsBuilder.buildRequest()).thenReturn(groupsRequest);
      when(groupsRequest.get()).thenReturn(new GroupCollectionPage(groups, null));
   }

   static void stubGroupsThrows(GraphServiceClient client, RuntimeException failure) {
      GroupCollectionRequestBuilder groupsBuilder = mock(GroupCollectionRequestBuilder.class);
      GroupCollectionRequest groupsRequest = mock(GroupCollectionRequest.class);
      when(client.groups()).thenReturn(groupsBuilder);
      when(groupsBuilder.buildRequest()).thenReturn(groupsRequest);
      when(groupsRequest.get()).thenThrow(failure);
   }

   static void stubGroupSite(GraphServiceClient client, String groupId, Site site) {
      GroupRequestBuilder groupBuilder = mock(GroupRequestBuilder.class);
      SiteRequestBuilder groupSiteBuilder = mock(SiteRequestBuilder.class);
      SiteRequest groupSiteRequest = mock(SiteRequest.class);
      when(client.groups(groupId)).thenReturn(groupBuilder);
      when(groupBuilder.sites("root")).thenReturn(groupSiteBuilder);
      when(groupSiteBuilder.buildRequest()).thenReturn(groupSiteRequest);
      when(groupSiteRequest.get()).thenReturn(site);
   }
}
