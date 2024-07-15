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
package inetsoft.uql.xmla;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.service.XHandler;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.xml.XMLAParser;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Principal;
import java.util.*;

/**
 * XMLAHandler is responsible for XMLA request.
 * All interfaces to an olap data source, including connection, meta data
 * retrieval, and query execution are processed through a handler.
 *
 * @version 10.3, 3/30/2010
 * @author InetSoft Technology Corp
 */
public class XMLAHandler extends XHandler {
   /**
    * Execute the query.
    * @param query the specified query to be executed.
    * @param params parameters for query.
    * @param user the user who call the execution of this query.
    * @param visitor Data cache vistor.
    * @return the result as a hierarchical tree.
    * @throws Exception
    */
   @Override
   public XNode execute(XQuery query, VariableTable params,
                        Principal user, DataCacheVisitor visitor) throws Exception
   {
      return execute((XMLAQuery) query, params, user, visitor);
   }

   /**
    * Connect to the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    * @throws Exception
    */
   @Override
   public void connect(XDataSource datasource, VariableTable params)
      throws Exception
   {
      this.datasource = (XMLADataSource) datasource;
      this.params = params;
   }

   /**
    * Test the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    * @throws Exception
    */
   @Override
   public void testDataSource(XDataSource datasource, VariableTable params)
      throws Exception
   {
      connect(datasource, params);
      String[] catalogs = getCatalogs(
         ((XMLADataSource) datasource).getCatalogName());

      // make sure we got exactly one catalog back
      if(catalogs == null || catalogs.length != 1) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "designer.qb.jdbc.failedConnectDB", datasource.getFullName()));
      }
   }

   /**
    * Build the meta data of this data source as an XNode tree. This
    * method will rebuild the meta data tree everytime it's called.
    * The meta data should be cached by the caller.
    * @param mtype meta data type, defined in each data source.
    * @return the root node of the meta data tree.
    * @throws Exception
    */
   @Override
   public XNode getMetaData(XNode mtype) throws Exception {
      XNode root = new XNode("metadata");
      root.setValue(getCubes());

      return root;
   }

   /**
    * Close the data source connection.
    * @throws Exception
    */
   @Override
   public void close() throws Exception {
      // do nothing
   }

   /**
    * Retrieve a list of catalog names for the specified datasource.
    * @return String array of catalog names.
    * @throws Exception
    */
   public String[] getCatalogs() throws Exception {
      return getCatalogs(null);
   }

   /**
    * Retrieve cubes within the connector's domain.
    * @return an XNode containing the Cubes.
    * @throws Exception
    */
   private Collection getCubes() throws Exception {
      String restrictions = "<CATALOG_NAME>" + datasource.getCatalogName() +
         "</CATALOG_NAME>";
      Collection cubes = discover(XMLAUtil.CUBES_REQUEST, restrictions);
      Iterator it = cubes.iterator();

      while(it.hasNext()) {
         Cube cube = (Cube) it.next();
         cube.setDimensions(getDimensions(cube));
         cube.setMeasures(getMeasures(cube.getName()));
      }

      return cubes;
   }

   /**
    * Retrieve a list of dimensions for the cube within the connector's domain.
    * @param cube the specified cube.
    * @return a Collection containing the Dimensions in the cube.
    * @throws Exception
    */
   private Collection getDimensions(Cube cube) throws Exception {
      String cubeName = cube.getName();
      String restrictions = "<CATALOG_NAME>" + datasource.getCatalogName() +
         "</CATALOG_NAME> <CUBE_NAME>" + cubeName + "</CUBE_NAME>";
      Collection dims = discover(XMLAUtil.DIMENSIONS_REQUEST, restrictions);
      Iterator it = dims.iterator();
      List dimensions = new ArrayList();

      while(it.hasNext()) {
         Dimension dim = (Dimension) it.next();
         List hierarchies = (List) getHierarchies(dim.getUniqueName(), cubeName);
         Iterator hit = hierarchies.iterator();

         while(hit.hasNext()) {
            String[] hierarchy = (String[]) hit.next();
            int origin = hierarchy[0] == null ? 0 : Integer.parseInt(hierarchy[0]);
            HierDimension hdim = new HierDimension();
            hdim.setDimensionName(dim.getDimensionName());
            hdim.setUniqueName(dim.getUniqueName());
            hdim.setCaption(dim.getCaption());
            hdim.setType(dim.getType());
            hdim.setHierarchyName(hierarchy[1]);
            hdim.setHierarchyUniqueName(hierarchy[2]);
            hdim.setParentCaption(dim.getCaption());
            hdim.setHierCaption(hierarchy[3]);
            hdim.setUserDefined((origin & MD_USER_DEFINED) != 0);
            hdim.setLevels(getLevels(hdim.getUniqueName(), hierarchy[2], cubeName));
            dimensions.add(hdim);
         }
      }

      return dimensions;
   }

   /**
    * Retrieve a list of measures for the cube within the connector's domain.
    * @param cubeName the specified cube name.
    * @return a Collection containing the CubeMembers representing
    * measures for the cube.
    * @throws Exception
   */
   private Collection getMeasures(String cubeName) throws Exception {
      String restrictions = "<CATALOG_NAME>" + datasource.getCatalogName() +
         "</CATALOG_NAME> <CUBE_NAME>" + cubeName + "</CUBE_NAME>";

      return discover(XMLAUtil.MEASURES_REQUEST, restrictions);
   }

   /**
    * Retrieve a list of hierarchiess of a dimension for the cube within
    * the connector's domain.
    * @param dimName dimension unique name.
    * @param cubeName cube name.
    * @return a Collection containing the CubeMembers representing
    * levels of a dimension for the cube.
    * @throws Exception
    */
   private Collection getHierarchies(String dimName, String cubeName)
                                     throws Exception {
      String restrictions = "<CATALOG_NAME>" + datasource.getCatalogName() +
         "</CATALOG_NAME> <CUBE_NAME>" + cubeName + "</CUBE_NAME>" +
         " <DIMENSION_UNIQUE_NAME>" + dimName + "</DIMENSION_UNIQUE_NAME>";

      return discover(XMLAUtil.HIERARCHIES_REQUEST, restrictions);
   }

   /**
    * Retrieve a list of levels of a dimension for the cube within
    * the connector's domain.
    * @param dimName dimension unique name.
    * @param hierarchyName the hierarchy unique name.
    * @param cubeName cube name.
    * @return a Collection containing the CubeMembers representing
    * levels of a dimension for the cube.
    * @throws Exception
    */
   private Collection getLevels(String dimName, String hierarchyName,
                                String cubeName) throws Exception {
      String restrictions = "<CATALOG_NAME>" + datasource.getCatalogName() +
         "</CATALOG_NAME> <CUBE_NAME>" + cubeName + "</CUBE_NAME>" +
         " <DIMENSION_UNIQUE_NAME>" + dimName + "</DIMENSION_UNIQUE_NAME>";

      if(hierarchyName != null) {
         restrictions += " <HIERARCHY_UNIQUE_NAME>" + hierarchyName +
            "</HIERARCHY_UNIQUE_NAME>";
      }

      return discover(XMLAUtil.LEVELS_REQUEST, restrictions);
   }

   /**
    * Retrieve the list of members of a level in a dimension for the cube within
    * the connector's domain.
    */
   ArrayList<MemberObject> getMembers(XMLAQuery query, Principal user)
      throws Exception
   {
      XMLADataSource ds = (XMLADataSource) query.getDataSource();
      String datasource = ds.getFullName();
      String cubeName = query.getCube();
      Dimension dim =
         XMLAUtil.getDimension(query, cubeName, query.getMemberRef(0));

      if(dim == null) {
         return null;
      }

      String dimName = dim.getUniqueName();
      StringBuilder buffer = new StringBuilder();
      buffer.append("<CATALOG_NAME>");
      buffer.append(ds.getCatalogName());
      buffer.append("</CATALOG_NAME> <CUBE_NAME>");
      buffer.append(cubeName);
      buffer.append("</CUBE_NAME> <DIMENSION_UNIQUE_NAME>");
      buffer.append(dimName);
      buffer.append("</DIMENSION_UNIQUE_NAME>");

      if(dim instanceof HierDimension) {
         buffer.append("<HIERARCHY_UNIQUE_NAME>");
         buffer.append(((HierDimension) dim).getHierarchyUniqueName());
         buffer.append("</HIERARCHY_UNIQUE_NAME>");
      }

      if(!"true".equals(query.getProperty("IgnoreLevel"))) {
         DimMember level = XMLAUtil.getLevel(
            query, cubeName, query.getMemberRef(0));
         buffer.append("<LEVEL_UNIQUE_NAME>");
         buffer.append(level.getUniqueName());
         buffer.append("</LEVEL_UNIQUE_NAME>");
      }

      String restrictions = buffer.toString();
      return discover(XMLAUtil.MDSCHEMA_MEMBERS, restrictions, ds, null, query,
                      user);
   }

   /**
    * Retrieve a list of catalogs, if catalogName == null
    * all catalogs will be returned.
    * @param catalogName catalog name.
    * @return String array of catalog names.
    * @throws Exception
    */
   private String[] getCatalogs(String catalogName) throws Exception {
      String restrictions = catalogName == null ? "" :
         "<CATALOG_NAME>" + catalogName + "</CATALOG_NAME>";
      Collection c = discover(XMLAUtil.CATALOGS_REQUEST, restrictions);
      String[] catalogs = new String[c.size()];
      c.toArray(catalogs);

      return catalogs;
   }

   /**
    * Discover metadata.
    */
   private ArrayList discover(String reqType, String restrictions)
      throws Exception
   {
      return discover(reqType, restrictions, datasource, params, null, null);
   }

   /**
    * Discover metadata.
    */
   private static ArrayList discover(String reqType, String restrictions,
      XMLADataSource ds, VariableTable params, XMLAQuery query,
      Principal user) throws Exception
   {
      String action = "\"urn:schemas-microsoft-com:xml-analysis:Discover\"";
      StringBuilder body = new StringBuilder();
      body.append(
         " <Discover xmlns=\"urn:schemas-microsoft-com:xml-analysis\">");
      body.append("<RequestType>");
      body.append(reqType);
      body.append("</RequestType>");
      body.append("<Restrictions><RestrictionList>");
      body.append(restrictions);
      body.append("</RestrictionList></Restrictions>");
      body.append("<Properties><PropertyList>");
      body.append("<DataSourceInfo>");
      body.append(ds.getDatasourceInfo());
      body.append("</DataSourceInfo>");

      if(restrictions != null && restrictions.trim().length() > 0) {
         body.append("<Catalog>");
         body.append(ds.getCatalogName());
         body.append("</Catalog>");
      }

      body.append("<Format>Tabular</Format>");
      body.append("</PropertyList></Properties>");
      body.append("</Discover>");

      XMLAParser parser = callSoap(body.toString(), action, ds, params, user);
      ArrayList list = new ArrayList();
      parseXML(reqType, list, parser);

      Collections.sort(list, new Comparator() {
         @Override
         public int compare(Object o1, Object o2) {
            if(o1 instanceof DimMember && o2 instanceof DimMember) {
               return ((DimMember) o1).getNumber() -
                  ((DimMember) o2).getNumber();
            }

            return 0;
         }

         public boolean equals(Object obj) {
            return obj instanceof Comparator;
         }
      });

      return list;
   }

   /**
    * Parse xml content to discovered object list.
    */
   private static void parseXML(String reqType, List list, XMLAParser parser)
      throws Exception
   {
      int state = XMLAParser.END_DOCUMENT;
      DiscoverHandler handler = new DiscoverHandler();
      handler.reqType = reqType;
      handler.objList = list;
      handler.setXMLPParser(parser);

      while((state = parser.next()) != XMLAParser.END_DOCUMENT) {
         switch(state) {
         case XMLAParser.START_TAG:
            handler.startElement();
            break;
         case XMLAParser.TEXT:
            handler.characters();
            break;
         case XMLAParser.END_TAG:
            handler.endElement();
            break;
         }
      }
   }

   /**
    * Execute drill through.
    */
   private XNode executeDrillThrough(XMLAQuery query, VariableTable params,
                                     Principal user) throws Exception
   {
      String[] mdxs = query.getMDXDefinitions();
      UnionTableNode union = new UnionTableNode();

      for(int i = 0; i < mdxs.length; i++) {
         try {
            // @by davidd, 2011-11-11 v11.3, Drill requests can be cancelled.
            QueryManager queryMgr =
                  (QueryManager) query.getProperty("queryManager");
            CancelHandler cancelHandler = new CancelHandler();

            if(queryMgr != null) {
               queryMgr.addPending(cancelHandler);
            }

            // continue to execute even one of its drill through failed
            union.appendTable(
                  execute(mdxs[i], params, user, "Tabular", cancelHandler));
         }
         catch(Exception ex) {
            LOG.error("Failed to execute drill through", ex);
         }
      }

      union.complete();

      if(union.isRewindable()) {
         union.rewind();
         union.next();
      }

      return union;
   }

   /**
    * Cache all levels of a certain dimension.
    */
   private void cacheLevels(XMLAQuery query, VariableTable params,
                            Principal user) throws Exception {
      Collection<Dimension> dims = query.getTouchedDimensions();
      Iterator<Dimension> it = dims.iterator();

      while(it.hasNext()) {
         Dimension dim = it.next();
         int levelCount = query.getLevelCount(dim);

         for(int i = 0; i < levelCount; i++) {
            DimMember level = (DimMember) dim.getLevelAt(i);
            XMLAQuery query0 = new XMLAQuery();
            query0.setDataSource(query.getDataSource());
            query0.setCube(query.getCube());
            AttributeRef attr = new AttributeRef(dim.getIdentifier(),
                                                 level.getUniqueName());
            attr.setRefType(DataRef.CUBE_DIMENSION);
            ColumnRef col = new ColumnRef(attr);
            col.setCaption(dim.getCaption() + "." + level.getCaption());
            query0.addMemberRef(col);
            query0.setProperty("noEmpty", "false");
            execute(query0, params, user, null, true);
         }
      }
   }

   /**
    * Get proper execute handler.
    */
   ExecuteHandler getExecuteHandler(XMLAQuery query) {
      XCube cube = query.getRuntimeCube();

      if(cube == null) {
         cube = XMLAUtil.getCube(query.getDataSource().getFullName(),
            query.getCube());
      }

      ExecuteHandler handler = null;

      if(XCube.SQLSERVER.equals(cube.getType())) {
         handler = XMLAUtil.hasCalcualtedMember(query) ?
            new SQLExecuteHandler2(this) : new SQLExecuteHandler(this);
      }
      else if(XCube.ESSBASE.equals(cube.getType())) {
         handler = new EssbaseExecuteHandler(this);
      }
      else if(XCube.MONDRIAN.equals(cube.getType())) {
         handler = new MondrianExecuteHandler(this);
      }
      else {
         if(!XMLAUtil.isDisplayFullCaption()) {
            handler = new ExecuteHandler(this) {
               /**
                * Fix caption and full caption.
                */
               @Override
               protected void fixCaption(MemberObject mobj, String caption) {
                  if(!Tool.equals(mobj.caption, caption)) {
                     mobj.caption = caption;
                     mobj.fullCaption = caption;
                  }
               }
            };
         }
         else {
            handler = new ExecuteHandler(this);
         }
      }

      handler.query = query;
      return handler;
   }

   /**
    * Execute mdx.
    */
   private XNode execute(XMLAQuery query, VariableTable params,
                         Principal user, DataCacheVisitor visitor)
      throws Exception
   {
      return execute(query, params, user, visitor, false);
   }

   /**
    * Execute mdx.
    */
   private XNode execute(XMLAQuery query, VariableTable params,
                         Principal user, DataCacheVisitor visitor,
                         boolean existOnly)
      throws Exception
   {
      if("true".equals(SreeEnv.getProperty("olap.security.enabled"))) {
         query.setProperty("RUN_USER", user);
      }

      if(isQueryCached(query, params, user, visitor)) {
         return new XNode();
      }

      if("true".equals(query.getProperty("showDetail"))) {
         return executeDrillThrough(query, params, user);
      }

      ExecuteHandler handler = getExecuteHandler(query);
      String cacheKey = XMLAUtil.getCacheKey(query);

      if(cacheKey != null) {
         // only need to check the query cache is existed
         if(XMLAUtil.findCachedResult(cacheKey) && existOnly) {
            return new XNode();
         }
         else {
            return handler.getCachedData(user, false);
         }
      }

      // get each dimension members for future sorting
      cacheLevels(query, params, user);

      // @by davidd, 2011-11-02 v11.3, XMLA requests can be cancelled.
      QueryManager queryMgr = (QueryManager) query.getProperty("queryManager");
      CancelHandler cancelHandler = new CancelHandler();

      if(queryMgr != null) {
         queryMgr.addPending(cancelHandler);
      }

      XMLAParser pparser = execute(query.getMDXDefinition(), params, user, cancelHandler);
      int state = XMLAParser.END_DOCUMENT;
      handler.pparser = pparser;

      try {
         while((state = pparser.next()) != XMLAParser.END_DOCUMENT && !cancelHandler.isCancelled())
         {
           switch(state) {
           case XMLAParser.START_TAG:
              handler.startElement();
              break;
           case XMLAParser.TEXT:
              handler.characters();
              break;
           case XMLAParser.END_TAG:
              handler.endElement();
              break;
           }
         }
      }
      catch(Exception e) {
         // @by davidd, Ignore exceptions if the request was cancelled.
         if(!cancelHandler.isCancelled()) {
            throw e;
         }
      }
      finally {
         // @by davidd, An XMLA cancel operation is non-trivial (requiring a
         // server discovery request), therefore we should remove a completed
         // query from the manager after successful or unsuccessful execution.
         if(queryMgr != null) {
            queryMgr.removePending(cancelHandler);
         }
      }

      // @by davidd, TODO If the result was parsed successfully, then cache it
      //             even if the request for data has been cancelled.
      if(cancelHandler.isCancelled()) {
         throw new CancelledException("XMLA Request Cancelled: "
               + cancelHandler.getStatement());
      }

      XMLATableNode table = new XMLATableNode(handler);
      table.groupAll();

      return table;
   }

   /**
    * Execute the element.
    * @param statement mdx statement.
    * @param params parameters.
    * @param user the user who executes this statement.
    * @param cancelHandler the cancelHandler to configure for this execution
    * @return  the XMLAParser configured to read the XMLA response
    * @throws IOException or XMLPParserException (see XMLPParser)
    */
   private XMLAParser execute(String statement, VariableTable params,
      Principal user, CancelHandler cancelHandler)
         throws IOException, Exception
   {
      return execute(statement, params, user, "Multidimensional",cancelHandler);
   }

   /**
    * Execute the element with a cancel handler.
    *
    * @param statement mdx statement.
    * @param params parameters.
    * @param user the user who executes this statement.
    * @param format the return result format.
    * @param cancelHandler the cancelHandler to configure for this execution
    * @return  the XMLAParser configured to read the XMLA response
    * @throws IOException or XMLPParserException (see XMLPParser)
    */
   private XMLAParser execute(String statement, VariableTable params,
       Principal user, String format, CancelHandler cancelHandler)
          throws IOException, Exception
   {
      String action = "\"urn:schemas-microsoft-com:xml-analysis:Execute\"";
      StringBuilder body = new StringBuilder();

      if(cancelHandler != null) {
         cancelHandler.init(statement, datasource, params, user);
      }

      body.append("<Execute xmlns=\"urn:schemas-microsoft-com:xml-analysis\">");
      body.append("<Command><Statement>");
      body.append(statement);
      body.append("</Statement></Command>");
      body.append("<Properties><PropertyList>");
      body.append("<DataSourceInfo>");
      body.append(datasource.getDatasourceInfo());
      body.append("</DataSourceInfo>");
      body.append("<Format>");
      body.append(format);
      body.append("</Format><Catalog>");
      body.append(datasource.getCatalogName());
      body.append("</Catalog>");
      body.append("<AxisFormat>TupleFormat</AxisFormat>");
      body.append("</PropertyList></Properties>");
      body.append("</Execute>");
      LOG.debug(statement);

      return callSoap(body.toString(), action, datasource, params, user,
            cancelHandler);
   }

   /**
    * Call soap service.
    *
    * @param body the XMLA execute or command request
    * @param action the SOAP Action
    * @param ds the XMLA datasource
    * @param params the params
    * @param user the user

    * @return  the XMLAParser configured to read the XMLA response
    * @throws IOException or XMLPParserException (see XMLPParser)
    */
   private static XMLAParser callSoap(String body, String action,
      XMLADataSource ds, VariableTable params, Principal user)
         throws IOException, Exception
   {
      return callSoap(body, action, ds, params, user, null);
   }

   /**
    * Call soap service.
    *
    * @param body the XMLA execute or command request
    * @param action the SOAP Action
    * @param ds the XMLA datasource
    * @param params the params
    * @param user the user
    * @param cancelHandler the cancelHandler to use for this execution
    *        The cancelHandler should be fully configured by the caller.
    * @return  the XMLAParser configured to read the XMLA response
    * @throws IOException or XMLPParserException (see XMLPParser)
    */
   private static XMLAParser callSoap(String body, String action,
      XMLADataSource ds, VariableTable params, Principal user,
      CancelHandler cancelHandler)
         throws IOException, Exception
   {
      InputStream input = callSoap0(body, action, ds, params, user, cancelHandler);
      XMLAParser pparser = new XMLAParser();

      if(input == null) {
         return pparser;
      }

      pparser.setInput(input, null);

      return pparser;
   }

   /**
    * Call soap service.
    *
    * @param body the XMLA execute or command request
    * @param action the SOAP Action
    * @param ds the XMLA datasource
    * @param params the params
    * @param user the user
    * @return the SOAP response stream for the request
    */
   private static InputStream callSoap0(String body, String action,
      XMLADataSource ds, VariableTable params, Principal user)
         throws IOException
   {
      return callSoap0(body, action, ds, params, user, null);
   }

   /**
    * Call soap service.
    *
    * @param body the XMLA execute or command request
    * @param action the SOAP Action
    * @param ds the XMLA datasource
    * @param params the params
    * @param user the user
    * @param cancelHandler the cancelHandler to use for this execution
    *        The cancelHandler should be fully configured by the caller.
    * @return the SOAP response stream for the request
    */
   private static InputStream callSoap0(String body, String action,
      XMLADataSource ds, VariableTable params, Principal user,
      CancelHandler cancelHandler)
         throws IOException
   {
      String author = getAuthenticator(ds, params, user);

      // If a cancelHandler is null, then the caller can't cancel it.
      // Instantiate a temporary cancel handler for the call.
      if(cancelHandler == null) {
         cancelHandler = new CancelHandler();
      }

      HttpURLConnection conn = null;

      // Block on the cancelHandler, so that a cancel request will block
      // until we initiate the request. We don't want a cancel request to occur
      // in between checking the state of isCancelled and the sending of the
      // actual XMLA request.
      synchronized(cancelHandler) {
         if(cancelHandler.isCancelled()) {
            return null;
         }

         URL url = new URL(ds.getURL());
         conn = (HttpURLConnection) url.openConnection();
         conn.setRequestMethod("POST");
         conn.setDoOutput(true);
         conn.setDoInput(true);
         conn.setRequestProperty("Authorization", "Basic " + author);
         conn.setRequestProperty("Content-Type", "text/xml");
         conn.setRequestProperty("SOAPAction", action);
         conn.setRequestProperty("Accept", "text/xml, application/xml");
         // @by davidd, Important: Set connection to Close to prevent keep-alive
         conn.setRequestProperty("Connection", "close");
         PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(
               new BufferedOutputStream(conn.getOutputStream()), "UTF-8"),
            true);

         StringBuilder buffer = new StringBuilder();
         buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         buffer.append("<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"");
         buffer.append("http://schemas.xmlsoap.org/soap/envelope/\">");
         buffer.append("<SOAP-ENV:Body>");
         buffer.append(body);
         buffer.append("</SOAP-ENV:Body> </SOAP-ENV:Envelope>");
         writer.println(buffer.toString());
         writer.flush();
         writer.close();
      }

      if(cancelHandler.isCancelled()) {
         return null;
      }

      if("OK".equals(conn.getResponseMessage())) {
         try {
            BufferedInputStream input =
               new BufferedInputStream(conn.getInputStream());
            return input;
         }
         catch(Exception e) {
            LOG.debug("Failed to get input stream", e);
            throw new RuntimeException(Catalog.getCatalog().getString(
               "viewer.viewsheet.cube.analysisErr"));
         }
      }
      else {
         throw new RuntimeException(
            Catalog.getCatalog().getString(
               "viewer.viewsheet.cube.actionErr", conn.getResponseMessage()));
      }
   }

   /**
    * A method for debug.
    */
   private static void debugOutputCallSoapResult(BufferedInputStream input) {
      try {
         System.err.println("Soap Result Start--------");
         int c = -1;

         while((c = input.read()) != -1) {
            System.err.print((char) c);
         }

         System.err.println("");
         System.err.println("-----------End Soap Result");
      }
      catch(Exception ex) {
         // ignore it
      }
   }

   /**
    * Get authenticator.
    */
   private static String getAuthenticator(XMLADataSource ds,
      VariableTable params, Principal principal)
   {
      if("true".equals(SreeEnv.getProperty("olap.security.enabled")) &&
         principal != null)
      {
         return getTicketFromUser(principal);
      }

      return getLocalTicket(ds, params, principal);
   }

   /**
    * Get ticket from user.
    */
   private static String getTicketFromUser(Principal principal) {
      XPrincipal user = (XPrincipal) principal;
      String userName = user.getProperty("__OLAP_LOGIN_USER_NAME__");
      String password = user.getProperty("__OLAP_LOGIN_USER_PASSWORD__");

      return encode(userName + ":" + password);
   }

   /**
    * Encode ticket.
    */
   private static String encode(String ticket) {
      return ticket == null ? null :
         new String(Base64.encodeBase64(ticket.getBytes(), false));
   }

   /**
    * Get local ticket.
    */
   private static String getLocalTicket(XMLADataSource ds,
      VariableTable params, Principal principal)
   {
      String user = ds.getUser();
      String password = ds.getPassword();

      if(!isEmptyStr(user) && !isEmptyStr(password)) {
         return encode(user + ":" + password);
      }

      if(params != null) {
         try {
            String user0 = (String) params.get(
               XUtil.DB_USER_PREFIX + ds.getFullName());
            String password0 = (String) params.get(
               XUtil.DB_PASSWORD_PREFIX + ds.getFullName());

             String key = ds.getFullName() + ":" +
                (principal == null ? null : principal.getName());
             String val = user0 + ":" + password0;
             userinfo.put(key, val);

             return encode(val);
         }
         catch(Exception ex) {
         }
      }

      if(principal != null) {
         String user0 = ((XPrincipal) principal).getProperty(
            XUtil.DB_USER_PREFIX + ds.getFullName());
         String password0 = ((XPrincipal) principal).getProperty(
            XUtil.DB_PASSWORD_PREFIX + ds.getFullName());

         if(user0 != null && password0 != null) {
            String key = ds.getFullName() + ":" + principal.getName();
            String val = user0 + ":" + password0;
            userinfo.put(key, val);

            return encode(val);
         }
      }

      String key = ds.getFullName() + ":" +
         (principal == null ? null : principal.getName());
      String val = (String) userinfo.get(key);
      val = val == null ? "" : val;

      return encode(val);
   }

   /**
    * Check if is an empty string.
    */
   private static boolean isEmptyStr(String str) {
      return str == null || str.length() == 0;
   }

   /**
    * CancelHandler is responsible for knowing whether an XMLA request has been
    * cancelled and also carrying out an XMLA cancellation.
    *
    * To carry out the XMLA cancellation, the CancelHandler must be initialized.
    * {@link #init(String, XMLADataSource, VariableTable, Principal)}
    */
   public static class CancelHandler {
      /**
       * Constructor.
       */
      public CancelHandler() {

      }

      /**
       *
       * @param statement  the MDX statement to cancel
       * @param ds   the XMLA datasource to cancel the statement on
       * @param params  the params
       * @param user the user
       */
      public void init(String statement, XMLADataSource ds,
         VariableTable params, Principal user)
      {
         this.statement = statement;
         this.ds = ds;
         this.params = params;
         this.user = user;
      }

      /**
       * Determined whether the handler has been initialized for active
       * cancellation.
       * @return  true if initialized
       */
      private boolean isInitialized() {
         return statement != null && ds != null &&
               user != null && params != null;
      }

      /**
       * Sets the cancelled flag to true, and attempts to cancel the statement
       * if initialized.
       *
       * Cancellation is best-effort, and will swallow and log any exceptions.
       */
      public void cancel() {
         try {
            isCancelled = true;

            if(isInitialized()) {
               performCancellation();
            }
         }
         catch(Exception e) {
            LOG.warn("XMLA Cancel failed", e);
         }
      }

      /**
       * Try to cancel an executing XMLA request on the server. This involves
       * performing a discovery of all sessions and finding the session that
       * matches the statement represented by this cancel handler, and
       * then requesting a cancellation.
       */
      private void performCancellation() throws Exception {
         if("false".equals(SreeEnv.getProperty("olap.cancel.enabled"))) {
            return;
         }

         LOG.debug("Cancelling XMLA statement: " + statement);

         final String action =
               "\"urn:schemas-microsoft-com:xml-analysis:Execute\"";
         final String discoverBody =
               "<Discover xmlns=\"urn:schemas-microsoft-com:xml-analysis\">" +
               "<RequestType>DISCOVER_SESSIONS</RequestType><Restrictions>" +
               "<RestrictionList></RestrictionList></Restrictions>" +
               "<Properties><PropertyList></PropertyList></Properties>" +
               "</Discover>";
         InputStream input = callSoap0(discoverBody, action, ds, params, user);

         if(input == null) {
            LOG.warn("Failed session discovery. " +
                    "Unable to perform cancel operation");
            return;
         }

         processDiscover(input);
         input.close();

         if(session_connection_id == null) {
            LOG.debug("Did not find XMLA Session to Cancel");
         }
         else {
            LOG.debug("Found XMLA Session to Cancel: " +
                    session_connection_id);

            final String cancelBody =
                  "<Execute xmlns='urn:schemas-microsoft-com:xml-analysis'>" +
                  "<Command><Cancel xmlns=\"http://schemas.microsoft.com/an" +
                  "alysisservices/2003/engine\"><ConnectionID>" +
                  session_connection_id +
                  "</ConnectionID></Cancel></Command><Properties/></Execute>";
            input = callSoap0(cancelBody, action, ds, params, user);

            // @by davidd @TODO verify the cancellation response
            if(input != null) {
               input.close();
            }
         }
      }

      /**
       * Processes the DISCOVER_SESSIONS response, looking for the session that
       * corresponds to this cancel handler's statement.
       *
       * @param input   the DISCOVER_SESSIONS response
       * @throws IOException
       * @throws SAXException
       * @throws ParserConfigurationException
       */
      private void processDiscover(InputStream input)
         throws IOException, SAXException, ParserConfigurationException
      {
         Document document = Tool.parseXML(input);
         NodeList rows = document.getElementsByTagName("row");

         if(rows == null) {
            return;
         }

         String session_last_command = null;
         NodeList tempList = null;

         for(int i = 0; i < rows.getLength(); i++){
            Element element = (Element) rows.item(i);

            if(element == null) {
               return;
            }

            tempList = element.getElementsByTagName("SESSION_LAST_COMMAND");
            if(tempList.getLength() > 0) {
               session_last_command = tempList.item(0).getTextContent();
               // @by davidd, The statement has been encoded, so in order to
               // compare strings, we must also encode the session_last_command.
               session_last_command =
                  Tool.encodeHTMLAttribute(session_last_command);
            }

            // Check if statement matches.
            if(!statement.equals(session_last_command)) {
               continue;
            }

            tempList = element.getElementsByTagName("SESSION_CONNECTION_ID");
            if(tempList.getLength() > 0) {
               session_connection_id = tempList.item(0).getTextContent();
            }

            break;
         }
      }

      /**
       * Returns whether this handler has had a cancel request.
       * @return  true if cancelled, false otherwise
       */
      public boolean isCancelled() {
         return isCancelled;
      }

      /**
       * Get the statement
       * @return  the MDX statement for this cancelHandler
       */
      public String getStatement() {
         return statement;
      }

      private String statement = null;
      private XMLADataSource ds = null;
      private Principal user = null;
      private VariableTable params = null;
      private boolean isCancelled = false;
      private String session_connection_id = null;
   }

   private static final int MD_USER_DEFINED = 1;

   private static final Logger LOG =
      LoggerFactory.getLogger(XMLAHandler.class);

   private static HashMap userinfo = new HashMap();
   private XMLADataSource datasource;
   private VariableTable params;
}
