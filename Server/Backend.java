import java.io.File;
import java.io.FileOutputStream;

// RMI registry packages
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

// Utils, hashmaps
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Security packages


public class Backend implements Auction
{
    public static String id;

    protected Map<Integer, AuctionItem> auctionItems;
    protected Map<String, RegisteredUser> registeredUsers;
    protected Map<Integer, AuctionItemObject> auctionItemObjects;
    protected Map<AuctionItem, Integer> auctionsMap;
    protected Map<Integer, TokenInfo> userTokens;
    protected Map<Integer, String> challengeMap;

    private PrivateKey serverPrivateKey;

    private int uniqueItemID;
    private int uniqueUserID;

    // Server private key stored in local server directory
    private static String privateKeyPath = "./serverKeyPriv.key";
    private static String publicKeyPath = "./serverKey.pub";

    public Backend(String passedID)
    {
        super();
        id = passedID;

        // Create hash maps
        this.auctionItems = new HashMap<>();
        this.registeredUsers = new HashMap<>();
        this.auctionItemObjects = new HashMap<>();
        this.auctionsMap = new HashMap<>();
        this.userTokens = new HashMap<>();
        this.challengeMap = new HashMap<>();

        try
        {
            File privKeyFile = new File(privateKeyPath);
            File pubKeyFile = new File(publicKeyPath);

            if (privKeyFile.exists() && pubKeyFile.exists())
            {
                // Load existing keys
                this.serverPrivateKey = loadPrivateKey(privateKeyPath);
                PublicKey publicKey = loadPublicKey(publicKeyPath);
                System.out.println("Loaded existing RSA key pair.");
            }
            else
            {
                // Generate new key pair
                KeyPair keyPair = generateKeyPair();

                // Store keys
                storePublicKey(keyPair.getPublic(), publicKeyPath);
                storePrivateKey(keyPair.getPrivate(), privateKeyPath);

                this.serverPrivateKey = keyPair.getPrivate();
                System.out.println("Generated new RSA key pair.");
            }
        }
        catch (Exception e)
        {
            System.err.println("Exception during key pair loading/generation:");
            e.printStackTrace();
        }
    }

    // Added Methods for Level 3 =========================================================================
    // Method to provide a new unique item ID, increment and return new id
    public int serveNewItemID()
    {
        this.uniqueItemID += 1;
        return this.uniqueItemID;
    }

    // Method to provide a new unique user ID, increment and return new id
    public int serveNewUserID()
    {
        this.uniqueUserID += 1;
        return this.uniqueUserID;
    }

    // Returns an array containing AuctionItem objects from the input map
    public AuctionItem[] convertMapToArray(Map<Integer, AuctionItem> map) {
        try
        {
            AuctionItem[] itemsArray = new AuctionItem[map.size()];
            int index = 0;
            for (AuctionItem item : map.values()) {
                // Assign each AuctionItem to the array and increment the index
                itemsArray[index++] = item;
            }
            return itemsArray;
        }
        catch (Exception e)
        {
            System.out.println("Exception while converting map to array:");
            e.printStackTrace();
            return null;
        }
    }

    // Get user email by user ID, loop through hashmap of users and compare IDs
    public String getEmailByUserID(int userID)
    {
        for (RegisteredUser user : registeredUsers.values())
        {
            if (user.getID() == userID)
            {
                return user.getEmail();
            }
        }

        System.out.println("Search for user via ID: User with userID " + userID + " not found.");
        return null;
    }

    // Check if a user exists based on user ID by iterating through each user and comparing IDs
    public boolean userExists(int userID)
    {
        for (RegisteredUser user : registeredUsers.values())
        {
            if (user.getID() == userID)
            {
                return true;
            }
        }
        return false;
    }

    // Check if the item key is present in the auctionItems hashmap
    public boolean itemExists(int itemID)
    {
        return auctionItems.containsKey(itemID);
    }

