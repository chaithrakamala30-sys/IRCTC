# RailConnect E-ticket PDF

`TicketPdfService` uses Apache PDFBox and ZXing to render an A4 PDF and QR image from backend ticket records. `GET /api/v1/tickets/{ticketId}/download` checks passenger ownership before rendering and returns `application/pdf` with `Cache-Control: private, no-store`.

The PDF presents the RailConnect PNR, booking reference, booking and issue timestamps, ticket status, train and route, journey date, scheduled times where the run snapshot provides them, passenger names, individual passenger-ticket references, coach/berth assignments, fare paid, currency, payment status/reference, and a signed QR. It also labels the service as an independent demo and explicitly disclaims official Indian Railways or IRCTC status.

The QR contains a signed, expiring opaque verification token only. The PDF does not include database IDs, credentials, bank data, private key material, or passenger data in its metadata. PDF generation failures are written to the issue history and audit log; a later download regenerates the same ticket and PNR.

## Unicode font requirement

Configure `RAILCONNECT_TICKET_FONT_PATH` to a readable TrueType font on the deployed service. The default is `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf`; install or mount a compatible font where that path is not available. The PDF endpoint fails explicitly if the font or document generator is unavailable; it does not silently substitute or alter passenger names.
