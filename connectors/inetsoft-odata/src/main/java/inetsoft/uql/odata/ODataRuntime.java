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
package inetsoft.uql.odata;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.Tool;
import inetsoft.util.Tuple;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.ODataClientBuilder;
import org.apache.olingo.client.api.communication.request.ODataBasicRequest;
import org.apache.olingo.client.api.communication.request.invoke.InvokeRequestFactory;
import org.apache.olingo.client.api.communication.request.invoke.ODataInvokeRequest;
import org.apache.olingo.client.api.communication.request.retrieve.*;
import org.apache.olingo.client.api.communication.response.ODataInvokeResponse;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.*;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.core.http.BasicAuthHttpClientFactory;
import org.apache.olingo.commons.api.edm.constants.ODataServiceVersion;
import org.apache.olingo.commons.api.format.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.net.URI;
import java.util.*;

@SuppressWarnings("WeakerAccess")
public class ODataRuntime extends TabularRuntime {
   public XTableNode runQuery(TabularQuery query0, VariableTable params) {
      ODataQuery query = (ODataQuery) query0;

      try {
         ODataDataSource ds = (ODataDataSource) query.getDataSource();
         ODataClient client = getClient(ds, true);
         String url = ds.getURL();
         URIBuilder builder;

         if(query.functionSelected()) { //  Bound Function
            if(query.getEntity() != null && !query.getEntity().isEmpty()) {
               if(query.getEntityURI() != null && query.getEntityURI().startsWith(url)) {
                  builder = client.newURIBuilder(query.getEntityURI());
               }
               else {
                  builder = client.newURIBuilder(url);
                  builder = builder.appendEntitySetSegment(query.getEntity());
               }

               if(!query.isBoundCollection()) {
                  builder.appendKeySegment(query.getBoundEntityValue());
               }
            }
            else { //  Unbound Function
               builder = client.newURIBuilder(ds.getURL());
            }

            builder = addQueryOptions(query, builder);

            builder.appendActionCallSegment(getFunctionUriSegment(query));
            URI uri = builder.build();
            boolean isV4 = isOdataV4(ds);

            if(query.getReturnType() == ODataFunction.ReturnType.ENTITYSET) {
               ODataInvokeRequest<ClientEntitySet> req;
               InvokeRequestFactory requestFactory = client.getInvokeRequestFactory();
               req = requestFactory.getFunctionInvokeRequest(uri, ClientEntitySet.class);
               setHeaders(isV4, req, true);
               req.setAccept("*/*");
               ODataInvokeResponse res = req.execute();
               return new OEntityTable((ClientEntitySet) res.getBody(), query);
            }
            else if(query.getReturnType() == ODataFunction.ReturnType.ENTITY) {
               ODataInvokeRequest<ClientEntity> req;
               InvokeRequestFactory requestFactory = client.getInvokeRequestFactory();
               req = requestFactory.getFunctionInvokeRequest(uri, ClientEntity.class);
               req.setAccept("*/*");
               setHeaders(isV4, req, true);

               ODataInvokeResponse res = req.execute();
               return new OEntityTable((ClientEntity) res.getBody(), query);
            }
            else {
               ODataInvokeRequest<ClientProperty> req;
               InvokeRequestFactory requestFactory = client.getInvokeRequestFactory();
               req = requestFactory.getFunctionInvokeRequest(uri, ClientProperty.class);
               req.setAccept("*/*");
               setHeaders(isV4, req, true);

               ODataInvokeResponse res = req.execute();
               res.getBody();
               return new OEntityTable((ClientProperty) res.getBody(), query);
            }
         }
         else {
            if(query.getEntityURI() != null && query.getEntityURI().startsWith(url)) {
               builder = client.newURIBuilder(query.getEntityURI());
            }
            else {
               builder = client.newURIBuilder(url);

               if(query.getEntity() != null) {
                  builder = builder.appendEntitySetSegment(query.getEntity());
               }
               else {
                  builder = builder.appendEntitySetSegment("");
               }
            }

            builder = addQueryOptions(query, builder);
            URI uri = builder.build();

            boolean isV4 = isOdataV4(ds);
            ODataEntitySetIteratorRequest<ClientEntitySet,ClientEntity> req;
            RetrieveRequestFactory requestFactory = client.getRetrieveRequestFactory();
            req = requestFactory.getEntitySetIteratorRequest(uri);
            setHeaders(isV4, req, true);

            ODataRetrieveResponse<ClientEntitySetIterator<ClientEntitySet, ClientEntity>>
               res = req.execute();
            ClientEntitySetIterator<ClientEntitySet, ClientEntity> iter = res.getBody();

            return new OEntityTable(iter, requestFactory, isV4, query);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to execute OData query: " + query.getName(), ex);
         Tool.addUserMessage("Failed to execute OData query: " + query.getName() +
                             " (" + ex.getMessage() + ")");
         handleError(params, ex, () -> null);
      }

      return null;
   }

   public void testDataSource(TabularDataSource ds0, VariableTable params) {
      ODataDataSource ds = (ODataDataSource) ds0;
      getEntities(ds, false);
   }

   static boolean isOdataV4(ODataDataSource ds) {
      if(ds.getVersion() != null) {
         return ds.getVersion().startsWith("4");
      }

      Document metadata = getMetaDataDocument(ds);

      if(metadata == null) {
         return false;
      }

      NodeList list = metadata.getElementsByTagName("edmx:Edmx");

      if(list != null && list.getLength() > 0) {
         Node metadataNode = list.item(0);
         NamedNodeMap metadataAttributes = metadataNode.getAttributes();

         //odata v4 is required to have the version number, otherwise it can be anything
         Node versionNode = metadataAttributes.getNamedItem("Version");
         String version = versionNode != null ? versionNode.getNodeValue() : "Less than 4.0";

         ds.setODataVersion(version);
         return version.startsWith("4");
      }
      else {
         return false;
      }
   }

   // must change content/accept type to a mimetype compatible with previous versions
   // and change the odata version so that the server will downgrade to v2 if it is v3.
   // Due to problems with the JSON deserializer, responses cannot always be read back in version2
   // so the format is set to atom/xml
   static void setHeaders(boolean isV4, ODataBasicRequest<?> req, boolean atom) {
      if(!isV4) {
         ContentType format = !atom ? ContentType.APPLICATION_XML : ContentType.APPLICATION_ATOM_XML;
         req.addCustomHeader("MinDataServiceVersion", ODataServiceVersion.V20.toString());
         req.addCustomHeader("MaxDataServiceVersion", ODataServiceVersion.V20.toString());
         req.addCustomHeader("DataServiceVersion", ODataServiceVersion.V20.toString());
         req.setFormat(format);
         req.setContentType(format.toString());
         req.setAccept(format.toString());
      }
      else {
         req.setAccept("*/*");
      }
   }

   static Map<String, URI> getEntities(ODataDataSource ds, boolean saveTokens) {
      String url = ds.getURL();
      ODataClient client = getClient(ds, saveTokens);
      ODataServiceDocumentRequest req =
         client.getRetrieveRequestFactory().getServiceDocumentRequest(url);

      boolean isV4 = isOdataV4(ds);
      setHeaders(isV4, req, false);

      ODataRetrieveResponse<ClientServiceDocument> res = req.execute();
      ClientServiceDocument doc = res.getBody();

      return doc.getEntitySets();
   }

   static ODataClient getClient(ODataDataSource ds, boolean saveTokens) {
      ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread()
         .setContextClassLoader(ODataRuntime.class.getClassLoader());

      try {
         ODataClient client = ODataClientBuilder.createClient();

         if(isUsingBasicAuth(ds)) {
            client.getConfiguration().setHttpClientFactory(
               new BasicAuthHttpClientFactory(ds.getUser(), ds.getPassword()));
         }
         else if(isUsingPasswordGrant(ds)) {
            client.getConfiguration().setHttpClientFactory(
               new OAuthPasswordGrantClientFactory(ds, saveTokens));
         }
         else if(ds.getAccessToken() != null) {
            client.getConfiguration()
               .setHttpClientFactory(new ODataHttpClientFactory(ds, saveTokens));
         }

         return client;
      }
      finally {
         Thread.currentThread().setContextClassLoader(oldLoader);
      }
   }

   static List<ODataFunction> getFunctions(Node schemaNode, String entityType) {
      List<ODataFunction> functions = new ArrayList<>();
      NodeList functionNodes = Tool.getChildNodesByTagName(schemaNode, "Function");

      for(int i = 0; i < functionNodes.getLength(); i ++) {
         Node function = functionNodes.item(i);

         String functionName = Tool.getAttribute((Element) function, "Name");
         Node returnTypeNode = Tool.getChildNodeByTagName(function,"ReturnType");
         String returnAttr = Tool.getAttribute((Element) returnTypeNode, "Type");
         ODataFunction.ReturnType returnType = ODataFunction.ReturnType.ENTITY;

         if(returnAttr.matches("Collection\\(.+\\)")) {
            returnType = ODataFunction.ReturnType.ENTITYSET;
            returnAttr = returnAttr.substring(11, returnAttr.length() - 1);
         }

         if(returnAttr.startsWith("Edm.")) {
            returnType = ODataFunction.ReturnType.PROPERTY;
         }

         ODataFunction functionModel = new ODataFunction(functionName, returnType);
         String boundEntity = "";  // Empty entity name means that it is unbound
         String isBoundAttr = Tool.getAttribute((Element) function, "IsBound");
         functionModel.setBound(isBoundAttr != null && isBoundAttr.equals("true"));

         if(functionModel.isBound() && entityType.isEmpty() ||
            !functionModel.isBound() && !entityType.isEmpty())
         {
            continue;
         }

         ArrayList<HttpParameter> parameters = new ArrayList<>();
         ArrayList<Boolean> parameterStrings = new ArrayList<>();
         NodeList parameterNodes = Tool.getChildNodesByTagName(function, "Parameter");
         boolean matchingEntityType = true;

         for(int j = 0; j < parameterNodes.getLength(); j ++) {
            Node parameter = parameterNodes.item(j);

            if(j == 0 && functionModel.isBound()) {
               String type = Tool.getAttribute((Element) parameter, "Type");
               functionModel.setBoundCollection(type.matches("Collection\\(.+\\)"));
               boundEntity = functionModel.isBoundCollection() ?
                  type.substring(11, type.length() - 1) : type;

               if(!boundEntity.equals(entityType)) {
                  matchingEntityType = false;
                  break;
               }

               if(functionModel.isBoundCollection()) {
                  continue;
               }
            }

            String paramName = Tool.getAttribute((Element) parameter, "Name");
            boolean isString = Tool.getAttribute((Element) parameter, "Type").equals("Edm.String");
            HttpParameter param = new HttpParameter();
            param.setName(paramName);
            param.setType(HttpParameter.ParameterType.QUERY);

            parameters.add(param);
            parameterStrings.add(isString);
         }

         if(!matchingEntityType) {
            continue;
         }

         functionModel.setParameters(parameters.toArray(new HttpParameter[0]));
         functionModel.setParameterTypes(parameterStrings.toArray(new Boolean[0]));
         functions.add(functionModel);
      }

      return functions;
   }

   static String getEntityType(Node schemaNode, String entitySet) {
      NodeList entitySetNodes = Tool.getChildNodesByTagName(schemaNode, "EntitySet");

      for(int i = 0; i < entitySetNodes.getLength(); i ++) {
         Node node = entitySetNodes.item(i);
         String name = Tool.getAttribute((Element) node, "Name");

         if(entitySet.equals(name)) {
            String type = Tool.getAttribute((Element) node, "EntityType");
            return type;
         }
      }

      NodeList containers = Tool.getChildNodesByTagName(schemaNode, "EntityContainer");

      if(entitySet != null) {
         for(int i = 0; i < containers.getLength(); i ++) {
            Node node = containers.item(i);
            String type = getEntityType(node, entitySet);

            if(!type.equals(entitySet)) {
               return type;
            }
         }
      }

      return entitySet;
   }

   static Document getMetaDataDocument(ODataDataSource ds) {
      try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
         String metadataUrl = ds.getURL();
         metadataUrl = metadataUrl.endsWith("/") ? metadataUrl + "%24metadata" :
            metadataUrl + "/%24metadata";

         HttpGet getRequest = new HttpGet(metadataUrl);
         //only mime type supported for getting metadata
         getRequest.setHeader("Content-Type", "application/xml");

         if(isUsingBasicAuth(ds)) {
            String credential = new String(
               Base64.encodeBase64((ds.getUser() + ":" + ds.getPassword()).getBytes()));
            getRequest.setHeader("Authorization", "Basic " + credential);
         }

         if(isUsingPasswordGrant(ds)) {
            final Tokens tokens = AuthorizationClient.refreshPasswordGrantToken(ds);

            if(tokens != null) {
               ds.updateTokens(tokens);
            }

            getRequest.setHeader("Authorization", "Bearer " + ds.getAccessToken());
         }

         try(CloseableHttpResponse httpResponse = httpClient.execute(getRequest)) {
            return Tool.parseXML(httpResponse.getEntity().getContent());
         }
      }
      catch(Exception ex) {
         LOG.warn("could not get metadata for datasource: " + ds.getName(), ex);
      }

      return null;
   }

