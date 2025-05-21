import java.io.Serializable;

public class AuctionItemObject implements Serializable
{
    private int ID;
    private String name;
    private String description;
    private int reservePrice;
    private int highestBid;
    private RegisteredUser highestBidder;
    private Boolean isOpen;
    private AuctionItem matchingItem;

    public AuctionItemObject(int ID)
    {
        this.ID = ID;
        this.reservePrice = 0;
        this.highestBid = 0;
        this.isOpen = true;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return this.description;
    }

    public void setDescription(String desc)
    {
        this.description = desc;
    }

    public int getReservePrice()
    {
        return this.reservePrice;
    }

    public void setReservePrice(int price)
    {
        this.reservePrice = price;
    }

    public int getHighestBid()
    {
        return this.highestBid;
    }

    public void setHighestBid(int bid)
    {
        this.highestBid = bid;
    }

    public RegisteredUser getHighestBidder()
    {
        return this.highestBidder;
    }

    public void setHighestBidder(RegisteredUser user)
    {
        this.highestBidder = user;
    }

    public boolean isOpen()
    {
        return this.isOpen;
    }

    public void setOpen(boolean status)
    {
        this.isOpen = status;
    }

    public AuctionItem getMatchingItem()
    {
        return this.matchingItem;
    }

    public void setAuctionItem(AuctionItem item)
    {
        this.matchingItem = item;
    }
}
