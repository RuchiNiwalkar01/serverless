# CSYE 6225 - Spring 2020 - Lambda


## Technology Stack
The application uses Lambda function to trigger email when message is sent to SQS and polled in a seaparate thread and then published to SNS topic. The lambda function is created in AWS console. Dynamodb is used to store token which indicates time to live.


## Build Instructions
To trigger lambda function to send email, clone git repository git@github.com:niwalkarr-spring2020/serverless.git


## Build Deploy
trigger the Code Deployment using curl command to call the circleci API


## CICD
1. For Circleci to read the config.yml and set inputs in CircleCI environment variables.
2. Setup your circleci user credentials in circle ci environment which is created in AWS console.
3. Setup code deploy bucket name which is the bucket created in AWS console for code deploy to upload the s3 artifact.
4. Setup the region in circle ci environment variables where the code deloy should take place.
5. Specify the branch name in circleci for which build needs to be triggered Command to trigger CICD curl.

