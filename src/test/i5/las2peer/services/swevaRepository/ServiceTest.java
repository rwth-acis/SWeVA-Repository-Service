package i5.las2peer.services.swevaRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.restMapper.data.Pair;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 */
public class ServiceTest {

    private static final String HTTP_ADDRESS = "http://127.0.0.1";
    private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

    private static LocalNode node;
    private static WebConnector connector;
    private static ByteArrayOutputStream logStream;

    private static UserAgent testAgent;
    private static final String testPass = "adamspass";

    private static final String testTemplateService = SwevaRepository.class.getCanonicalName();

    private static final String mainPath = "swevarepository/";


    private static final String superToolJSON = "{\n" +
            "    \"name\": \"super-tool\",\n" +
            "    \"author\": {\n" +
            "        \"id\": \"234635731\",\n" +
            "        \"name\": \"John Doe\",\n" +
            "        \"email\": \"a@b.de\"\n" +
            "    },\n" +
            "    \"title\": \"SuperTool\",\n" +
            "    \"short-description\":\"This is a short text!\",\n" +
            "    \"thumbnail\":\"data:image/png;base64,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\",\n" +
            "    \"description\": \"Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.\",\n" +
            "    \"source\": \"https://www.google.com\",\n" +
            "    \"license\": {\n" +
            "        \"name\": \"BSD\",\n" +
            "        \"url\": \"https://www.google.com\"\n" +
            "    },\n" +
            "    \"tags\": [\n" +
            "        \"banana\",\n" +
            "        \"blue\",\n" +
            "        \"green\",\n" +
            "        \"canada\"\n" +
            "    ],\n" +
            "    \"documentation\": {\n" +
            "        \"text\": \"ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing\",\n" +
            "        \"url\": \"\"\n" +
            "    },\n" +
            "    \"files\": {            \n" +
            "        \"1-0-1\": {\n" +
            "           \n" +
            "           \"address\" : {\n" +
            "              \"street\" : \"2 Avenue\",\n" +
            "              \"zipcode\" : \"10075\",\n" +
            "              \"building\" : \"1480\",\n" +
            "              \"coord\" : [ -73.9557413, 40.7720266 ],\n" +
            "           },\n" +
            "           \"borough\" : \"Manhattan\",\n" +
            "           \"cuisine\" : \"Italian\",\n" +
            "           \"grades\" : [\n" +
            "              {\n" +
            "                 \"date\" : ISODate(\"2014-10-01T00:00:00Z\"),\n" +
            "                 \"grade\" : \"A\",\n" +
            "                 \"score\" : 11\n" +
            "              },\n" +
            "              {\n" +
            "                 \"date\" : ISODate(\"2014-01-16T00:00:00Z\"),\n" +
            "                 \"grade\" : \"B\",\n" +
            "                 \"score\" : 17\n" +
            "              }\n" +
            "           ],\n" +
            "           \"name\" : \"Vella\",\n" +
            "           \"restaurant_id\" : \"41704620\"\n" +
            "        }\n" +
            "    }\n" +
            "    \n" +
            "}    \n";
    /**
     * Called before the tests start.
     * <p>
     * Sets up the node and initializes connector and users that can be used throughout the tests.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void startServer() throws Exception {

        // start node
        node = LocalNode.newNode();
        node.storeAgent(MockAgentFactory.getAdam());
        node.launch();

        ServiceAgent testService = ServiceAgent.generateNewAgent(testTemplateService, "a pass");
        testService.unlockPrivateKey("a pass");


        node.registerReceiver(testService);

        // start connector
        logStream = new ByteArrayOutputStream();

        connector = new WebConnector(true, HTTP_PORT, false, 1000);
        connector.setLogStream(new PrintStream(logStream));
        connector.start(node);


        MongoDatabase db = (MongoDatabase) testService.invoke("initDatabase", new String[]{"testing"});

        //deletes all collections before testing
        MongoIterable<String> collections = db.listCollectionNames();

        db.getCollection("users").drop();
        db.getCollection("tools").drop();

        //reinitialization of indexers
        db = (MongoDatabase) testService.invoke("initDatabase", new String[]{"testing"});

        Thread.sleep(1000); // wait a second for the connector to become ready
        testAgent = MockAgentFactory.getAdam();


        connector.updateServiceList();
        // avoid timing errors: wait for the repository manager to get all services before continuing
        try {
            System.out.println("waiting..");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * Called after the tests have finished.
     * Shuts down the server and prints out the connector log file for reference.
     *
     * @throws Exception
     */
    @AfterClass
    public static void shutDownServer() throws Exception {

        connector.stop();
        node.shutDown();

        connector = null;
        node = null;

        LocalNode.reset();

        System.out.println("Connector-Log:");
        System.out.println("--------------");

        System.out.println(logStream.toString());

    }

