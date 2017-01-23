package Ticket;


import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.smartcardio.CardException;
import Ultralight.UltralightCommands;
import Ultralight.UltralightUtilities;
import Ultralight.Dump;

/**
 * TODO: Complete the implementation of this class. Most of your code and
 * modifications go to this file. You will want to replace some of the example
 * code below.
 *
 * @author Tan Nguyen
 *
 */
public class Ticket {

	// Define a page-4 application tag to use for the ticket application.
	// It will be written to card memory page 4 and used to identify the
	// ticket application.

	public byte[] applicationTag = "TCKT".getBytes();

	private static final int usedMacLength = 2; // Mac length in 4-byte pages.

	//private static byte[] authenticationKey = "BREAKMEIFYOUCAN!".getBytes();// 16 byte long key

	private static byte[] authenticationKey = "!NACUFIEMKAERB!!".getBytes();// 16 byte long key

	// Dummy, not used in Java version
	private String infoToShow;

	private java.io.PrintStream msgOut; // Use this for any output to the user.
	private UltralightCommands ul;
	private UltralightUtilities utils;
	private TicketMac macAlgorithm;
	private Dump dump;

	private Boolean isValid = false;
	private Boolean isExpired = false;
	private Boolean isFirstTime = false;
	private int remainingUses = 0;
	private int expiryTime = 0;
	private int remainingDays = 0;
	private int firstTimeFlag = 1;


	public Ticket(UltralightCommands ul, java.io.PrintStream msgOut)
			throws IOException, GeneralSecurityException {
		this.msgOut = msgOut;
		this.ul = ul;
		utils = new UltralightUtilities(ul, msgOut);
		macAlgorithm = new TicketMac();

		//TODO: Change hmac key according to your need
		byte[] hmacKey = new byte[16];
		macAlgorithm.setKey(hmacKey);

		if (macAlgorithm.getMacLength() < usedMacLength * 4)
			throw new GeneralSecurityException("Bug: The MAC is too short.");
	}

	public boolean erase(boolean auth) throws CardException  {
		boolean status = true;

		if (auth)
			status = utils.authenticate(authenticationKey);
		if(!status)
			return status;
		status = utils.eraseMemory();

		return status;
	}

	// Format the card to be used as a ticket.
	public boolean format() throws CardException {
		    boolean status;
				status = utils.authenticate(authenticationKey);
			  if(!status)
				  return status;

        // Zero the card memory. Fails is any of the pages is locked.
        status = utils.eraseMemory();

        // Write the application tag to memory page 4.
        status = ul.writeBinary(4, applicationTag, 0);
				if (!status)
            return false;
        // In a real application, we probably would lock some pages here,
        // but remember that locking pages is irreversible.

        // Check the format.
        if (!checkFormat()) {
            return false;
        }

        return true;
	}

	// Check that the card has been correctly formatted.
	protected boolean checkFormat() throws CardException {
		 // Read the card contents and check that all is ok.
        byte[] memory = utils.readMemory();
        if (memory == null)
            return false;
        // Check the application tag.
        for (int i = 1; i < 4; i++)
            if (memory[4 * 4 + i] != applicationTag[i])
                return false;
        // Check zeros. Ignore page 36-39 because of the safe mode.
        for (int i = 5 * 4; i < 36 * 4; i++)
            if (memory[i] != 0)
                return false;
     // Check that the memory pages 4..39 are not locked.
	    // Lock 0: Check lock status for pages 4-7
	    if (memory[2 * 4 + 2] != 0)
	        return false;
	    // Lock 1:
	    if (memory[2 * 4 + 3] != 0)
	        return false;
	    // Lock 2:
	    if (memory[40 * 4] != 0)
	    		return false;
	    // Lock 3:
	    if (memory[40 * 4 + 1] != 0)
	    		return false;

        return true;
	}

