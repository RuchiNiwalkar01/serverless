package com.neu.edu.lambda;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class LogEvent implements RequestHandler<SNSEvent, Object> 
{

	private DynamoDB dynamoDB;
	private final String TABLE_NAME = "csye6225";
	private Regions REGION = Regions.US_EAST_1;
	static final String DOMAIN = System.getenv("Domain");
	static final String subject = "View your Bills Due";
	static String htmlBody;
	private static String textBody;
	private String from="";
	private String username;
	static JSONArray billIds;
	 static String token;
	@Override
	public Object handleRequest(SNSEvent request, Context context)
	{
		
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("domain"+DOMAIN);
       
        from = "no-reply@test." + DOMAIN;

        //Creating ttl
        context.getLogger().log("Invocation started: " + timeStamp);
        long now = Calendar.getInstance().getTimeInMillis()/1000; // unix time
        long ttl = 10 * 60; // ttl set to 15 min
        long totalttl = ttl + now ;

        try {
            context.getLogger().log("Message rec "+request.getRecords().get(0).getSNS().getMessage());
            JSONObject body = new JSONObject(request.getRecords().get(0).getSNS().getMessage());
            username =  body.getString("uname");
            billIds = body.getJSONArray("billsDue");
            context.getLogger().log("Username is "+username);
            context.getLogger().log("Bill Ids"+billIds);
        } catch (JSONException e) 
        {
            e.printStackTrace();
        }
        token = UUID.randomUUID().toString();

        context.getLogger().log("Invocation completed: " + timeStamp);
        
        try {
            initDynamoDbClient();
            long ttlDbValue = 0;
            Item item = this.dynamoDB.getTable(TABLE_NAME).getItem("id", username);
            if (item != null) {
                context.getLogger().log("Checking for timestamp");
                ttlDbValue = item.getLong("ttl");
            }

            if (item == null || (ttlDbValue < now && ttlDbValue != 0)) {
                context.getLogger().log("Checking for valid ttl");
                context.getLogger().log("ttl expired, creating new token and sending email");
                this.dynamoDB.getTable(TABLE_NAME)
                        .putItem(
                                new PutItemSpec().withItem(new Item()
                                        .withString("id", username)
                                        .withString("token", token)
                                        .withLong("ttl", totalttl)));

                //loop
                StringBuilder BillIdsforEmail = new StringBuilder();
                for (int i=0; i < billIds.length(); i++){
                	BillIdsforEmail.append(DOMAIN +  "/v1/bill/"+billIds.get(i) + System.lineSeparator());
                    
                }
                context.getLogger().log("Text " + BillIdsforEmail);
                htmlBody = "<h2>Email sent from Amazon SES</h2>"
                        + "<p>The url for your the bills created by you " +
                        "Link: "+ BillIdsforEmail + "</p>";
                context.getLogger().log("This is HTML body: " + htmlBody);

                textBody="Hello "+username+ "\n You have following bills due. The urls are as below \n Links : "+BillIdsforEmail;
                //Sending email using Amazon SES client
                AmazonSimpleEmailService clients = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion(Regions.US_EAST_1).build();
                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(
                                new Destination().withToAddresses(username))
                        .withMessage(new Message()
                                .withBody(new Body()
                                        .withHtml(new Content()
                                                .withCharset("UTF-8").withData(htmlBody))
                                        .withText(new Content()
                                                .withCharset("UTF-8").withData(textBody)))
                                .withSubject(new Content()
                                        .withCharset("UTF-8").withData(subject)))
                        .withSource(from);
                clients.sendEmail(emailRequest);
                context.getLogger().log("Email sent successfully to email id: " +username);


            }
            else 
            {
                context.getLogger().log("ttl is not expired. New request is not processed for the user: " +username);
            }
        } 
        catch (Exception ex) 
        {
            context.getLogger().log("Email was not sent. Error message: " + ex.getMessage());
        }
        return null;
		
	}
	
	private void initDynamoDbClient() 
	{
		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(REGION)
                .build();
		dynamoDB = new DynamoDB(client);
	}
}

