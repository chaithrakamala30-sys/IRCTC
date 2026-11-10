# Phase 14: Event Catalogue & Mapping

| Event Code | Category | Priority | Trigger Source | Payload & Variables |
| :--- | :--- | :--- | :--- | :--- |
| `BOOKING_CONFIRMED` | `BOOKING` | `HIGH` | Phase 11 / Booking Engine | `bookingReference`, `trainNumber`, `travelDate` |
| `PAYMENT_SUCCESS` | `PAYMENT` | `HIGH` | Phase 12 / Dhruva Webhook | `paymentId`, `amount`, `paymentProvider` |
| `PAYMENT_FAILED` | `PAYMENT` | `URGENT` | Phase 12 / Payment Engine | `bookingReference`, `reason` |
| `TICKET_ISSUED` | `TICKET` | `HIGH` | Phase 10 / Ticket Service | `pnr`, `trainNumber` |
| `WAITLIST_PROMOTED` | `WAITLIST` | `HIGH` | Phase 12 / Promotion Engine | `passengerName`, `fromStatus`, `toStatus`, `pnr` |
| `RAC_PROMOTED` | `RAC` | `HIGH` | Phase 12 / Promotion Engine | `passengerName`, `coachNumber`, `berthNumber`, `pnr` |
| `CHART_PREPARED` | `RESERVATION` | `HIGH` | Phase 12 / Charting Engine | `trainNumber` |
| `CANCELLATION_CONFIRMED` | `CANCELLATION` | `HIGH` | Phase 11 / Cancellation Engine | `bookingReference`, `refundAmount` |
| `REFUND_COMPLETED` | `REFUND` | `HIGH` | Phase 11 / Refund Engine | `refundAmount` |
| `TRAIN_DELAYED` | `TRAIN_OPERATION` | `URGENT` | Phase 13 / Operations Centre | `trainNumber`, `delayMinutes`, `currentStation` |
| `PLATFORM_CHANGED` | `PLATFORM_CHANGE` | `URGENT` | Phase 13 / Platform Manager | `trainNumber`, `platformNumber`, `stationCode` |
| `OPERATIONAL_ANNOUNCEMENT`| `ANNOUNCEMENT` | `NORMAL` | Operations Officer / Admin | `announcementTitle`, `announcementBody` |