	// Issue new tickets.
	public boolean issue(int days, int uses, int currentTime) throws CardException, GeneralSecurityException
	{
		boolean status;
		if (!checkFormat()) {
            System.err.print("Format error");
            return false;
        }

	  firstTimeFlag = 1;
		isFirstTime = true;

		//convert the firstTimeFlag, uses and days to bytes
		byte[] firstTimeFlagByte = new byte[4];
		byte[] daysByte = new byte[4];
		byte[] usesByte = new byte[4];
		firstTimeFlagByte = dump.IntToByteArray(firstTimeFlag);
		daysByte = dump.IntToByteArray(days);
		usesByte = dump.IntToByteArray(uses);

		// Page 5 for 1st_time flag, page 6 for days/expiryTime and page 7 for uses => need 8 pages
		byte[] dataOnCard = new byte[8 *4];
		//read first 5 pages from card
		utils.readPages(0, 5, dataOnCard, 0);

		// ignore page 2 and page 3 i.e. locks and OTP bits in safe mode
		if(ul.safe){
			for (int ig = 0; ig < 8; ig ++)
				dataOnCard[8 + ig] = 0;
		}
		//copy firstTimeFlag, days and uses to dataOnCard
		for (int ig = 0; ig < 4; ig ++) {
			dataOnCard[5*4 + ig] = firstTimeFlagByte[ig];
			dataOnCard[6*4 + ig] = daysByte[ig];
			dataOnCard[7*4 + ig] = usesByte[ig];
		}
		//do authentication before writing
		status = utils.authenticate(authenticationKey);
		if(!status)
			return status;
		//write firstTimeFlag, days and uses to card
    utils.writePages(firstTimeFlagByte, 0, 5, 1);
		utils.writePages(daysByte, 0, 6, 1);
		utils.writePages(usesByte, 0, 7, 1);

		//generate MAC from dataOnCard
		byte[] mac = macAlgorithm.generateMac(dataOnCard);
		if(mac == null)
			msgOut.println("Error: calculating mac.");

		// We only use 8 bytes (64 bits) of the MAC output.
		// MAC starts from page 8
		utils.writePages(mac, 0, 8, usedMacLength);

		// Change the authentication key
		utils.changeKey(authenticationKey);

		// Sets Auth0 and Auth1 settings
		utils.setAuth0(3);//Authentication is required from page 3
		utils.setAuth1(false);// true: Authentication is required for read & write; false: Authentication is require for write only

		return true;
	}


  // Add more rides to the ticket .
	public void add(int days, int uses, int currentTime) throws CardException, GeneralSecurityException
	{
			infoToShow = "Ticket Valid";
			isValid = true;

	    byte[] dataOnCard = new byte[8 * 4];
	    byte[] macOnCard = new byte[2 * 4];
	    utils.readPages(0, 8, dataOnCard, 0);
			utils.readPages(8, usedMacLength, macOnCard, 0);

			if(ul.safe){
				for (int ig = 0; ig < 8; ig ++)
					dataOnCard[8 + ig] = 0;
			}

			byte[] mac = macAlgorithm.generateMac(dataOnCard);
			if(mac == null)
			   msgOut.println("Error: calculating mac.");
			// only use 8 bytes (64 bits) of the MAC.
			for (int i = 0; i < usedMacLength*4; i++)
				if (macOnCard[i] != mac[i]) {
					infoToShow = "Invalid Ticket";
					isValid = false;
	    	}

			if (!isValid){
			 System.err.println(infoToShow);
			 return;
		  }

			//get firstTimeFlag
			firstTimeFlag = ((dataOnCard[5*4 + 0] & 0xff) << 24) | ((dataOnCard[5*4 + 1] & 0xff) << 16)
					| ((dataOnCard[5*4 + 2] & 0xff) << 8) | (dataOnCard[5*4 + 3] & 0xff);

			//get page 6 (days or expiryTime) and remainingUses
			int page6 = ((dataOnCard[6*4 + 0] & 0xff) << 24) | ((dataOnCard[6*4 + 1] & 0xff) << 16)
			    | ((dataOnCard[6*4 + 2] & 0xff) << 8) | (dataOnCard[6*4 + 3] & 0xff);

			remainingUses = ((dataOnCard[7*4 + 0] & 0xff) << 24) | ((dataOnCard[7*4 + 1] & 0xff) << 16)
			    | ((dataOnCard[7*4 + 2] & 0xff) << 8) | (dataOnCard[7*4 + 3] & 0xff);

			if (firstTimeFlag == 1)
			{
				isFirstTime = true;
				page6 += days;
				remainingDays = page6;
			}
			else //firstTimeFlag == 0
			{
				isFirstTime = false;
				expiryTime = page6;
				if (expiryTime < currentTime)
				{
					expiryTime = currentTime + days * 24 * 60;
				}
	      else
				//if ((expiryTime > currentTime) && (remainingUses >= 0))
				{
					expiryTime += (days * 24 * 60);
				}
				page6 = expiryTime;
			}
			remainingUses += uses;

			//authenticate before writing
			utils.authenticate(authenticationKey);

		  byte[] page6Bytes = new byte[4];
		  page6Bytes = dump.IntToByteArray(page6);

			//write days/expiryTime to page 6 on the card
			utils.writePages(page6Bytes, 0, 6, 1);

		  //write new remainingUses to the card
		  byte[] remainingUsesBytes = new byte[4];
		  remainingUsesBytes = dump.IntToByteArray(remainingUses);
			utils.writePages(remainingUsesBytes, 0, 7, 1);

      //read page 0-7
		  utils.readPages(0, 8, dataOnCard, 0);
		  if(ul.safe){
			   for (int ig = 0; ig < 8; ig ++)
				   dataOnCard[8 + ig] = 0;
		  }
			//re-calculate HMAC and write HMAC
		  mac = macAlgorithm.generateMac(dataOnCard);
		  if(mac == null)
			   msgOut.println("Error: calculating mac.");
		  utils.writePages(mac, 0, 8, usedMacLength);
	}

