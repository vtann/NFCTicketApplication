1.Ticket layout:

Page 0,1: 7 bytes UID

Page 2: lock bytes

Page 3: OTP

Page 4: application tag TCKT

Page 5: first-time-use tag. Stores 1 if the card has never been used by customer and 0 if the
card has already been used.

Page 6:
If first-time-use tag in page 5 is 1, page 6 stores the validity period (number of days).
If the tag in page 5 is 0, page 6 stores the expiry Time.

Page 7: stores the remaining uses of the ticket

Page 8, 9: stores MAC. This MAC is calculated from page 0à7 and only 8 bytes are stored.

Page 10 à 43: Not in use

Page 44 à 47: Authentication key

2.Security mechanism:
After issuing the card, an authentication key is stored in page 44 à 47 in the card and
unreadable. From this time, the card has to be authenticated (the key in the card has to be
matched with the key on the application) before being written (adding or use the ticket).

To validate if the card is valid or not, the data in the card from page 0-7 will be calculated to a
MAC using SHA1 algorithm and then stores only 8 bytes MAC to page 8-9. When the
application validates the card, it will read data in page 0-7 to compute the MAC and compare
with the MAC stored in page 8-9. If the MAC is matched, the validation is passed and then the
application starts writing new data to the card.

3.Application’s features

Erase: erase the ticket to blank

Format: erase the card and add application tag “TCKT” to page 4

Issue: add 1-day validity period and 10 uses to the card, set the authentication key and
authentication configuration (AUTH0 and AUTH1)

Add: add 1-day validity period and 10 uses to the card without deleting old usages
o If the card is already expired (expiry time < current time):
new expiry time = current time + validity period
o If expiry time > = current time:
new expiry time = old expiry time + validity period

Use: reduce 1 use from remaining uses
First time use: Validity period will start counting from current time.

Check: check the usage of the card such as expiry time, remaining uses.
