import java.util.HashMap;
import java.util.Map;

public class ReplicaState implements java.io.Serializable
{
    Map<Integer, AuctionItem> auctionItems;
    Map<String, RegisteredUser> registeredUsers;
    Map<Integer, AuctionItemObject> auctionItemObjects;
    Map<AuctionItem, Integer> auctionsMap;
    Map<Integer, TokenInfo> userTokens;
    Map<Integer, String> challengeMap;

    public ReplicaState()
    {
        auctionItems = new HashMap<>();
        registeredUsers = new HashMap<>();
        auctionItemObjects = new HashMap<>();
        auctionsMap = new HashMap<>();
        userTokens = new HashMap<>();
        challengeMap = new HashMap<>();
    }
}
