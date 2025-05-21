import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class Client
{
    private static final String CLIENT_KEY_PATH = "client_key.priv";

    private static KeyPair generateKeyPair() throws Exception
    {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        return keyPairGenerator.generateKeyPair();
    }

    private static KeyPair getOrGenerateKeyPair() throws Exception
    {
        Path keyPath = Paths.get(CLIENT_KEY_PATH);

        // If key exists, load it
        if (Files.exists(keyPath)) {
            byte[] keyBytes = Files.readAllBytes(keyPath);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(keyBytes)))
            {
                return (KeyPair) ois.readObject();
            }
        }

        // Generate new key pair if none exists
        KeyPair keyPair = generateKeyPair();

        // Save the key pair
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos))
        {
            oos.writeObject(keyPair);
            Files.write(keyPath, baos.toByteArray());
        }

        return keyPair;
    }

    private static byte[] signChallenge(PrivateKey privateKey, String challenge) throws Exception
    {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(challenge.getBytes());

        return signature.sign();
    }

    // Generate random challenge String
    private static String generateChallenge()
    {
        SecureRandom random = new SecureRandom();
        byte[] challengeBytes = new byte[16];
        random.nextBytes(challengeBytes);

        return Base64.getEncoder().encodeToString(challengeBytes);
    }

    // Authenticate server challenge
    private static boolean verifyServerChallenge(byte[] serverResponse, String clientChallenge, PublicKey serverPublicKey) throws Exception
    {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(serverPublicKey);
        verifier.update(clientChallenge.getBytes());
    
        // Verify the server's response using the server's public key
        if (verifier.verify(serverResponse))
        {
            return true;
        }

        return false;
    }

    public static PublicKey getServerPublicKeyFromFile(String filePath) throws Exception
    {
        // Read the Base64 encoded public key from the file
        String publicKeyBase64 = new String(Files.readAllBytes(Paths.get(filePath)));

        // Decode the Base64 string to get the original public key bytes
        byte[] decodedKeyBytes = Base64.getDecoder().decode(publicKeyBase64);

        // Generate the public key from the decoded bytes
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        
        return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKeyBytes));
    }

    public static void main(String[] args)
    {
        // Check if there are enough arguments to parse at least email and function choice
        if (args.length < 2)
        {
            System.out.println("\nUsage: java Client (user email) (function) (arguments)"
            + "\nAccepted functions:"
                    + "\n    (email) getSpec (itemID)"
                    + "\n    (email) newAuction (itemName) (reserverPrice) (itemDescription)"
                    + "\n    (email) closeAuction (itemID)"
                    + "\n    (email) listItems"
                    + "\n    (email) bid (itemID) (bidPrice)\n");
            return;
        }

        // Parse email and chosen function as variables
        String userEmail = args[0];
        String userFunction = args[1];
        System.out.println("User Function: " + userFunction);

        try
        {
            // Connect to RMI service
            String serviceName = "FrontEnd";
            Registry registry = LocateRegistry.getRegistry("localhost");
            Auction server = (Auction) registry.lookup(serviceName);

            String serverKeyFilePath = "../keys/serverKey.pub";
            PublicKey serverPublicKey = getServerPublicKeyFromFile(serverKeyFilePath);    

            // Get or generate key pair for the client
            KeyPair keyPair = getOrGenerateKeyPair();

            // Register the user with the server, including the public key so that the server can verify the client
            PublicKey clientPublicKey = keyPair.getPublic();
            int userID = server.register(userEmail, clientPublicKey);
            System.out.println("Current UserID: " + userID + "\n");

            // Perform 3-way authentication
            // Generate challenge string for server to sign, and recieve as challengeInfo object
            String challengeToUse = generateChallenge();
            ChallengeInfo challengeInfo = server.challenge(userID, challengeToUse);

            // Sign servers generated challenge string using private key
            byte[] clientSignature = signChallenge(keyPair.getPrivate(), challengeInfo.clientChallenge);
            
            // Check if the server has returned the correct signature for the challenge we gave them
            boolean isServerChallengeVerified = verifyServerChallenge(challengeInfo.response, challengeToUse, serverPublicKey);
            
            // If the challenge is verified, we know we are talking to the right server
            if(isServerChallengeVerified == false)
            {
                System.out.println("Server challenge verification failed. Authentication unsuccessful.");
                // System.exit(0);
            }

            // Call authenticate, passing the signed challenge, server will use our pub key given in registration
            TokenInfo tokenInfo = server.authenticate(userID, clientSignature);
            String userToken = tokenInfo.token; // Store returned one-time token for auction operations

            // Switch on users chosen function for different auction operations
            switch(userFunction)
            {
                case "getSpec": // Use given function string in cmd arguments
                    // Get detailed information about a specific item
                    int aucID = Integer.parseInt(args[2]);
                    AuctionItem fetchedItem = server.getSpec(userID, aucID, userToken);

                    if(fetchedItem != null) // Check if returned object is null, if not, print details
                    {
                        System.out.println(String.format("id: %d\nname: %s\ndescription: %s\nhighest bid: %d",
                        fetchedItem.itemID, fetchedItem.name, fetchedItem.description, fetchedItem.highestBid));
                    }
                    else // Tell the user if the item doesnt exist 
                    {
                        System.out.println("Item returned was null: it probably doesnt exist");
                    }
                    
                    break;

                case "newAuction":
                    // Create a new auction listing
                    String newItemName = args[2];
                    int resPrice = Integer.parseInt(args[3]);
                    
                    // Description is the last remaining arguments, parsed as one string
                    StringBuilder itemDescBuilder = new StringBuilder();

                    for (int i = 4; i < args.length; i++)
                    {
                        itemDescBuilder.append(args[i]).append(" ");
                    }

                    String itemDesc = itemDescBuilder.toString().trim();

                    // Generate object to pass to server for listing an item, giving details from cmd line
                    AuctionSaleItem saleItem = new AuctionSaleItem();
                    saleItem.name = newItemName;
                    saleItem.description = itemDesc;
                    saleItem.reservePrice = resPrice;
                    
                    // Fetch returned item ID
                    int itemID = server.newAuction(userID, saleItem, userToken);
                    
                    // TimeUnit.SECONDS.sleep(11); // TEST FOR 10 SEC EXPIRED TOKEN LEAVE COMMENTED
                    AuctionItem listedItem = server.getSpec(userID, itemID, userToken);
                    System.out.println(String.format("item listed:\nid: %d\nname: %s\ndescription: %s\nhighest bid: %d",
                        listedItem.itemID, listedItem.name, listedItem.description, listedItem.highestBid));

                    break;
                
                case "closeAuction":
                    // Close an ongoing auction
                    int closeItemID = Integer.parseInt(args[2]);
                    AuctionResult res = server.closeAuction(userID, closeItemID, userToken);

                    if(res == null) // Check if result is empty, therefore auction could not be closed
                    {
                        System.out.println("Problem closing auction, make sure it exists AND belongs to you");
                        break;
                    }
                    else if(res.winningEmail == null) // Check if there was a winner, if not, no one bid on the item
                    {
                        System.out.println("Auction closed, no one bid on the item");
                        break;
                    }
                    else // Otherwise, the auction closed properly and had a winner
                    {
                        System.out.println("Auction closed\nwinner: " + res.winningEmail + "\nbid: " + res.winningPrice);
                        break;
                    }

                case "listItems":
                    // List all available auction items
                    AuctionItem[] items = server.listItems(userID, userToken);

                    System.out.println("All Items Listed:\n");
                    if(items.length >= 1) // 
                    {
                        for(int x = 0; x < items.length; x++)
                        {
                            System.out.println(String.format("\nid: %d\nname: %s\ndescription: %s\nhighest bid: %d\n\n",
                            items[x].itemID, items[x].name, items[x].description, items[x].highestBid));
                        }
                    }
                    else
                    {
                        System.out.println("There are currently no items listed");
                    }

                    break;

                case "bid":
                    // Place a bid on an auction item
                    int bidItemID = Integer.parseInt(args[2]);
                    int bidPrice = Integer.parseInt(args[3]);
            
                    boolean bidResult = server.bid(userID, bidItemID, bidPrice, userToken);

                    if(bidResult == true)
                    {
                        System.out.println("Bid successful!");
                    }
                    else
                    {
                        System.out.println("Bid unsuccessful. Check the current highest bid and try again.");
                    }

                    break;
                
                default:
                    // Display available functions if an invalid one is provided
                    System.out.println("\nFunction not valid, Accepted functions:"
                    + "\n    (email) getSpec (itemID)"
                    + "\n    (email) newAuction (itemName) (reserverPrice) (itemDescription)"
                    + "\n    (email) closeAuction (itemID)"
                    + "\n    (email) listItems"
                    + "\n    (email) bid (itemID) (bidPrice)\n");
                    break;
            }
        }

        catch(Exception e)
        {
            // Handle any exceptions that occur
            System.out.println("Exception: ");
            e.printStackTrace();
        }        
    }
}