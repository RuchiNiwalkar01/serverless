package com.neu.edu.lambda;

import java.text.SimpleDateFormat;
import java.util.Calendar;

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
	static final String SUBJECT = "Bills Due";
	private String textPart;
	private String htmlPart;
	private String from="";
	private String username;
	
	
	@Override
	public Object handleRequest(SNSEvent request, Context context)
	{
		
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("domain"+DOMAIN);
        from="niwalkar.r@husky.neu.edu";
        //from = "noreply@test." + DOMAIN;

        //Creating ttl
        context.getLogger().log("Invocation started: " + timeStamp);
        long now = Calendar.getInstance().getTimeInMillis()/1000; // unix time
        long ttl = 30 * 60; // ttl set to 15 min
        long totalttl = ttl + now ;

        //Function Excecution for sending the email
        
//        try {
//            JSONObject body = new JSONObject(input.getRecords().get(0).getSNS().getMessage());
//            username=body.getString("emailAddress");
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
        username=request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log("username: "+username);
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
                                        .withLong("ttl", totalttl)));

                textPart = "Test Email Body";
                context.getLogger().log("Text " + textPart);
                htmlPart = "<h2>Email sent from Amazon SES</h2>" + "<p>Username is :" + username + "</p>";
                context.getLogger().log("This is HTML body: " + htmlPart);


                //Sending email using Amazon SES client
                AmazonSimpleEmailService clients = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion(REGION).build();
                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(
                                new Destination().withToAddresses(username))
                        .withMessage(new Message()
                                .withBody(new Body()
                                        .withHtml(new Content()
                                                .withCharset("UTF-8").withData(htmlPart))
                                        .withText(new Content()
                                                .withCharset("UTF-8").withData(textPart)))
                                .withSubject(new Content()
                                        .withCharset("UTF-8").withData(SUBJECT)))
                        .withSource(from);
                clients.sendEmail(emailRequest);
                context.getLogger().log("Email sent successfully to email id: " +username);

            } else {
                context.getLogger().log("ttl is not expired. New request is not processed for the user: " +username);
            }
        } catch (Exception ex) {
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