	// Use the ticket once.
	public void use(int currentTime) throws CardException,
			GeneralSecurityException {
		// Dummy ticket use that validates only the HMAC. You need to implement the rest.
		infoToShow = "Ticket Valid";
		isValid = true;
		expiryTime = 0;
    remainingUses = 0;
		isExpired = false;

    byte[] dataOnCard = new byte[8 * 4];
    byte[] macOnCard = new byte[2 * 4];
    utils.readPages(0, 8, dataOnCard, 0);
		utils.readPages(8, usedMacLength, macOnCard, 0);

		if(ul.safe){
			for (int ig = 0; ig < 8; ig ++)
				dataOnCard[8 + ig] = 0;
		}

		byte[] mac = macAlgorithm.generateMac(dataOnCard);
		if(mac == null)
			msgOut.println("Error: calculating mac.");
		// We only use 8 bytes (64 bits) of the MAC.
		for (int i = 0; i < usedMacLength*4; i++)
			if (macOnCard[i] != mac[i]) {
				infoToShow = "Invalid Ticket";
				isValid = false;
    	}

		if (!isValid){
		 System.err.println(infoToShow);
		 return;
	  }

    //get firstTimeFlag
		firstTimeFlag = ((dataOnCard[5*4 + 0] & 0xff) << 24) | ((dataOnCard[5*4 + 1] & 0xff) << 16)
				| ((dataOnCard[5*4 + 2] & 0xff) << 8) | (dataOnCard[5*4 + 3] & 0xff);

		//get page 6 (days or expiryTime)
		int page6 = ((dataOnCard[6*4 + 0] & 0xff) << 24) | ((dataOnCard[6*4 + 1] & 0xff) << 16)
				| ((dataOnCard[6*4 + 2] & 0xff) << 8) | (dataOnCard[6*4 + 3] & 0xff);

		remainingUses = ((dataOnCard[7*4 + 0] & 0xff) << 24) | ((dataOnCard[7*4 + 1] & 0xff) << 16)
				| ((dataOnCard[7*4 + 2] & 0xff) << 8) | (dataOnCard[7*4 + 3] & 0xff);

		if (firstTimeFlag == 1)
		{
      int days = page6;
			//set the flag to zero
			firstTimeFlag = 0;
			isFirstTime = false;

			//calcuate expiryTime and reduce use
			expiryTime = currentTime + days * 24 * 60;
			remainingUses--;

			byte[] firstTimeFlagByte = new byte[4];
			firstTimeFlagByte = dump.IntToByteArray(firstTimeFlag);
			byte[] remainingUseByte = new byte[4];
			remainingUseByte = dump.IntToByteArray(remainingUses);
			byte[] expiryTimeBytes = new byte[4];
			expiryTimeBytes = dump.IntToByteArray(expiryTime);
			//authenticate before writing
			utils.authenticate(authenticationKey);

			//write firstTimeFlag to card
			utils.writePages(firstTimeFlagByte, 0, 5, 1);

			//write expiryTime to card
			utils.writePages(expiryTimeBytes, 0, 6, 1);

			//write remainingUse to card
			utils.writePages(remainingUseByte, 0, 7, 1);

			dataOnCard = new byte[8 *4];
			utils.readPages(0, 8, dataOnCard, 0);
			if(ul.safe){
				for (int ig = 0; ig < 8; ig ++)
					dataOnCard[8 + ig] = 0;
			}
			mac = macAlgorithm.generateMac(dataOnCard);
			if(mac == null)
				msgOut.println("Error: calculating mac.");

		  //update HMAC to page 8,9
			utils.writePages(mac, 0, 8, usedMacLength);

			msgOut.println("Use the ticket for the first time. Start counting the validity period.");
		}

    else //firstTimeFlag == 0
		{
			isFirstTime = false;
			expiryTime = page6;
		  //validate expiryTime and remainingUses before decrement
		  if ((expiryTime > currentTime) && (remainingUses > 0))
		  {
		   remainingUses--;
	     //write new remainingUses to the card and update MAC
	     byte[] remainingUseByte = new byte[4];
	     remainingUseByte = dump.IntToByteArray(remainingUses);
		   //authenticate before writing
		   utils.authenticate(authenticationKey);
		   utils.writePages(remainingUseByte, 0, 7, 1);
		   dataOnCard = new byte[8 *4];
	     utils.readPages(0, 8, dataOnCard, 0);
	     if(ul.safe){
		     for (int ig = 0; ig < 8; ig ++)
			     dataOnCard[8 + ig] = 0;
	     }
	     mac = macAlgorithm.generateMac(dataOnCard);
	     if(mac == null)
		     msgOut.println("Error: calculating mac.");
	     utils.writePages(mac, 0, 8, usedMacLength);
	   }

	   else if ((expiryTime < currentTime) || (remainingUses == 0)) {
		  isExpired = true;
		  return;
	   }
   }
	}