    // Register a new user with a unique ID or return existing ID if the email is already registered
    public Integer register(String email, PublicKey clientPubKey) throws RemoteException
    {
        try
        {
            if (registeredUsers.containsKey(email)) // Check if user is already registered
            {
                // Return existing user ID if the email is already registered
                RegisteredUser existingUser = registeredUsers.get(email);
                existingUser.setPublicKey(clientPubKey); // Still, update key if changed
                System.out.println("User with email " + email + " already registered. Returning existing ID: " + existingUser.getID());

                return existingUser.getID();
            }
            else // Otherwise
            {
                // Register a new user with a unique ID
                int nextUserID = serveNewUserID();
                registeredUsers.putIfAbsent(email, new RegisteredUser(email, nextUserID, clientPubKey));
                System.out.println("User Registered\nEmail: " + email + "\nID: " + nextUserID);

                return nextUserID;
            }
        }
        catch(Exception e)
        {
            System.out.println("Exception for registering user:");
            e.printStackTrace();
            return null;
        }
    }

    // Get details of a specific auction item based on item ID
    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException
    {
        try
        {
            // Check if token is valid and has not expired
            if(isValidToken(userID, token) == false)
            {
                System.out.println("User (" + userID + ") has an invalid or expired token");
                return null;
            }

            // Check if item exists in hashmap
            if(itemExists(itemID) == false)
            {
                System.out.println("User tried fetching item (ID: " + itemID + ") that doesnt exist");
                return null;
            }

            // Retrieve auction item object from the map using the given ID
            AuctionItem reqItem = auctionItems.get(itemID);

            // Log details to terminal
            System.out.println("User ID: " + userID + " fetched specification of item: " + itemID);

            return reqItem;
        }
        catch(Exception e)
        {
            System.out.println("User ID: " + userID + " tried accessing item: " + itemID + "\n Exception: ");
            e.printStackTrace();

            return null;
        }
    }

    // Create a new auction with the provided AuctionItem object
    public Integer newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException
    {
        try
        {
            // Check if token is valid and has not expired
            if(isValidToken(userID, token) == false)
            {
                System.out.println("User (" + userID + ") has an invalid or expired token");
                return null;
            }

            // Check if the user exists
            if (!this.userExists(userID))
            {
                System.out.println("Non-registered user attempted to create an auction");
                return null;
            }

            // Generate a new item ID
            int newItemID = this.serveNewItemID();

            // Create auction item and object
            AuctionItem auctionItem = new AuctionItem();
            auctionItem.itemID = newItemID;
            auctionItem.name = item.name;
            auctionItem.description = item.description;
            auctionItem.highestBid = 0;

            AuctionItemObject itemObject = new AuctionItemObject(newItemID);
            itemObject.setName(item.name);
            itemObject.setDescription(item.description);
            itemObject.setReservePrice(item.reservePrice);

            // Update maps with the new auction item and object
            auctionsMap.put(auctionItem, userID);
            auctionItems.put(newItemID, auctionItem);
            auctionItemObjects.put(newItemID, itemObject);

            // Log details to server terminal
            System.out.println("New Auction Created\nItem ID: " + auctionItem.itemID +
                    "\nName: " + auctionItem.name +
                    "\nDescription: " + auctionItem.description +
                    "\nReserve Price: " + item.reservePrice);

            return newItemID;
        }
        catch(Exception e)
        {
            System.out.println("Exception while creating a new auction:");
            e.printStackTrace();
            return null;
        }
    }

    // List all auction items
    public AuctionItem[] listItems(int userID, String token) throws RemoteException
    {
        try
        {
            // Check if the user exists
            if (!this.userExists(userID))
            {
                System.out.println("Non-registered user attempted to create an auction");
                return null;
            }

            // Check if token is valid and has not expired
            if(isValidToken(userID, token) == false)
            {
                System.out.println("User (" + userID + ") has an invalid or expired token");
                return null;
            }

            // Requires converting hashmap to return an array
            AuctionItem[] itemsArray = convertMapToArray(auctionItems);

            // Log details to server terminal
            System.out.println("Auction items listed to user");
            return itemsArray;
        }
        catch(Exception e)
        {
            System.out.println("Exception while listing auction items:");
            e.printStackTrace();
            return null;
        }
    }