   public static Node getSchemaNode(ODataDataSource ds) {
      NodeList nodes = ODataRuntime.getMetaDataDocument((ODataDataSource) ds)
         .getElementsByTagName("edmx:Edmx");

      if(nodes.getLength() > 0) {
         Node elem = nodes.item(0);

         if(elem != null) {
            elem = Tool.getChildNodeByTagName(elem, "edmx:DataServices");

            if(elem != null) {
               elem = Tool.getChildNodeByTagName(elem, "Schema");

               if(elem != null) {
                  return elem;
               }
            }
         }
      }

      LOG.warn("Could not get schema for datasource: " + ds.getName());
      return null;
   }

   private static boolean isUsingBasicAuth(ODataDataSource ds) {
      return ds.getUser() != null && ds.getPassword() != null &&
         (ds.getTokenUri() == null || ds.getTokenUri().isEmpty());
   }

   private static boolean isUsingPasswordGrant(ODataDataSource ds) {
      return ds.getUser() != null && ds.getPassword() != null && ds.getTokenUri() != null &&
         !ds.getTokenUri().isEmpty() &&
         (ds.getAuthorizationUri() == null || ds.getAuthorizationUri().isEmpty());
   }

   private static URIBuilder addQueryOptions(ODataQuery query, URIBuilder builder) {
      if(query.getFilter() != null && !query.getFilter().isEmpty()) {
         builder = builder.filter(query.getFilter());
      }

      if(query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
         builder = builder.orderBy(query.getOrderBy());
      }

      if(query.getSelect() != null && !query.getSelect().isEmpty()) {
         builder = builder.select(query.getSelect());
      }

      if(query.getExpand() != null && !query.getExpand().isEmpty()) {
         builder = builder.expand(query.getExpand());
      }

      if(!query.functionSelected() || query.getReturnType() == ODataFunction.ReturnType.ENTITYSET) {
         if(query.getTop() > 0) {
            builder = builder.top(query.getTop());
         }
         else if(query.getMaxRows() > 0) {
            builder = builder.top(query.getMaxRows());

            LOG.debug("Applying row limit configured in the Query " +
                         "Properties Dialog to: " + query.getMaxRows() + " rows.");
         }
      }

      if(query.getSkip() > 0) {
         builder = builder.skip(query.getSkip());
      }

      return builder;
   }

   private static String getFunctionUriSegment(ODataQuery query) {
      StringBuilder builder = new StringBuilder();
      HttpParameter[] params = query.getFunctionParameters();
      boolean isBoundFunction = query.getEntity() != null && !query.getEntity().isEmpty();

      if(isBoundFunction && query.getNameSpace() != null && !query.getNameSpace().isEmpty()) {
         builder.append(query.getNameSpace() + ".");
      }

      builder.append(query.getFunction() + "(");

      int startIndex = !isBoundFunction || query.isBoundCollection() ? 0 : 1;

      for(int i = startIndex; i < params.length; i ++) {
         String value = params[i].getValue();
         value = query.isParameterString(i) ? "'" + value + "'" : value;
         builder.append(params[i].getName() + "=" + value);

         if(i != params.length - 1) {
            builder.append(",");
         }
      }

      builder.append(")");
      return builder.toString();
   }

   private static final Logger LOG = LoggerFactory.getLogger(ODataRuntime.class.getName());
}
