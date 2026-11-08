# Passenger fare quote guide

On either mobile or laptop, open train details from a search result, select a coach class and passenger category, and request a quote. If the selected category has configured age boundaries, enter each passenger's age. Add passengers as needed and review the itemized backend-calculated total and expiry.

Use **Refresh quote** to request a newly calculated snapshot. The refreshed quote can change if an administrator has published a different rule; an earlier quote is never extended. If the service responds that fare configuration is unavailable, no approved rule covers that journey/class/date. The application does not invent a fare.

An estimate is not a ticket, reservation, availability indication, guaranteed price, or payment authorization. This page does not allocate a seat or create a booking.

The interface presents the same quote flow through the dedicated `/mobile/train-details` and `/laptop/train-details` routes and reflows its controls, passenger rows, and itemized breakdown for small screens.
