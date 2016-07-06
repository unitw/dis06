package de.dis2016;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mongodb.BasicDBObject;
import com.mongodb.util.JSON;
import de.dis2016.entities.Article;
import de.dis2016.entities.Sale;
import de.dis2016.entities.Shop;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.hibernate.Session;
import org.hibernate.Transaction;

/**
 * Created by Joanna on 25.06.2015.
 */
public class MainApp {

    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new DataWarehouseModule());

        Session session = injector.getInstance(Session.class);
        session.doWork(connection -> {
            // This updates the database – should not be necessary!
            if (false) {
                Transaction tx;

                // Empty all articles
                connection.prepareStatement("DELETE FROM VSISP66.ARTICLE").execute();

                // Load from external DB
                PreparedStatement statement = connection.prepareStatement("SELECT\n" +
                        "  a.ArticleID AS articleId,\n" +
                        "  g.ProductGroupID AS groupId,\n" +
                        "  f.ProductFamilyID AS familyId,\n" +
                        "  cat.ProductCategoryID AS categoryId,\n" +
                        "  cat.name AS category,\n" +
                        "  f.name AS family,\n" +
                        "  g.name AS group,\n" +
                        "  a.name AS article,\n" +
                        "  a.preis AS price\n" +
                        "FROM DB2INST1.ArticleID AS a\n" +
                        "INNER JOIN DB2INST1.ProductGroupID AS g ON a.ProductGroupID = g.ProductGroupID\n" +
                        "INNER JOIN DB2INST1.ProductFamilyID AS f ON g.ProductFamilyID = f.ProductFamilyID\n" +
                        "INNER JOIN DB2INST1.ProductCategoryID AS cat ON f.ProductCategoryID = cat.ProductCategoryID\n");

                ResultSet result = statement.executeQuery();

                tx = session.beginTransaction();
                while (result.next()) {
                    Article article = new Article();
                    article.setArticleId(result.getInt("articleid"));
                    article.setGroupId(result.getInt("groupId"));
                    article.setFamilyId(result.getInt("familyId"));
                    article.setCategoryId(result.getInt("categoryId"));
                    article.setCategory(result.getString("category"));
                    article.setFamily(result.getString("family"));
                    article.setGroup(result.getString("group"));
                    article.setArticle(result.getString("article"));
                    article.setPrice(result.getFloat("price"));

                    session.save(article);
                }

                session.flush();
                tx.commit();

                // Empty all shops
                connection.prepareStatement("DELETE FROM VSISP66.SHOP").execute();

                // Load shops from external DB
                statement = connection.prepareStatement("SELECT \n" +
                        "  s.ShopId AS shopId,\n" +
                        "  c.StadtId AS cityId,\n" +
                        "  r.REGIONID AS regionId,\n" +
                        "  l.LANDID AS countryId,\n" +
                        "  l.name AS country,\n" +
                        "  r.name AS region,\n" +
                        "  c.name AS city,\n" +
                        "  s.name AS shopName\n" +
                        "FROM DB2INST1.ShopID AS s\n" +
                        "INNER JOIN DB2INST1.StadtID AS c ON c.StadtID = s.StadtID\n" +
                        "INNER JOIN DB2INST1.RegionID AS r ON c.RegionID = r.RegionID\n" +
                        "INNER JOIN DB2INST1.LandID AS l ON l.LandID = r.LandID");
                result = statement.executeQuery();

                tx = session.beginTransaction();
                while (result.next()) {
                    Shop shop = new Shop();
                    shop.setShopId(result.getInt("shopid"));
                    shop.setCityId(result.getInt("cityid"));
                    shop.setRegionId(result.getInt("regionid"));
                    shop.setCountryId(result.getInt("countryid"));
                    shop.setCountryName(result.getString("country"));
                    shop.setRegionName(result.getString("region"));
                    shop.setCityName(result.getString("city"));
                    shop.setShopName(result.getString("shopName"));

                    session.save(shop);
                }

                session.flush();
                tx.commit();

                // Empty all sales
                connection.prepareStatement("DELETE FROM VSISP66.SALE").execute();

                tx = session.beginTransaction();

                Pattern datePattern = Pattern.compile("^(\\d{2})\\.(\\d{2})\\.(\\d{4})$");

                // Open CSV
                try {
                    File csvData = new File(MainApp.class.getResource("sales.csv").toURI());
                    CSVParser parser = CSVParser.parse(csvData, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT.withHeader().withDelimiter(';'));
                    int i = 0;
                    for (CSVRecord record : parser) {
                        Sale sale = new Sale();
                        sale.setShopName(record.get("Shop"));
                        sale.setArticle(record.get("Artikel"));
                        sale.setAmount(Integer.parseInt(record.get("Verkauft")));
                        sale.setTurnover(Float.parseFloat(record.get("Umsatz").replace(',', '.')));

                        // Split date into y, m, d
                        String date = record.get("Datum");
                        Matcher matcher = datePattern.matcher(date);

                        if (matcher.matches()) {
                            sale.setDay(Integer.parseInt(matcher.group(1)));
                            sale.setMonth(Integer.parseInt(matcher.group(2)));
                            sale.setYear(Integer.parseInt(matcher.group(3)));
                        }

                        session.save(sale);

                        if (++i % 100 == 0) {
                            session.flush();
                            tx.commit();

                            tx = session.beginTransaction();
                        }

//                    if (i >= 500) {
//                        break;
//                    }
                    }
                } catch (URISyntaxException | IOException e) {
                    e.printStackTrace();
                }

                session.flush();
                tx.commit();

                // Insert everything into star schema
                tx = session.beginTransaction();

                connection.prepareStatement("DELETE FROM VSISP66.STAR").execute();

                connection.prepareStatement("INSERT INTO STAR (SHOPID, SALESID, REGIONID, GROUPID, FAMILYID, " +
                        "COUNTRYID, " +
                        "CITYID, CATEGORYID, ARTICLEID, AMOUNT, ARTICLE, CATEGORY, CITYNAME, COUNTRYNAME, DAY, FAMILY, " +
                        "GROUP, MONTH, PRICE, REGIONNAME, SHOPNAME, TURNOVER, YEAR)\n" +
                        "SELECT\n" +
                        "  SHOPID, SALESID, REGIONID, GROUPID, FAMILYID, COUNTRYID, CITYID, CATEGORYID, ARTICLEID, " +
                        "AMOUNT, SALE.ARTICLE, CATEGORY, CITYNAME, COUNTRYNAME, DAY, FAMILY, GROUP, MONTH, PRICE, " +
                        "REGIONNAME, SALE.SHOPNAME, TURNOVER, YEAR\n" +
                        "FROM SALE\n" +
                        "INNER JOIN SHOP AS S ON S.SHOPNAME = SALE.SHOPNAME\n" +
                        "INNER JOIN ARTICLE AS A ON A.ARTICLE = SALE.ARTICLE").execute();

                tx.commit();
            }
        });

        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setWelcomeFiles(new String[] { "index.html" });
        resourceHandler.setBaseResource(Resource.newClassPathResource("/web"));

        ContextHandler context = new ContextHandler();
        context.setContextPath("/data");
        context.setResourceBase(".");
        context.setAllowNullPathInfo(true);
        context.setClassLoader(Thread.currentThread().getContextClassLoader());
        context.setHandler(new AbstractHandler() {

            private BasicDBObject nestAYolo(BasicDBObject parent, String key) {
                if (parent.get(key) == null) {
                    parent.put(key, new BasicDBObject());
                }

                return (BasicDBObject) parent.get(key);
            }

            @Override
            public void handle(String s, Request baseRequest, HttpServletRequest request, HttpServletResponse
                    response) throws IOException, ServletException {

                BasicDBObject data = new BasicDBObject();

                // Get year param
                Optional<String> yearStr = optParam(request, "year");
                Optional<Integer> optYear = yearStr.flatMap(str -> Optional.of(Integer.parseInt(str)));

                int year = optYear.orElse(2013);
                data.put("year", year);

                Session session = injector.getInstance(Session.class);

                // Load shops.
                BasicDBObject shops = new BasicDBObject();
                for (Object shopObj: session.createCriteria(Shop.class).list()) {
                    Shop shop = (Shop) shopObj;
                    shops.put(String.valueOf(shop.getShopId()), shop.getCityName());
                }
                data.put("shops", shops);

                // Load articles.
                BasicDBObject articles = new BasicDBObject();
                for (Object articleObj: session.createCriteria(Article.class).list()) {
                    Article article = (Article) articleObj;
                    articles.put(String.valueOf(article.getArticleId()), article.getArticle());
                }
                data.put("articles", articles);

                // Load values.
                session.doWork(connection -> {
                    BasicDBObject values = new BasicDBObject();
                    PreparedStatement statement = connection.prepareStatement("SELECT SHOPID, MONTH, ARTICLEID, SUM(AMOUNT) AS VALUE\n" +
                            "FROM STAR\n" +
                            "WHERE YEAR=?\n" +
                            "GROUP BY SHOPID, MONTH, ARTICLEID");
                    statement.setInt(1, year);
                    final ResultSet resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        String shop = resultSet.getString("shopid");
                        String article = resultSet.getString("articleid");
                        String month = resultSet.getString("month");
                        String value = resultSet.getString("value");

                        BasicDBObject shopValues = nestAYolo(values, shop);
                        BasicDBObject monthValues = nestAYolo(shopValues, month);
                        monthValues.put(article, value);

                        data.put("values", values);
                    }
                });

                response.setContentType("application/json;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
                response.getWriter().print(JSON.serialize(data));
            }

            private Optional<String> optParam(HttpServletRequest request, String paramName) {
                if (request.getParameter(paramName) != null) {
                    return Optional.of(request.getParameter(paramName));
                }

                return Optional.empty();
            }
        });

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{ resourceHandler, context });

        Server server = new Server(1337);
        server.setHandler(handlers);

        // Start server
        try {
            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
