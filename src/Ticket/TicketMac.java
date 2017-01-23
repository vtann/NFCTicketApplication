package Ticket;

import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TicketMac {

	// Set here the secret key that will be used for the MAC. The same key
	// must be known both by the ticket issuer and checker. In a real
	// application, the key would be distributed securely in a file or in a
	// smart
	// card and not embedded in the code.

	private static SecretKeySpec hmacKey;
	private Mac mac;
	private boolean isKeySet = false;

	public TicketMac() throws GeneralSecurityException {
		isKeySet = false;
        hmacKey = null;
	}

	public void setKey(byte[] key) throws GeneralSecurityException {
		hmacKey = new SecretKeySpec(key, "HmacSHA1");
		mac = Mac.getInstance("HmacSHA1");
	    mac.init(hmacKey);    
		isKeySet = true;
	}
	
	public byte[] generateMac(byte[] data) throws GeneralSecurityException {
		if(!isKeySet)
			return null;
		mac.reset();
		return mac.doFinal(data);
	}

	public int getMacLength() {
		if(!isKeySet)
			return 0;
		return mac.getMacLength();
	}

}
