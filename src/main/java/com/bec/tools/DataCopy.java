package com.bec.tools;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import org.apache.commons.collections4.iterators.ReverseListIterator;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <ol>
 * <li>GMAIL API <a href="https://mailtrap.io/blog/send-emails-with-gmail-api/">Quickstart Guide</a>-Be sure to add yourself as a tester (see <a href="https://stackoverflow.com/a/66054825">Stackoverflow question</a>)</li>
 * <li>What's not mentioned in the guide is that you need to make sure the correct Google Cloud project is selected in the upper left, and that after you enable the Gmail API click->Enable APIs & services->Gmail API->CREDENTIALS->"+ CREATE CREDENTIALS" then select OAuth Client ID->Application Type = Desktop app->CREATE.</li>
 * <li>Your first run will prompt you to log into your google account and give this app "bec-datacopy" access. After you do you will see it show up under <a href="https://myaccount.google.com/permissions?continue=https://myaccount.google.com/security">Third-party apps with account access</a>.</li>
 * </ol>
 */
public class DataCopy
{
    // App Name
    private static final String APPLICATION_NAME = "Gmail API Java - Bart's Electric DataCopy";
    // Global instance of the JSON factory.
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    // Directory to store authorization tokens for this application.
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * <b>NOTE:</b> Global instance of the scopes required by this quickstart.<br>
     * If modifying these scopes, <b>delete</b> your previously saved tokens/ folder.<br>
     * *
     * Check security in you <a href="https://myaccount.google.com/permissions?continue=https://myaccount.google.com/security">Third-party apps with account access</a>. (i.e. #3 above)
     */
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_SEND);
    private static final String CREDENTIALS_FILE_PATH = "/google-gmail-api-credentials.json";

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException
    {
        // Load client secrets.
        InputStream in = DataCopy.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null)
        {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        // Returns an authorized Credential object.
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
    
    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to email address of the receiver
     * @param from email address of the sender, the mailbox account
     * @param subject subject of the email
     * @param bodyText body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     */
    public static MimeMessage createEmail(String to,
                                          String from,
                                          String subject,
                                          String bodyText) throws MessagingException
    {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(to));
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }

    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     */
    public static Message createMessageWithEmail(MimeMessage emailContent) throws MessagingException, IOException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        
        return message;
    }

    /**
     * Send an email from the user's mailbox to its recipient.
     *
     * @param service Authorized Gmail API instance.
     * @param userId User's email address. The special value "me"
     * can be used to indicate the authenticated user.
     * @param emailContent Email to be sent.
     * @return The sent message
     * @throws MessagingException
     * @throws IOException
     */
    public static Message sendMessage(Gmail service,
                                      String userId,
                                      MimeMessage emailContent)
            throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        message = service.users().messages().send(userId, message).execute();

        System.out.println("Message id: " + message.getId());
        System.out.println(message.toPrettyString());
        return message;
    }

    /**
     *
     * @param fromPath
     * @param toPath
     * @return
     */
    private static Path getToPath(Path fromPath, Path toPath)
    {
//        System.out.println("parent: " + fromPath.getParent());

        // If it doesn't have a parent then it should be a root drive (ex. d:\) so just exit.
        if( fromPath.getParent() == null )
        {
            return null;
        }

        // ex. [d:\, \\ds1\share, etc.] + \ + [my_folder, my_folder\my_folder2, etc.]
//        System.out.println(" child: " + fromPath.toString().replace(fromPath.getRoot().toString(), ""));
        return Paths.get(toPath.toString(), fromPath.toString().replace(fromPath.getRoot().toString(), ""));
    }



    public static void main(String[] args) throws IOException, GeneralSecurityException, MessagingException
    {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                                 .setApplicationName(APPLICATION_NAME)
                                 .build();
        sendMessage(service,"me", createEmail("johnny@naosoft.us.com","jaulner@gmail.com",
                                                    "Test email","It works!!"));

        System.exit(0);


        final Scanner userInput = new Scanner(System.in);

        System.out.print("Enter # of Dir to Process for output: ");
        final int modForDir = userInput.nextInt();
        userInput.nextLine();


        System.out.print("FROM Location [ex. d:\\, d:\\my_folder, \\\\host\\share]: ");
//        String in = userInput.nextLine().replace("\\","/");
//        System.out.println("in:" + in);
//        Path fromPath = Paths.get(in);
        final Path fromPath = Paths.get(userInput.nextLine());
//        System.out.println("fromPath.toString: " + fromPath.toString());
//        System.out.println("fromPath.getFileName: " + fromPath.getFileName());
//        System.out.println("fromPath.getParent: " + fromPath.getParent());
//        System.out.println("fromPath.getRoot: " + fromPath.getRoot());


        System.out.print("TO Location [ex. d:\\, d:\\my_folder, \\\\host\\share]: ");
        final Path toPath = Paths.get(userInput.nextLine());
//        System.out.println("toPath.toString: " + toPath.toString());
//        System.out.println("toPath.toAbsolutePath: " + toPath.toAbsolutePath());
//        System.out.println("toPath.getFileName: " + toPath.getFileName());
//        System.out.println("toPath.getParent: " + toPath.getParent());
//        System.out.println("toPath.getRoot: " + toPath.getRoot());

        // Verify TO and FROM destinations
        System.out.print("Are you sure you want copy files FROM \"" + fromPath + "\" -> TO \"" + toPath + "\" [YES]? ");
        if(!userInput.nextLine().equals("YES"))
        {
            System.out.println("exiting..");
            System.exit(0);
        }

        LocalDateTime startDateTime = LocalDateTime.now();
        System.out.println("Start Date/Time: " + startDateTime.toString());
//        System.out.println("Start Date/Time: " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(startDateTime));

        // Create directories on TO side
        try (Stream<Path> walk = Files.walk(fromPath))
        {

            final List<Path> result = walk.filter(Files::isDirectory) //.filter(Files::isRegularFile)
                    .collect(Collectors.toList());


            // Copy files by directory in reverse order
            ReverseListIterator<Path> reverseListIterator = new ReverseListIterator(result);
            Long dirCount = 0L;

            while (reverseListIterator.hasNext())
            {
                dirCount++;

                final Path fromDir = reverseListIterator.next();
//                System.out.println(fromDir.toString());

                final Path toDir = getToPath(fromDir, toPath);
                if(toDir != null)
                {
                    if(dirCount % modForDir == 0)
                    {
                        System.out.println("processing directory: " + toDir);
                    }

                    // Create Directory on TO side if it does NOT exist.
                    if( !Files.exists(toDir) )
                    {
//                        System.out.println("creating..");
                        Files.createDirectories(toDir);
//                        Files.createDirectory(toDir);
                    }

                    // copy files - outside of the create directory if block incase a failure occured during file copying
                    // Copy files
                    try (Stream<Path> walkFiles = Files.walk(fromDir))
                    {
                        List<Path> files = walkFiles.filter(Files::isRegularFile)
                                .collect(Collectors.toList());

//                        files.forEach(System.out::println);
                        files.forEach(fromFile -> {
//                            System.out.println("copy file: " + fromFile.toString());
                            final Path toFile = getToPath(fromFile, toPath);
                            if( !Files.exists(toFile) )
                            {
                                try
                                {
//                                    System.out.println("create file: " + toFile);
                                    Files.copy(fromFile, toFile);
                                }
                                catch (Exception ex)
                                {
                                    System.out.println(ex.getStackTrace());
                                    throw new IllegalStateException("Failed to create directory: " + ex.getMessage());
                                }
                            }
                        });
                    }
                }

            }

            LocalDateTime endDateTime = LocalDateTime.now();
            System.out.println("End Date/Time: " + endDateTime.toString());

            Duration duration = Duration.between(startDateTime, endDateTime);
            System.out.println("duration in minutes: " + duration.toMinutes());
        }
        catch (IOException e)
        {
            e.printStackTrace();
            
            // Send email to restart the job
            sendMessage(service,"me", 
                    createEmail("johnny@naosoft.us.com","jaulner@gmail.com", "DataCopy Job Died: ",e.getMessage()));

        }

    }

}
