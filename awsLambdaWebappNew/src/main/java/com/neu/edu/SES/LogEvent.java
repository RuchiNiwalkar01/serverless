package com.neu.edu.SES;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;

public class LogEvent implements RequestHandler<SNSEvent, Object> 
{

	static DynamoDB dynamoDb;
	private String tableName = "csye6225";
	private Regions region = Regions.US_EAST_1;
	public String from = "";
	static String mailSubject = "BillsDue";
	static String htmlBody;
	static String textBody;
	static String token;
	static String username;
	 
	
	
	@Override
	public Object handleRequest(SNSEvent request, Context context)
	{
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
		System.out.println("HELLO WORLD");
	    String domain = System.getenv("DOMAIN_NAME");
	    context.getLogger().log("Domain is :"+domain);
	    from = "no-reply@test." + domain;
	    context.getLogger().log("FROM EMAIL is :"+from);
	    context.getLogger().log("Invocation started: " + timeStamp);
        long now = Calendar.getInstance().getTimeInMillis()/1000;
        long ttl = 30*60; // ttl set to 15 min
        long totalttl = ttl + now ;
        
        token = UUID.randomUUID().toString();
        
        //Mail Sending
        try {
        	 context.getLogger().log("Message rec "+request.getRecords().get(0).getSNS().getMessage());
             JSONObject body = new JSONObject(request.getRecords().get(0).getSNS().getMessage());
            username = body.getString("username");
        } 
        catch (JSONException e) 
        {
            e.printStackTrace();
        }
        context.getLogger().log("Username is: "+username);
        try 
        {
        	AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(region).build();
            dynamoDb = new DynamoDB(client);
            long ttlDbValue = 0;
            Item item = LogEvent.dynamoDb.getTable(tableName).getItem("id", username);
            if (item != null) 
            {
                context.getLogger().log("Checking for timestamp");
                ttlDbValue = item.getLong("ttl");
            }
            if (item == null || (ttlDbValue < now && ttlDbValue != 0)) 
            {
            	  context.getLogger().log("Checking for valid ttl");
                  context.getLogger().log("ttl expired, creating new token and sending email");
                  this.dynamoDb.getTable(tableName).putItem(new PutItemSpec().withItem(new Item()
                                  .withString("id", username)
                                  .withString("token", token)
                                  .withLong("ttl", totalttl)));
                  
                  
                  
                  textBody = "https://" + domain +  "/v1/bills/due/x";
                  context.getLogger().log("Text " + textBody);
                  htmlBody = "<h2>Email sent from Amazon SES</h2>"
                          + "<p>Bills Due can be view in the link below. " +
                          "Link: "+ textBody + "</p>";
                  context.getLogger().log("This is HTML body: " + htmlBody);
                  
                  //Sending email using Amazon SES client
                  AmazonSimpleEmailService clients = AmazonSimpleEmailServiceClientBuilder.standard().withRegion(region).build();
                  
                  SendEmailRequest emailRequest = new SendEmailRequest()
                          .withDestination( new Destination().withToAddresses(username))
                          .withMessage(new Message()
                                  .withBody(new Body()
                                          .withHtml(new Content()
                                                  .withCharset("UTF-8").withData(htmlBody))
                                          .withText(new Content()
                                                  .withCharset("UTF-8").withData(textBody)))
                                  .withSubject(new Content()
                                          .withCharset("UTF-8").withData(mailSubject)))
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

	

}
