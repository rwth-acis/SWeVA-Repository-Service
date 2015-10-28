package i5.las2peer.services.swevaRepository;

import com.mongodb.client.result.UpdateResult;
import i5.las2peer.api.Service;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.MediaType;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.restMapper.tools.ValidationResult;
import i5.las2peer.restMapper.tools.XMLCheck;

import io.swagger.annotations.*;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import javax.ws.rs.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

import net.minidev.json.JSONValue;
import org.bson.Document;
import com.mongodb.Block;
import com.mongodb.client.FindIterable;

import static com.mongodb.client.model.Projections.*;

/**
 * SWeVA Repository Service
 * <p>
 * This service hosts modules and components related to the SWeVA platform.
 * <p>
 */
@Path("/swevarepository")
@Version("0.1") // this annotation is used by the XML mapper
@Api
@SwaggerDefinition(
        info = @Info(
                title = "SWeVA Repository Service",
                version = "0.1",
                description = "This service hosts modules and components related to the SWeVA platform.",
                termsOfService = "",
                contact = @Contact(
                        name = "Alexander Ruppert",
                        url = "",
                        email = "alexander.ruppert@rwth-aachen.de"
                ),
                license = @License(
                        name = "BSD",
                        url = ""
                )
        ))
public class SwevaRepository extends Service {

    public static final String TOOLS = "tools";
    public static final String USERS = "users";
    public static final double INITIAL_RATING = 3.0;


    private final String[] PROHIBITED_PROPERTIES_TO_UPDATE = {
            "name", "author", "category", "created", "modified", "files", "rating", "comments"
    };

    MongoDatabase db;

    public SwevaRepository() {
        // read and set properties values
        // IF THE SERVICE CLASS NAME IS CHANGED, THE PROPERTIES FILE NAME NEED TO BE CHANGED TOO!
        setFieldValues();



        //mongoDB initialization
        initDatabase("test");



    }

    /**
     * Initializes the database (method imortant for unit tests)
     * @param dbName name of the database
     * @return database object
     */
    public MongoDatabase initDatabase(String dbName){
        MongoClient mongoClient = new MongoClient();
        db = mongoClient.getDatabase(dbName);
        db.getCollection(TOOLS).createIndex(
                new Document("name", "text")
                        .append("title", "text")
                        .append("description", "text")
                        .append("short-description", "text")
                        .append("tags", "text")
        );
        return db;
    }
    // //////////////////////////////////////////////////////////////////////////////////////
    // Service methods.
    // //////////////////////////////////////////////////////////////////////////////////////


    //-- Items


