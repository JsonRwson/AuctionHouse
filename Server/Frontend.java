import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

// Implements the auction interface to provide seamless auction function calls
public class Frontend implements Auction
{
    // Create list for replicas and variable for the primary
    private List<AuctionReplica> AuctionReplicas;
    private AuctionReplica primary;
    public static String serviceName = "FrontEnd";

    public Frontend()
    {
        // Create list to store references to running replicas
        // Elect a primary replica immediately once the front end has started
        
        AuctionReplicas = new ArrayList<>();
        AuctionReplicas = retrieveRunningReplicas();
        primary = getPrimaryReplica();
    }

    // Function to get the primary replica to use for every auction operation
    // Checks if the primary replica is down, if so, it elects a new primary and returns it
    // If the current primary replica is running fine, simply return that one
    public AuctionReplica getPrimaryReplica()
    {
        try
        {
            if(primary != null) // Check if the primary hasnt previously been elected for some reason
            {
                try
                {
                    if(primary.isAlive()) // Check if primary is alive, function simply returns true
                    {
                        System.out.println("Primary is not null and is alive, continuing..");

                        return primary;
                    }
                }
                catch (Exception e) // Catch exception for isAlive
                {
                    System.out.println("Primary is down, electing a new primary..");
                }
            }
    
            // If the primary replica is down, update the list and elect a new one
            // Important to refresh the list of running replicas first
            AuctionReplicas = retrieveRunningReplicas();
            primary = electPrimaryReplica();

            return primary;

        }
        catch(Exception e)
        {
            System.out.println("Exception retrieving the primary replica: ");
            e.printStackTrace();

            return null;
        }
    }

    // Function to elect a new primary replica, called when the frontend starts or primary is detected as down
    // Loop through each identified replica, call is alive to check if it can be elected
    public AuctionReplica electPrimaryReplica()
    {
        // Refresh list of replicas on localhost before electing a new one
        AuctionReplicas = retrieveRunningReplicas();

        try
        {
            // If the are no replicas returned in the list, we cant elect a new primary
            if(AuctionReplicas.size() == 0)
            {
                throw new Exception("No replicas active");
            }

            // Loop through replicas, call is alive, elect the first replica to properly respond and return it
            for(AuctionReplica replica : AuctionReplicas)
            {
                try
                {
                    if(replica.isAlive())
                    {
                        System.out.println("New primary replica elected, ID: " + replica.getPrimaryReplicaID());
                        return replica;
                    }
                }
                catch(Exception e)
                {
                    System.out.println("Checked replica doesnt return isAlive properly, moving on..");
                }
                
            }

            throw new Exception("No alive replicas active");
        }
        catch(Exception e)
        {
            System.out.println("Exception electing a primary replica: ");
            e.printStackTrace();
            return null;
        }
        
    }

    // Function to retrieve all running replicas on the localhost registry
    public ArrayList<AuctionReplica> retrieveRunningReplicas()
    {
        // Create a list to store and return the running replicas
        ArrayList<AuctionReplica> runningReplicas = new ArrayList<>();

        try
        {
            // Loop through all the names in the localhost registry, add each one to the list
            Registry replicaRegistry = LocateRegistry.getRegistry("localhost");

            for (String name : replicaRegistry.list())
            {
                if (!name.contains("FrontEnd")) // Ensure we arent picking up the front end in the list of replicas
                {
                    AuctionReplica replica = (AuctionReplica) replicaRegistry.lookup(name);
                    runningReplicas.add(replica);
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error fetching running replicas:");
            e.printStackTrace();
        }

        return runningReplicas;
    }

    // Implemented Auction methods, simply direct the function call to the primary replica and return the result
    // getPrimaryReplica ensures that the primary is alive before calling the auction method on it
    // Before returning the result to the user, make the primary update the state of every other replica so state is maintained

    public Integer register(String email, PublicKey pubKey) throws RemoteException
    {
        try
        {
            Integer userID = getPrimaryReplica().register(email, pubKey);
            primary.updateReplicaStates();

            return userID;
        }
        catch (Exception e)
        {
            System.out.println("Frontend Exception: ");
            e.printStackTrace();
            return null;
        }
    }

    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException
    {
        ChallengeInfo challengeInfo = getPrimaryReplica().challenge(userID, clientChallenge);
        primary.updateReplicaStates();

        return challengeInfo;
    }

    public TokenInfo authenticate(int userID, byte[] signature) throws RemoteException
    {
        TokenInfo tokenInfo = getPrimaryReplica().authenticate(userID, signature);
        primary.updateReplicaStates();

        return tokenInfo;
    }

    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException
    {
        AuctionItem auctionItem = getPrimaryReplica().getSpec(userID, itemID, token);
        primary.updateReplicaStates();

        return auctionItem;
    }

    public Integer newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException
    {
        Integer newItemID = getPrimaryReplica().newAuction(userID, item, token);
        primary.updateReplicaStates();

        return newItemID;
    }

    public AuctionItem[] listItems(int userID, String token) throws RemoteException
    {
        AuctionItem[] itemList = getPrimaryReplica().listItems(userID, token);
        primary.updateReplicaStates();

        return itemList;
    }

    public AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException
    {
        AuctionResult auctionResult = getPrimaryReplica().closeAuction(userID, itemID, token);
        primary.updateReplicaStates();

        return auctionResult;
    }

    public boolean bid(int userID, int itemID, int price, String token) throws RemoteException
    {
        boolean bidResult = getPrimaryReplica().bid(userID, itemID, price, token);
        primary.updateReplicaStates();

        return bidResult;
    }

    // This method is not called by clients and does not create state changes
    // Therefore updating replicas is not required, but the primary is still checked if it is alive
    public int getPrimaryReplicaID() throws RemoteException
    {
        return getPrimaryReplica().getPrimaryReplicaID();
    }

    // Main method, advertise front end service of application for clients to use
    public static void main(String[] args)
    {
        try
        {
            Frontend fe = new Frontend();

            Auction stub = (Auction) UnicastRemoteObject.exportObject(fe, 0);
            Registry registry = LocateRegistry.getRegistry("localhost");
            registry.rebind(serviceName, stub);

            System.out.println("Frontend service started");

        }
        catch(Exception e)
        {
            System.out.println("Exception starting frontend: ");
            e.printStackTrace();
        }
    }
}
