import java.security.PublicKey;

public class RegisteredUser implements java.io.Serializable
{
    private String email;
    private int userID;
    private PublicKey publicKey;

    public RegisteredUser(String pEmail, int pUserID, PublicKey key)
    {
        this.email = pEmail;
        this.userID = pUserID;
        this.publicKey = key;
    }

    public String getEmail()
    {
        return this.email;
    }

    public int getID()
    {
        return this.userID;
    }

    public PublicKey getPublicKey()
    {
        return this.publicKey;
    }

    public void setPublicKey(PublicKey pubKey)
    {
        this.publicKey = pubKey;
    }
}
