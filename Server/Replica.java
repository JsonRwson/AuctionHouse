import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

// A Replica object inherits the Backend class
// The Backend contains all functionality for auction operations, authentication and storing data
// The Replica class builds upon the backend providing methods specific to implementing passive replication
// Backend was previously the class for a sole server, now starting the rmi services has been moved to this class
public class Replica extends Backend implements AuctionReplica
{
    public static String replicaID;

    // Call the Backend constructor which generates keypairs and sets up the data structures
    // Try to update state from other replicas, this means new replicas can be added during runtime of others
    public Replica(String id)
    {
        super(id);
        updateCurrentState();
    }

    // Check called by the frontend to see if the replica is responding properly
    public boolean isAlive() throws RemoteException
    {
        return true;
    }

    // Fetch all the other replicas Except for the one calling this function
    public ArrayList<AuctionReplica> retrieveExclusiveRunningReplicas()
    {
        try
        {
            // Create a list to store all remote objects on the localhost registry 
            ArrayList<AuctionReplica> runningReplicas = new ArrayList<>();
            Registry replicaRegistry = LocateRegistry.getRegistry("localhost");

            // Go through each remote object add them to the list if they arent the frontend and this replica
            for (String name : replicaRegistry.list())
            {
                if (!name.contains("FrontEnd") && !name.contains(replicaID))
                {
                    AuctionReplica replica = (AuctionReplica) replicaRegistry.lookup(name);
                    runningReplicas.add(replica);
                }
            }
            
            return runningReplicas; // Return the completed list
        }
        catch (Exception e)
        {
            System.out.println("Exception fetching running replicas:");
            e.printStackTrace();

            return null;
        }
    }

    // Function for a replica to update its own state from any other replicas it can find
    // This function is called when a replica is spun up so it can get upto date on stored data
    public boolean updateCurrentState()
    {
        try
        {
            // First fetch all other running replicas
            ArrayList<AuctionReplica> runningReplicasExclusive = retrieveExclusiveRunningReplicas();

            // Check if any replica actually exists to update state from
            if(runningReplicasExclusive.isEmpty())
            {
                System.out.println("No other replicas to update state from!");
                return false; // no other replicas to update state from
            }

            // Try to retrieve state from each replica found
            // Copy the state from the first replica to properly return a state object
            for (AuctionReplica replica : runningReplicasExclusive)
            {
                try
                {
                    System.out.println("Updating state from replica ID: " + replica.getPrimaryReplicaID());
                    updateStateObject(replica.getStateObject());

                    return true;
                }
                catch (Exception e)
                {
                    System.out.println("Cant update state from this replica, its probably not alive, trying another");
                }
            }

            System.out.println("Other replicas exist, but state cannot be retrieved from them to update from");
            return false;
        }
        catch(Exception e)
        {
            System.out.println("Exception updating current replica state:");
            e.printStackTrace();

            return false;
        }
        
    }

    // Creates a state object storing all the hashmap data required for auction operations
    // This is called by other replicas to retrieve state from another replica
    public ReplicaState getStateObject() throws RemoteException
    {
        // Create a state object and fill it with all the local data
        ReplicaState state = new ReplicaState();

        state.auctionItems = auctionItems;
        state.registeredUsers = registeredUsers;
        state.auctionItemObjects = auctionItemObjects;
        state.auctionsMap = auctionsMap;
        state.userTokens = userTokens;
        state.challengeMap = challengeMap;

        return state; // Return the filled object

    }

    // Update the locally stored hashmaps with a given state object 
    public boolean updateStateObject(ReplicaState updatedState) throws RemoteException
    {
        // Set local values to the the corrosponding data stored in the state object
        auctionItems = updatedState.auctionItems;
        registeredUsers = updatedState.registeredUsers;
        auctionItemObjects = updatedState.auctionItemObjects;
        auctionsMap = updatedState.auctionsMap;
        userTokens = updatedState.userTokens;
        challengeMap = updatedState.challengeMap;

        return true;
    }

    // Function to update the state of every other replica, this will be called on the primary replica when it performs an auction operation
    public boolean updateReplicaStates() throws RemoteException
    {
        try
        {
            // Fetch the list of running remote replica objects
            ArrayList<AuctionReplica> runningReplicasExclusive = retrieveExclusiveRunningReplicas();

            // If the list is empty, there are no other replicas to update state to
            if(runningReplicasExclusive.isEmpty())
            {
                System.out.println("No other replicas to update state to!");
                return false; // no other replicas to update state from
            }

            // Loop through each replica found and try to call the update state on it
            // Pass in the current state of this replica as an object using the getStateObject
            for(AuctionReplica replica : runningReplicasExclusive)
            {
                try
                {
                    replica.updateStateObject(getStateObject());
                    System.out.println("Updated state of another replica, ID: " + replica.getPrimaryReplicaID());
                }
                catch (Exception e)
                {
                    System.out.println("Cant update state of another replica, its probably not alive");
                }
            }

            return true;
        }
        catch(Exception e)
        {
            System.out.println("Exception updating other replicas: ");
            e.printStackTrace();
            
            return false;
        }
    }

    // Run a new replica, advertise the Replica object on localhost
    // Assign and advertise its name as the one given in the commandline argument
    public static void main(String[] args)
    {
        try
        {
            replicaID = args[0]; // Fetch id from cl argument
            Replica replica = new Replica(replicaID);
            AuctionReplica stub = (AuctionReplica) UnicastRemoteObject.exportObject(replica, 0);
            Registry newReplicaRegistry = LocateRegistry.getRegistry();
            newReplicaRegistry.rebind(replicaID, stub); // Advertise its name as the one given as a cl argument
        }
        catch(Exception e)
        {
            System.out.println("Exception Starting Replica: ");
            e.printStackTrace();
        }
    }
    
}
