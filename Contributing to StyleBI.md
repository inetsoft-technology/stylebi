# Contributing to the Open Source Project

We recommend reading the following Github articles if you are unfamiliar with contributing to an open source project: \
\
[https://docs.github.com/en/get-started/exploring-projects-on-github/finding-ways-to-contribute-to-open-source-on-github](https://docs.github.com/en/get-started/exploring-projects-on-github/finding-ways-to-contribute-to-open-source-on-github) \
\
[https://docs.github.com/en/get-started/exploring-projects-on-github/contributing-to-a-project](https://docs.github.com/en/get-started/exploring-projects-on-github/contributing-to-a-project)


## Getting Started With This Project

Firstly, [fork the project](https://github.com/orgs/community/discussions/35849) into your own repository so that you can make changes to it without affecting the original project.

Once you are satisfied with your changes and wish to contribute back to the original project, you may [create a pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests).

The pull request will then be reviewed by our developers and accepted once they are deemed appropriate.


## Example Contribution (Creating a New Data Source)

The StyleBI application allows users to create connections to various data sources to then create data visualizations from data that is returned from queries sent to these data sources.

If a data source that you want to connect to is not available or achievable through the generic data source connections (like the REST JSON data source), you may want to create a new Data Source in the application.

In the following example, we will provide steps to get started on creating your own REST JSON Data Source:



1. Fork the project into your own repository
2. Build the project from src and verify that the project starts without issue by following the steps in the [StyleBI repository](https://github.com/inetsoft-technology/stylebi)
3. Once you are familiar with the application, you can begin to make changes to it.
   1. To perform a clean rebuild of the StyleBI application after making changes to the src code, run the following command from the stylebi directory:  \
   ` .\mvnw.cmd clean install`
   2. To run the application after rebuilding, run the following command from the stylebi directory:  \
   `.\mvnw.cmd -o spring-boot:run -pl server`
4. The first step in creating a new Data Source is to create the listing for it in the User Portal > Data tab > Data Source > New Data Source listing.
5. Create a new java package under [/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource](https://github.com/inetsoft-technology/stylebi/tree/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource) directory which will contain the relevant classes/methods for your new data source. For now, they can be skeleton classes which do not have any functionality.
   1. In the package, you will want to create a class for the DataSource, Endpoint, Endpoints, Listing, Query and Service functions which implement their respective abstract parent classes. For example, see the classes under the inetsoft.uql.rest.datasource.twitter package.
6. Each data source package obtains the icon, endpoint listings and string translations from its resource bundle. Resource bundles are kept under: [/connectors/inetsoft-rest/src/main/resources/inetsoft/uql/rest/datasource](https://github.com/inetsoft-technology/stylebi/tree/main/connectors/inetsoft-rest/src/main/resources/inetsoft/uql/rest/datasource)
7. The Bundle.properties file includes string translations for various text values in the data source.
8. The data source listing requires an icon file (named icon.svg) to be included to display in the application. This can be added to the resource folder, for example, Twitter’s resource folder at: \
   [/connectors/inetsoft-rest/src/main/resources/inetsoft/uql/rest/datasource/twitter](https://github.com/inetsoft-technology/stylebi/tree/main/connectors/inetsoft-rest/src/main/resources/inetsoft/uql/rest/datasource/twitter)
9. The endpoints.json file specifies the endpoints that will be selectable from the Data Source query dropdown panel. The general structure of endpoint is as follows:
   1. The “name” value of the endpoint will be the label that’s created in the query dropdown
   2. The “suffix” value is the suffix that will be appended to the base API URL
      1. Parameters should be surrounded by curly braces and optional parameters should be followed by a “?” within the curly braces.
   3. If the endpoint has multiple pagination methods for separate endpoints, you may specify a “pageType” value which indicates method of pagination. See [/connectors/inetsoft-rest/src/main/resources/inetsoft/uql/rest/datasource/linkedin/endpoints.json](https://github.com/inetsoft-technology/stylebi/tree/main/connectors/inetsoft-rest/src/main/resources/inetsoft/uql/rest/datasource/linkedin/endpoints.json) for reference
10. Once the new REST data source package has been created, a new listing instance should be added to the [RestDataSourceListingService class](https://github.com/inetsoft-technology/stylebi/tree/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/listing/RestDataSourceListingService.java) 
11. To add functionality to the data source, we can begin to complete the classes created in Step 5.
12. Generally, the [Datasource]Endpoint.java, [Datasource]Endpoints.java, [Datasource]Service.java class and [Datasource]Listing.java classes will not require additional functionality outside of extending classes and constructor methods. See the [Github package classes](https://github.com/inetsoft-technology/stylebi/tree/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/github) for reference.
13. The [Datasource]DataSource.java class defines the data source creation panel as well as defining the connection details. For example classes of specific authentication methods, see the listing below:
    1. For an example of OAuth Client Id/Secret, see the [TwitterDataSource.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/twitter/TwitterDataSource.java) class
    2. For an example of an API Token data source, see the [ActiveCampaignDataSource.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/activecampaign/ActiveCampaignDataSource.java) class
    3. For an example of an Access Token data source, see the [ZendeskSellDataSource.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/zendesksell/ZendeskSellDataSource.java) class
    4. For an example of an API Key data source, see the [CampaignMonitorDataSource.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/campaignmonitor/CampaignMonitorDataSource.java) class
    5. For an example of User/Password data source, see the [ServiceNowDataSource.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/ServiceNow/ServiceNowDataSource.java) class
14. The getURL() method in the DataSource class should return the base URL for the data source connection.
15. The getSuffix() method in the DataSource class should return the suffix that will be appended to the base URL to verify that the data source has connected properly from the User Portal > Data > Data Source tab.
16. The [Datasource]Query.java class defines the Query panel for the data source when creating a new query in the Visual Composer. The updatePagination method will define the pagination method for the data source endpoints, see below for examples of Pagination Types:
    1. For an example of “total count and offset” pagination, see the [BoxQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/box/BoxQuery.java) class. [Box documentation.](https://developer.box.com/guides/api-calls/pagination/offset-based/)
    2. For an example of “total count and page” pagination, see the [FreshsalesQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/freshsales/FreshsalesQuery.java) class. [Freshsales documentation.](https://developer.freshsales.io/api/#pagination)
    3. For an example of “link iteration” pagination, see the [ZendeskQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/zendesk/ZendeskQuery.java) class. [Zendesk documentation.](https://developer.freshsales.io/api/#pagination)
    4. For an example of “page” pagination, see the [ChargifyQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/chargify/ChargifyQuery.java) class. [Chargify documentation.](https://developers.maxio.com/http/getting-started/about-the-api/list-operations#pagination)
    5. For an example of “page count” pagination, see the [CampaignMonitorQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/campaignmonitor/CampaignMonitorQuery.java) class. [Campaign Monitor documentation.](https://www.campaignmonitor.com/api/v3-3/campaigns/#campaign-recipients)
    6. For an example of “offset” pagination, see the [YouTubeAnalyticsQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/youtubeanalytics/YouTubeAnalyticsQuery.java) class. [Youtube Analytics documentation.](https://developers.google.com/youtube/analytics/reference/reports/query)
    7. For an example of “iteration” pagination, see the [AirtableQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/airtable/AirtableQuery.java) class. [Airtable documentation.](https://airtable.com/developers/web/api/list-records#response-offset)
    8. For an example of “GraphQL cursor” pagination, see the [ShopifyQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/shopify/ShopifyQuery.java) class. [Shopify documentation.](https://shopify.dev/docs/api/usage/pagination-graphql)
    9. For an example of “GraphQL” pagination, see the [GraphQLQuery.java](https://github.com/inetsoft-technology/stylebi/blob/main/connectors/inetsoft-rest/src/main/java/inetsoft/uql/rest/datasource/graphql/GraphQLQuery.java) class.
17. Once the [Datasource]Query.java and [Datasource]Datasource classes have added their required functionality, perform a clean rebuild of the source to test the new data source: \
    ` .\mvnw.cmd clean install`