    @GET
    @Path("/catalog/{category}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "View items",
            notes = "Retrieves a set of searched items")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Items retrieved")
    })

    public HttpResponse getItems(@PathParam("category") String category, @DefaultValue("") @QueryParam("search") String search, @DefaultValue("age") @QueryParam("sort") String sort) {

        search = search.toLowerCase();

        sort = sort.toLowerCase();
        String sortProperty = "created";
        int sortDirection = -1;

        switch (sort) {
            case "age":
                sortProperty = "created";
                sortDirection = -1;
                break;
            case "rating":
                sortProperty = "rating.rating";
                sortDirection = -1;
                break;
        }

        Document queryDocument = new Document("category", category);
        if (search.trim().length() > 0) {
            queryDocument = queryDocument.append("$text", new Document("$search", search));
        }
        FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                queryDocument
        ).projection(
                fields(include("title", "short-description", "tags", "name", "thumbnail", "rating.rating", "created", "modified"), excludeId())
        ).sort(
                new Document(sortProperty,sortDirection)
        );
        JSONArray array = new JSONArray();

        iterable.forEach((Block<Document>) document -> {
            //remove stupid extended JSON
            String created = Long.toString((Long) document.get("created"));
            String modified = Long.toString((Long) document.get("modified"));
            String rating = Double.toString((Double)((Document)document.get("rating")).get("rating"));

            document.put("created", created);
            document.put("modified", modified);
            document.put("rating", rating);
            array.add(document);
        });
        return new HttpResponse(array.toJSONString(), HttpURLConnection.HTTP_OK);
    }

    @GET
    @Path("/catalog/{category}/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Full item retrieval",
            notes = "Retrieves full item information")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Item retrieved"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Item not found")
    })

    public HttpResponse getItem(@PathParam("category") String category, @PathParam("name") String name) {


        FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                new Document("name", name)
                        .append("category", category)
        ).projection(
                fields(exclude("files"), exclude("rating.ratings","comments"), excludeId())
        );
        Document firstResult = iterable.first();


        if (firstResult != null) {

            //remove stupid extended JSON
            String created = Long.toString((Long) firstResult.get("created"));
            String modified = Long.toString((Long) firstResult.get("modified"));
            String rating = Double.toString((Double) ((Document)firstResult.get("rating")).get("rating"));

            firstResult.put("rating", rating);
            firstResult.put("created", created);
            firstResult.put("modified", modified);

            return new HttpResponse(firstResult.toJson(), HttpURLConnection.HTTP_OK);
        } else {
            return new HttpResponse("{}", HttpURLConnection.HTTP_NOT_FOUND);
        }

    }

    @PUT
    @Path("/catalog/{category}/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Item update",
            notes = "Updates item information")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Item updated"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Item not found")
    })

    public HttpResponse updateItem(@PathParam("category") String category, @PathParam("name") String name, @ContentParam() String content) {

        category = category.toLowerCase();

        JSONObject input = (JSONObject) JSONValue.parse(content);
        String userId = (String) ((JSONObject) input.get("author")).get("id");

        Document searchDocument = new Document("name", name)
                .append("category", category)
                .append("author.id", userId);



        Date date = new Date();
        long now = date.getTime();


        Document updateDocument = new Document();

        searchDocument.append("name", name)
                .append("category", category)
                .append("author", input.get("author"));

        //see what fields provided in JSON and update
        Iterator propertyIterator = input.entrySet().iterator();
        while (propertyIterator.hasNext()) {
            Map.Entry pair = (Map.Entry) propertyIterator.next();
            String key = ((String) (pair.getKey())).toLowerCase().trim();

            if (canBeUpdatedByUser(key)) {


                updateDocument = updateDocument.append(key, pair.getValue());
            } else if (key.equals("files")) { //handle files and versions explicitly

                for (Map.Entry<String, Object> file : ((JSONObject) pair.getValue()).entrySet()) {
                    updateDocument = updateDocument.append("files." + file.getKey(), file.getValue());
                }
            }
            propertyIterator.remove(); // avoids a ConcurrentModificationException
        }
        updateDocument = updateDocument.append("modified", now);
        UpdateResult result = db.getCollection(TOOLS).updateOne(searchDocument, new Document("$set", updateDocument));

        if (result.wasAcknowledged() && result.getMatchedCount() > 0) {
            return new HttpResponse("Item updated!", HttpURLConnection.HTTP_OK);
        } else {
            return new HttpResponse("Something happened!", HttpURLConnection.HTTP_NOT_FOUND);
        }



    }

    @POST
    @Path("/catalog/{category}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Item creation",
            notes = "Creates a new item")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "Item created"),
            @ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "Item already exists")
    })
    public HttpResponse createItem(@PathParam("category") String category, @ContentParam() String content) {

        category = category.toLowerCase();

        JSONObject input = (JSONObject) JSONValue.parse(content);
        String name = (String) input.get("name");

        createUserIfNotExisting((JSONObject) input.get("author"));


        FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                new Document("name", name)
                        .append("category", category));

        Document firstResult = iterable.first();
        if (firstResult != null) { //if already existing
            return new HttpResponse("An item with the same name already exists", HttpURLConnection.HTTP_CONFLICT);
        } else { // create new entry


            Document root = new Document();
            root.append("name", name);
            root.append("category", category);
            root.append("author", input.get("author"));
            Date date = new Date();
            long now = date.getTime();
            root.append("created", now);
            root.append("modified", now);
            root.append("rating", new Document("rating", INITIAL_RATING));

            copyProperty(root, input, "title");
            copyProperty(root, input, "short-description");
            copyProperty(root, input, "thumbnail");
            copyProperty(root, input, "description");
            copyProperty(root, input, "source");


            copyProperty(root, input, "license");
            copyProperty(root, input, "tags");
            copyProperty(root, input, "documentation");
            copyProperty(root, input, "files");

            db.getCollection(TOOLS).insertOne(root);


            return new HttpResponse("/swewarepository/" + category + "/" + name, HttpURLConnection.HTTP_CREATED);
        }
    }

    /**
     * Helper method to copy properties between two JSON objects
     * @param target
     * @param source
     * @param property
     */
    private void copyProperty(Document target, JSONObject source, String property) {
        target.append(property, source.get(property));
    }

    /**
     * Checks, if a database entry may be updated by the user
     * @param key name of the property to be updated
     * @return true, if an update is allowed
     */
    private boolean canBeUpdatedByUser(String key) {
        key = key.toLowerCase().trim();
        for (int i = 0; i < PROHIBITED_PROPERTIES_TO_UPDATE.length; i++) {
            if (key.equals(PROHIBITED_PROPERTIES_TO_UPDATE[i])) {
                return false;
            }
        }
        return true;
    }

    @DELETE
    @Path("/catalog/{category}/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Item deletion",
            notes = "Deletes an existing item")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Item deleted"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Item not found")
    })
    public HttpResponse deleteItem(@PathParam("category") String category, @PathParam("name") String name) {

        FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                new Document("name", name)
                        .append("category", category));
        Document firstResult = iterable.first();

        if (firstResult != null) {
            db.getCollection(TOOLS).deleteMany(
                    new Document("name", name)
                            .append("category", category));

            return new HttpResponse("Deleted!", HttpURLConnection.HTTP_OK);
        }

        return new HttpResponse("Item not found!", HttpURLConnection.HTTP_NOT_FOUND);
    }

    // raw
    @GET
    @Path("/raw/{category}/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Item raw data",
            notes = "Displays the raw data of the item without any metadata. I.e. the pure JSON of a module.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Item retrieved"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Item not found")
    })
    public HttpResponse getRawItem(@PathParam("category") String category, @PathParam("name") String name, @DefaultValue("") @QueryParam("version") String version) {


        if (version.length() == 0) {
            FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                    new Document("name", name)
                            .append("category", category)
            ).projection(
                    fields(include("files"), excludeId())
            );
            Document firstResult = iterable.first();


            if (firstResult != null) {

                Document files = (Document) firstResult.get("files");
                Iterator iterator = files.entrySet().iterator();
                int[] highestKey = {0, 0, 0};
                Object file = new Object();
                while (iterator.hasNext()) { //find newest version
                    Map.Entry pair = (Map.Entry) iterator.next();
                    String key = ((String) (pair.getKey())).toLowerCase().trim();
                    String[] split = key.split("-");
                    int[] keySplit = {Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2])};
                    if ((highestKey[0] == 0) && (highestKey[1] == 0) && highestKey[2] == 0) {
                        highestKey = keySplit;
                        file =  pair.getValue();
                    } else {
                        if (keySplit[0] > highestKey[0]) {
                            highestKey = keySplit;
                            file =  pair.getValue();
                        } else if (keySplit[0] == highestKey[0]) {
                            if (keySplit[1] > highestKey[1]) {
                                highestKey = keySplit;
                                file =  pair.getValue();
                            } else if (keySplit[1] == highestKey[1]) {
                                if (keySplit[2] > highestKey[2]) {
                                    highestKey = keySplit;
                                    file =  pair.getValue();
                                }
                            }
                        }
                    }
                }
                String result;
                try {
                    result = ((Document)file).toJson();
                }
                catch (Exception e) {
                    result = (String)file;
                }
                return new HttpResponse(result, HttpURLConnection.HTTP_OK);
            } else {
                return new HttpResponse("{}", HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                    new Document("name", name)
                            .append("category", category)
                            .append("files." + version, new Document("$exists", true))
            ).projection(
                    fields(include("files." + version), excludeId())

            );
            Document firstResult = iterable.first();
            String result="";
            if (firstResult != null) {
                Object file =((Document) firstResult.get("files")).get(version);

                try {
                    result = ((Document) file).toJson();
                }
                catch (Exception e) {
                    result = ((String) file);
                }

                return new HttpResponse(result, HttpURLConnection.HTTP_OK);
            } else {
                return new HttpResponse("{}", HttpURLConnection.HTTP_NOT_FOUND);
            }
        }
    }


    //-- Rating


    @PUT
    @Path("/catalog/{category}/{name}/rating/{rating}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Rating update",
            notes = "Updates the rating of an item")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Item rated"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Item not found")
    })
    public HttpResponse rateItem(@PathParam("category") String category, @PathParam("name") String name, @PathParam("rating") String rating) {


        Document searchDocument = new Document("name", name)
                .append("category", category);

        FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                searchDocument

        ).projection(
                fields(include("rating"), excludeId())
        );
        Document firstResult = iterable.first();


        if (firstResult != null) {
            ArrayList<Document> ratings = (ArrayList<Document>) ((Document) firstResult.get("rating")).get("ratings");
            int sum = Integer.parseInt(rating);
            float newRating = Integer.parseInt(rating);

            if (ratings != null) {

                for (int i = 0; i < ratings.size(); i++) {
                    sum += Integer.parseInt((String) ratings.get(i).get("rating"));
                }
                newRating = ((float) sum / (ratings.size() + 1));
            }

            db.getCollection(TOOLS).updateOne(searchDocument,
                    new Document("$set", new Document("rating.rating", newRating))
                            .append("$push", new Document("rating.ratings", new Document("user", "1").append("rating", rating)))
            );

            return new HttpResponse("{\"rating\": " + newRating + "}", HttpURLConnection.HTTP_OK);
        } else {
            return new HttpResponse("{}", HttpURLConnection.HTTP_NOT_FOUND);
        }
    }

    //-- Users
    @GET
    @Path("/users/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "User retrieval",
            notes = "Retrieves information about a user")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "User retrieved"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "User not found")
    })
    public HttpResponse getUser(@PathParam("id") String userId) {

        FindIterable<Document> iterable = db.getCollection(USERS).find(
                new Document("id", userId)
        ).projection(
                fields(include("name", "email", "created"), excludeId())
        );

        Document firstUser = iterable.first();
        if (firstUser != null) {

            String created = Long.toString((Long) firstUser.get("created"));
            firstUser.put("created", created);

            return new HttpResponse(firstUser.toJson(), HttpURLConnection.HTTP_OK);
        } else {
            return new HttpResponse("Not found!", HttpURLConnection.HTTP_NOT_FOUND);
        }

    }

    @PUT
    @Path("/users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "User update",
            notes = "Updates information of an existing user")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "User updated"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "User not found")
    })
    public HttpResponse updateUser(@PathParam("id") String userId, @ContentParam String content) {

        JSONObject input = (JSONObject) JSONValue.parse(content);

        UpdateResult result = db.getCollection(USERS).updateOne(
                new Document("id", userId),
                new Document("$set",
                        new Document("name", input.get("name"))
                                .append("email", input.get("email"))
                )
        );

        //tools
        UpdateResult result2 = db.getCollection(TOOLS).updateMany(
                new Document("author.id", input.get("id")),
                new Document("$set",
                        new Document("author.name", input.get("name"))
                                .append("author.email", input.get("email"))
                )
        );
        //comments
        FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                new Document("comments", new Document("$elemMatch", new Document("author.id", input.get("id"))))
        ).projection(
                fields(include("comments"))
        );

        iterable.forEach(new Block<Document>() {
            @Override
            public void apply(final Document document) {
                Document updateDocument = new Document();
                ArrayList<Document> comments = (ArrayList<Document>) (document.get("comments"));

                for (int i = 0; i < comments.size(); i++) {
                    if (((Document) comments.get(i).get("author")).get("id").equals(input.get("id"))) {
                        updateDocument.append("comments." + i + ".author.name", input.get("name"));
                        updateDocument.append("comments." + i + ".author.email", input.get("email"));
                    }
                }
                UpdateResult updateResult = db.getCollection(TOOLS).updateOne(
                        new Document("_id", document.get("_id")),
                        new Document("$set",
                                updateDocument
                        )
                );
            }
        });

        if (result.wasAcknowledged() && result2.wasAcknowledged()) {
            return new HttpResponse(input.toJSONString(), HttpURLConnection.HTTP_OK);
        } else {
            return new HttpResponse("Not found!", HttpURLConnection.HTTP_NOT_FOUND);
        }

    }

    //-- Comments

    /**
     * Creates a user, if no user with the same id already exists
     * @param author information (id, name, email)
     */
    public void createUserIfNotExisting(JSONObject author) {


        FindIterable<Document> iterable = db.getCollection(USERS).find(
                new Document("id", author.get("id"))
        );

        Document firstUser = iterable.first();
        if (firstUser == null) {

            Date date = new Date();
            long now = date.getTime();

            String userEmail = ((String) author.get("email")).toLowerCase().trim();
            String userName = ((String) author.get("name"));
            db.getCollection(USERS).insertOne(new Document("id", author.get("id"))
                            .append("created", now)
                            .append("name", userName)
                            .append("email", userEmail)

            );
        }
    }



    @GET
    @Path("/catalog/{category}/{name}/comments")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Comment retrieval",
            notes = "Retrieves comments on an item")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Comments retrieved"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Item not found")
    })
    public HttpResponse getItemComments(@PathParam("category") String category, @PathParam("name") String name) {

        FindIterable<Document> iterable = db.getCollection(TOOLS).find(
                new Document("name", name)
                        .append("category", category)
        ).projection(
                fields(include("comments"), excludeId())
        );
        Document firstResult = iterable.first();

        if (firstResult != null) {

            ArrayList<Document> comments = (ArrayList<Document>) (firstResult.get("comments"));
            if(comments!=null){

                for (int i = 0; i < comments.size(); i++) {
                    String created = Long.toString((Long) comments.get(i).get("created"));
                    String modified = Long.toString((Long) comments.get(i).get("modified"));
                    comments.get(i).put("created", created);
                    comments.get(i).put("modified", modified);
                }
            }
            else {
                JSONArray jarr = new JSONArray();

                firstResult.put("comments",jarr);
            }
            return new HttpResponse(firstResult.toJson(), HttpURLConnection.HTTP_OK);
        } else {
            return new HttpResponse("Not found!", HttpURLConnection.HTTP_NOT_FOUND);
        }


    }



    @POST
    @Path("/catalog/{category}/{name}/comments")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Comment creation",
            notes = "Creates a comment on an existing item")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "Comment created"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Item not found")
    })
    public HttpResponse postItemComment(@PathParam("category") String category, @PathParam("name") String name, @ContentParam String content) {


        JSONObject input = (JSONObject) JSONValue.parse(content);

        String comment = (String) input.get("comment");
        String id = UUID.randomUUID().toString();

        Date date = new Date();
        long now = date.getTime();


        createUserIfNotExisting((JSONObject) input.get("author"));


        Document searchDocument = new Document("name", name)
                .append("category", category);

        Document commentDocument = new Document("id", id)
                .append("author", input.get("author"))
                .append("created", now)
                .append("modified", now)
                .append("comment", comment);

        UpdateResult result = db.getCollection(TOOLS).updateOne(searchDocument,
                new Document("$push", new Document("comments", commentDocument))
        );

        if (result.wasAcknowledged()) {
            return new HttpResponse("swevarepository/catalog/" + category + "/" + name + "/comments/" + id, HttpURLConnection.HTTP_CREATED);
        } else {
            return new HttpResponse("Not found!", HttpURLConnection.HTTP_NOT_FOUND);
        }
    }

    @PUT
    @Path("/catalog/{category}/{name}/comments/{commentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Comment update",
            notes = "Updates an existing comment")
    @ApiResponses(value = {
            @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Comment updated"),
            @ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Comment not found")
    })
    public HttpResponse updateItemComment(@PathParam("category") String category, @PathParam("name") String name, @PathParam("commentId") String commentId, @ContentParam String content) {


        JSONObject input = (JSONObject) JSONValue.parse(content);
        String userId = (String) ((JSONObject) input.get("author")).get("id");
        String comment = (String) input.get("comment");


        Date date = new Date();
        long now = date.getTime();

        Document searchDocument = new Document("name", name)
                .append("category", category)
                .append("comments.id", commentId)
                .append("comments.author.id", userId);


        UpdateResult result = db.getCollection(TOOLS).updateOne(searchDocument,
                new Document("$set", new Document("comments.$.modified", now).append("comments.$.comment", comment))
        );

        if (result.wasAcknowledged() && result.getMatchedCount() > 0) {
            return new HttpResponse("Comment updated!", HttpURLConnection.HTTP_OK);
        } else {
            return new HttpResponse("Something happened!", HttpURLConnection.HTTP_NOT_FOUND);
        }
    }




    // //////////////////////////////////////////////////////////////////////////////////////
    // Methods required by the LAS2peer framework.
    // //////////////////////////////////////////////////////////////////////////////////////

    /**
     * Method for debugging purposes.
     * Here the concept of restMapping validation is shown.
     * It is important to check, if all annotations are correct and consistent.
     * Otherwise the service will not be accessible by the WebConnector.
     * Best to do it in the unit tests.
     * To avoid being overlooked/ignored the method is implemented here and not in the test section.
     *
     * @return true, if mapping correct
     */
    public boolean debugMapping() {
        String XML_LOCATION = "./restMapping.xml";
        String xml = getRESTMapping();

        try {
            RESTMapper.writeFile(XML_LOCATION, xml);
        } catch (IOException e) {
            e.printStackTrace();
        }

        XMLCheck validator = new XMLCheck();
        ValidationResult result = validator.validate(xml);

        if (result.isValid()) {
            return true;
        }
        return false;
    }

    /**
     * This method is needed for every RESTful application in LAS2peer. There is no need to change!
     *
     * @return the mapping
     */
    public String getRESTMapping() {
        String result = "";
        try {
            result = RESTMapper.getMethodsAsXML(this.getClass());
        } catch (Exception e) {

            e.printStackTrace();
        }
        return result;
    }

}