    // Close an auction, determine the winner, and provide the result
    public AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException
    {
        try
        {
            // Check if token is valid and has not expired
            if(isValidToken(userID, token) == false)
            {
                System.out.println("User (" + userID + ") has an invalid or expired token");
                return null;
            }

            // Check if item exists
            if(itemExists(itemID) == false)
            {
                System.out.println("User tried closing Auction Item (ID: " + itemID + ") that doesnt exist");
                return null;
            }

            // Check if the user exists
            if (!this.userExists(userID))
            {
                System.out.println("Non-registered user attempted to close an auction");
                return null;
            }

            // Fetch item and object user wishes to close
            AuctionItem closedItem = auctionItems.get(itemID);
            AuctionItemObject closedObject = auctionItemObjects.get(itemID);

            // Check if the auction belongs to the user
            if (this.auctionsMap.get(closedItem) != userID)
            {
                System.out.println("User ID " + userID + " attempted to close an auction that didn't belong to them");
                return null;
            }

            // Get the winning user and create result object to return
            RegisteredUser winningUser = auctionItemObjects.get(itemID).getHighestBidder();
            AuctionResult result = new AuctionResult();

            // If the item exists but there is no getHighestBidder, that means no one has bid on the item
            if(winningUser != null)
            {
                result.winningEmail = winningUser.getEmail();
                result.winningPrice = closedObject.getHighestBid();

                // Log details to server terminal
                System.out.println("Auction closed\nWinner: " + result.winningEmail +
                        "\nWinning Price: " + result.winningPrice);
            }
            else // So tell the user if no one has bid
            {
                result.winningEmail = null;
                result.winningPrice = closedObject.getHighestBid();

                System.out.println("Auction closed, no one bid on the item");
            }

            // Keep the item details as an object but remove from auctionItems map
            // So that the item details remain stored as an object e.g. for delivery or item purchase history
            // But remove from auctionItems so its not listed as an available item to bid on when user calls listItems
            auctionItemObjects.get(itemID).setOpen(false);
            auctionItems.remove(itemID);

            return result;
            
        }
        catch(Exception e)
        {
            System.out.println("Exception while closing auction:");
            e.printStackTrace();
            return null;
        }
    }

    // Place a bid on an auction item
    public boolean bid(int userID, int itemID, int price, String token) throws RemoteException
    {
        try
        {
            // Check if token is valid and has not expired
            if(isValidToken(userID, token) == false)
            {
                System.out.println("User (" + userID + ") has an invalid or expired token");
                return false;
            }

            // Check if the user exists
            if (!this.userExists(userID))
            {
                System.out.println("Non-registered user attempted to bid on an auction");
                return false;
            }

            // Check if item exists
            if(itemExists(itemID) == false)
            {
                System.out.println("User tried bidding on Item (ID: " + itemID + ") that doesnt exist");
                return false;
            }

            // Fetch the item and object user wishes to bid on
            AuctionItem itemToBid = auctionItems.get(itemID);
            AuctionItemObject objectToBid = auctionItemObjects.get(itemID);

            // If the proposed price is higher, they can bid on the item
            if (price > itemToBid.highestBid) {
                // Update highest bid and bidder
                itemToBid.highestBid = price;
                objectToBid.setHighestBid(price);
                objectToBid.setHighestBidder(this.registeredUsers.get(this.getEmailByUserID(userID)));

                // Log details to terminal
                System.out.println("Bid Successful\nNew Highest Bid for Item " + itemID + ": " + price);
                return true;
            }
            else // Otherise log the failed bid details
            {
                System.out.println("Bid Rejected\nCurrent Highest Bid for Item " + itemID + ": " + itemToBid.highestBid);
                return false;
            }
        }
        catch(Exception e)
        {
            System.out.println("Exception while processing bid:");
            e.printStackTrace();
            return false;
        }
    }

    // Example use: storePublicKey(aPublicKey, ‘../keys/serverKey.pub’)
    public void storePublicKey(PublicKey publicKey, String filePath) throws Exception
    {
        // Convert the public key to a byte array
        byte[] publicKeyBytes = publicKey.getEncoded();

        // Encode the public key bytes as Base64
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);