	// Check the usage of the ticket (expiryTime and remainingUses)
	public void check(int currentTime) throws CardException, GeneralSecurityException
	{
			infoToShow = "Ticket Valid";
			isValid = true;

	    byte[] dataOnCard = new byte[8 * 4];
	    byte[] macOnCard = new byte[2 * 4];
	    utils.readPages(0, 8, dataOnCard, 0);
			utils.readPages(8, usedMacLength, macOnCard, 0);

			if(ul.safe){
				for (int ig = 0; ig < 8; ig ++)
					dataOnCard[8 + ig] = 0;
			}

			byte[] mac = macAlgorithm.generateMac(dataOnCard);
			if(mac == null)
			   msgOut.println("Error: calculating mac.");
			// We only use 8 bytes (64 bits) of the MAC.
			for (int i = 0; i < usedMacLength*4; i++)
				if (macOnCard[i] != mac[i]) {
					infoToShow = "Invalid Ticket";
					isValid = false;
	    	}

			if (!isValid){
			 System.err.println(infoToShow);
			 return;
		  }

			//get firstTimeFlag
			firstTimeFlag = ((dataOnCard[5*4 + 0] & 0xff) << 24) | ((dataOnCard[5*4 + 1] & 0xff) << 16)
					| ((dataOnCard[5*4 + 2] & 0xff) << 8) | (dataOnCard[5*4 + 3] & 0xff);
			//get expiryTime and remainingUses
			int page6 = ((dataOnCard[6*4 + 0] & 0xff) << 24) | ((dataOnCard[6*4 + 1] & 0xff) << 16)
			    | ((dataOnCard[6*4 + 2] & 0xff) << 8) | (dataOnCard[6*4 + 3] & 0xff);

			remainingUses = ((dataOnCard[7*4 + 0] & 0xff) << 24) | ((dataOnCard[7*4 + 1] & 0xff) << 16)
			    | ((dataOnCard[7*4 + 2] & 0xff) << 8) | (dataOnCard[7*4 + 3] & 0xff);
			if (firstTimeFlag == 1)
			{
				isFirstTime = true;
				remainingDays = page6;
			}
			else //firstTimeFlag == 0
			{
				isFirstTime = false;
			  expiryTime = page6;
			  if (expiryTime < currentTime)
			  {
				  isExpired = true;
			  }
			  else
			  {
			    isExpired = false;
		    }
			}
			return;
	}


	// After validation, get ticket status: was it valid or not?
	public boolean isValid() {
		return isValid;
	}

  //get ticket status: expired or not
	public boolean isExpired() {
		return isExpired;
	}

	//get ticket status: firsttime use or not
	public boolean isFirstTime() {
		return isFirstTime;
	}

	// After validation, get the number of remaining uses.
	public int getRemainingUses() {
		return remainingUses;
	}

	// After validation, get the number of remaining days.
	public int getDays() {
		return remainingDays;
	}

	// After validation, get the expiry time.
	public int getExpiryTime() {
		return expiryTime;
	}

}