    /**
     * Tests items (get put post delete)
     */
    @Test
    public void testItems() {
        MiniClient c = new MiniClient();
        c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);


        try {

            //create

            c.setLogin(Long.toString(testAgent.getId()), testPass);





            ClientResponse result = c.sendRequest("POST", mainPath + "catalog/tools", superToolJSON, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(201, result.getHttpCode());
            //when creating again, there should be an error
            result = c.sendRequest("POST", mainPath + "catalog/tools", superToolJSON, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(409, result.getHttpCode());


            //search
            result = c.sendRequest("GET", mainPath + "catalog/tools?search=SuperTool", "");
            assertEquals(200, result.getHttpCode());
            JSONArray resultJSONArray = (JSONArray) JSONValue.parse(result.getResponse());
            assertEquals("SuperTool", ((JSONObject) (resultJSONArray.get(0))).get("title")); //yield a result

            result = c.sendRequest("GET", mainPath + "catalog/tools?search=bananarananajksfdjklsdf", "");
            assertEquals(200, result.getHttpCode());
            resultJSONArray = (JSONArray) JSONValue.parse(result.getResponse());
            assertEquals(0, resultJSONArray.size()); //should be an empty array


            //details
            result = c.sendRequest("GET", mainPath + "catalog/tools/super-tool", "");
            assertEquals(200, result.getHttpCode());

            JSONObject resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals("SuperTool", resultJSON.get("title")); //should have correct title

            //update

            String content = "{\n" +
                    "    \"author\": {\n" +
                    "        \"id\": \"234635731\",\n" +
                    "        \"name\": \"John Doe\",\n" +
                    "        \"email\": \"a@b.de\"\n" +
                    "    },\n" +
                    "    \"title\": \"SuperMegaTool\"\n" +
                    "}";
            result = c.sendRequest("PUT", mainPath + "catalog/tools/super-tool", content, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(200, result.getHttpCode());
            result = c.sendRequest("GET", mainPath + "catalog/tools/super-tool", "");
            resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals("SuperMegaTool", resultJSON.get("title")); //should have new title

            //raw data
            result = c.sendRequest("GET", mainPath + "raw/tools/super-tool", content);
            assertEquals(200, result.getHttpCode());
            resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals("Manhattan", resultJSON.get("borough"));

            //delete
            result = c.sendRequest("DELETE", mainPath + "catalog/tools/super-tool", content);
            assertEquals(200, result.getHttpCode());
            result = c.sendRequest("GET", mainPath + "catalog/tools/super-tool", "");
            assertEquals(404, result.getHttpCode());


        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e);
        }


    }

    /**
     * Tests ratings
     */
    @Test
    public void testRatings() {
        MiniClient c = new MiniClient();
        c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

        //create
        try {
            c.setLogin(Long.toString(testAgent.getId()), testPass);

            //crate item first
            ClientResponse result = c.sendRequest("POST", mainPath + "catalog/tools", superToolJSON, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(201, result.getHttpCode());

            result = c.sendRequest("PUT", mainPath + "catalog/tools/super-tool/rating/5", "");
            assertEquals(200, result.getHttpCode());

            result = c.sendRequest("PUT", mainPath + "catalog/tools/super-tool/rating/3", "");
            assertEquals(200, result.getHttpCode());

            result = c.sendRequest("PUT", mainPath + "catalog/tools/super-tool/rating/1", "");
            assertEquals(200, result.getHttpCode());

            result = c.sendRequest("GET", mainPath + "catalog/tools/super-tool", "");
            JSONObject resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals(Float.toString((5 + 3 + 1) / 3f), resultJSON.get("rating"));


            result = c.sendRequest("DELETE", mainPath + "catalog/tools/super-tool", "");
            assertEquals(200, result.getHttpCode());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e);
        }
    }

    /**
     * Tests users
     */
    @Test
    public void testUsers() {
        MiniClient c = new MiniClient();
        c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

        //create
        try {
            c.setLogin(Long.toString(testAgent.getId()), testPass);

            //crate item first
            ClientResponse result = c.sendRequest("POST", mainPath + "catalog/tools", superToolJSON, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(201, result.getHttpCode());

            result = c.sendRequest("GET", mainPath + "users/234635731", "");
            assertEquals(200, result.getHttpCode());
            JSONObject resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals("John Doe", resultJSON.get("name"));


            String content = "{\n" +
                    "        \"id\": \"234635731\",\n" +
                    "        \"name\": \"Jane Doe\",\n" +
                    "        \"email\": \"a@b.de\"\n" +
                    "}";

            result = c.sendRequest("PUT", mainPath + "users/234635731", content, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(200, result.getHttpCode());

            result = c.sendRequest("GET", mainPath + "users/234635731", "");
            assertEquals(200, result.getHttpCode());
            resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals("Jane Doe", resultJSON.get("name"));


            result = c.sendRequest("DELETE", mainPath + "catalog/tools/super-tool", "");
            assertEquals(200, result.getHttpCode());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e);
        }
    }


    /**
     * Tests comments
     */
    @Test
    public void testComments() {
        MiniClient c = new MiniClient();
        c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

        //create
        try {
            c.setLogin(Long.toString(testAgent.getId()), testPass);

            //crate item first
            ClientResponse result = c.sendRequest("POST", mainPath + "catalog/tools", superToolJSON, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(201, result.getHttpCode());

            String content = "{\n" +
                    "    \"author\": {\n" +
                    "        \"id\": \"234635731\",\n" +
                    "        \"name\": \"John Doe\",\n" +
                    "        \"email\": \"a@b.de\"\n" +
                    "    },\n" +
                    "    \"comment\": \"SuperMegaTool\"\n" +
                    "}";
            result = c.sendRequest("POST", mainPath + "catalog/tools/super-tool/comments", content, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(201, result.getHttpCode());
            String commentUrl = result.getResponse();
           
            result = c.sendRequest("GET", mainPath + "catalog/tools/super-tool/comments", "");
            assertEquals(200, result.getHttpCode());
            JSONObject resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals("SuperMegaTool", ((JSONObject) (((JSONArray) resultJSON.get("comments")).get(0))).get("comment"));

            content = "{\n" +
                    "    \"author\": {\n" +
                    "        \"id\": \"234635731\",\n" +
                    "        \"name\": \"John Doe\",\n" +
                    "        \"email\": \"a@b.de\"\n" +
                    "    },\n" +
                    "    \"comment\": \"SuperMegaToolBanana\"\n" +
                    "}";
            result = c.sendRequest("PUT", commentUrl, content, new Pair[]{new Pair<String>("content-type", "application/json")});
            assertEquals(200, result.getHttpCode());

            result = c.sendRequest("GET", mainPath + "catalog/tools/super-tool/comments", "");
            assertEquals(200, result.getHttpCode());
            resultJSON = (JSONObject) JSONValue.parse(result.getResponse());
            assertEquals("SuperMegaToolBanana", ((JSONObject) (((JSONArray) resultJSON.get("comments")).get(0))).get("comment"));

            result = c.sendRequest("DELETE", mainPath + "catalog/tools/super-tool", "");
            assertEquals(200, result.getHttpCode());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e);
        }
    }


    /**
     * Test the TemplateService for valid rest mapping.
     * Important for development.
     */
    @Test
    public void testDebugMapping() {
        SwevaRepository cl = new SwevaRepository();
        assertTrue(cl.debugMapping());
    }

}