        // Write the Base64 encoded public key to a file in the shared keys directory
        try (FileOutputStream fos = new FileOutputStream(filePath))
        {
            fos.write(publicKeyBase64.getBytes());
        }
    }

    // Write server private key to file
    private void storePrivateKey(PrivateKey privateKey, String filePath) throws Exception
    {
        // Convert the private key to a byte array
        byte[] privateKeyBytes = privateKey.getEncoded();

        // Encode the private key bytes as Base64
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyBytes);

        // Write the Base64 encoded private key to a file in the server's directory
        try (FileOutputStream fos = new FileOutputStream(filePath))
        {
            fos.write(privateKeyBase64.getBytes());
        }
    }

    // Generate keypair for the server, which will subsequently be stored
    private KeyPair generateKeyPair() throws NoSuchAlgorithmException
    {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        return keyPairGenerator.generateKeyPair();
    }

    // Generate random challenge bytes and return as string
    private String generateChallenge()
    {
        SecureRandom random = new SecureRandom();
        byte[] challengeBytes = new byte[16];
        random.nextBytes(challengeBytes);

        return Base64.getEncoder().encodeToString(challengeBytes);
    }

    // Sign clients challenge using private key and return as ChallengeInfo object to authenticate server
    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException
    {
        try
        {
            // Generate a server challenge
            String serverChallenge = generateChallenge();

            // Store the server challenge associated with the user ID
            challengeMap.put(userID, serverChallenge);

            // Sign the client challenge with the servers private key
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(serverPrivateKey);
            signature.update(clientChallenge.getBytes());
            byte[] serverResponse = signature.sign();

            // Create and return the ChallengeInfo object
            ChallengeInfo challengeInfo = new ChallengeInfo();
            challengeInfo.response = serverResponse;
            challengeInfo.clientChallenge = serverChallenge;

            return challengeInfo;
        }
        catch(Exception e)
        {
            System.out.println("Exception during challenge:");
            e.printStackTrace();
            return null;
        }

    }

    // Authenticate client by using their public key given when registering and the signed challenge they passed in
    // The challenge used is the signed version of the one we sent in challenge, which was stored for verifying the client
    public TokenInfo authenticate(int userID, byte signature[]) throws RemoteException
    {
        try
        {
            // Get the user's public key
            PublicKey userPublicKey = registeredUsers.get(getEmailByUserID(userID)).getPublicKey();

            // Retrieve the stored challenge associated with the user ID
            String serverChallenge = challengeMap.get(userID);

            // Verify the client's signature using the user's public key and the stored challenge
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(userPublicKey);
            verifier.update(serverChallenge.getBytes());

            if (!verifier.verify(signature))
            {
                System.out.println("Authentication failed for user: " + userID);
                return null;
            }

            // Generate a one-time use token string
            String token = generateTokenString();

            // Set token expiration time (10 seconds)
            long expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);

            // Create and return the TokenInfo object
            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.token = token;
            tokenInfo.expiryTime = expiryTime;

            // Store the token and its expiration time
            userTokens.put(userID, tokenInfo);

            return tokenInfo;
        }
        catch (Exception e)
        {
            System.out.println("Exception during authentication:");
            e.printStackTrace();

            return null;
        }
    }

    private PrivateKey loadPrivateKey(String filePath) throws Exception
    {
        byte[] keyBytes = Base64.getDecoder().decode(Files.readAllBytes(Paths.get(filePath)));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String filePath) throws Exception
    {
        byte[] keyBytes = Base64.getDecoder().decode(Files.readAllBytes(Paths.get(filePath)));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    // Generate random token string
    private String generateTokenString()
    {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[16];
        random.nextBytes(tokenBytes);
        return Base64.getEncoder().encodeToString(tokenBytes);
    }

    // Check if the users provided token is in our map, and that it hasnt expired
    private boolean isValidToken(int userID, String token)
    {
        // Check if the token exists for the given userID
        if (userTokens.containsKey(userID))
        {
            TokenInfo tokenInfo = userTokens.get(userID);

            // Check if the token matches and has not expired
            if(tokenInfo.token.equals(token) && (System.currentTimeMillis() < tokenInfo.expiryTime))
            {
                return true;
            }
        }

        // If the userID is not found or the token is not present return false
        return false;
    }

    public int getPrimaryReplicaID() throws RemoteException
    {
        return Integer.parseInt(id);
    }

    // Main method to start the server =========================================================================
    public static void main(String[] args)
    {
        try
        {
            // Start RMI server and advertise
            Backend s = new Backend(id);
            Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(id, stub);

            System.out.println("Server Replica ready");
        }
        catch(Exception e)
        {
            System.err.println("Exception starting server:");
            e.printStackTrace();
        }
    }
}