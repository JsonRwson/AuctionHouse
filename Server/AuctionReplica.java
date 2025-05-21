import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.PublicKey;

public interface AuctionReplica extends Remote 
{
    // A remote interface for the front end to call operations on replicas
    // Similar to the auction interface but with added methods for passive replication

    public ReplicaState getStateObject() throws RemoteException;
    public boolean updateStateObject(ReplicaState updatedState) throws RemoteException;
    public boolean updateReplicaStates() throws RemoteException;
    public boolean isAlive() throws RemoteException;

    public Integer register(String email, PublicKey pubKey) throws RemoteException;
    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException;
    public TokenInfo authenticate(int userID, byte signature[]) throws RemoteException;
    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException;
    public Integer newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException;
    public AuctionItem[] listItems(int userID, String token) throws RemoteException;
    public AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException;
    public boolean bid(int userID, int itemID, int price, String token) throws RemoteException;
    public int getPrimaryReplicaID() throws RemoteException;
}
