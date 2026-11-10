package in.railconnect.core.ticket;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import in.railconnect.core.ticket.TicketModels.TicketPassenger;
import in.railconnect.core.ticket.TicketModels.TicketView;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class TicketPdfService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketPdfService.class);
    private static final String PLATFORM = "RAILCONNECT - INDEPENDENT DEMO RAILWAY RESERVATION PLATFORM";
    private static final String DISCLAIMER = "This ticket is for RailConnect's independent simulated railway service. "
            + "It is not an official Indian Railways or IRCTC ticket.";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z").withZone(ZoneId.of("Asia/Kolkata"));

    private final TicketIssuanceService tickets;
    private final String verificationOrigin;
    private final Path fontPath;

    public TicketPdfService(
            TicketIssuanceService tickets,
            @Value("${railconnect.tickets.public-verification-origin}") String verificationOrigin,
            @Value("${railconnect.tickets.font-path}") String fontPath
    ) {
        this.tickets = tickets;
        this.verificationOrigin = validateOrigin(verificationOrigin);
        this.fontPath = Path.of(fontPath);
    }

    public byte[] generate(TicketView ticket, java.util.UUID actorId) {
        try {
            if (!Files.isRegularFile(fontPath) || !Files.isReadable(fontPath)) {
                throw new TicketException(HttpStatus.SERVICE_UNAVAILABLE, "TICKET_PDF_FONT_UNAVAILABLE",
                        "The ticket document service is temporarily unavailable");
            }
            String token = tickets.verificationToken(ticket.ticketId());
            byte[] qr = qrPng(verificationOrigin + "?token=" + token);
            try (PDDocument document = new PDDocument();
                 var fontStream = Files.newInputStream(fontPath);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                var font = PDType0Font.load(document, fontStream);
                PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qr, "ticket-verification");
                document.getDocumentInformation().setTitle("RailConnect electronic ticket");
                try (PDPageContentStream content = new PDPageContentStream(document, page, AppendMode.OVERWRITE, true)) {
                    drawTicket(content, font, qrImage, ticket, token);
                }
                document.save(output);
                byte[] pdf = output.toByteArray();
                tickets.recordPdfOutcome(ticket.ticketId(), actorId, true);
                return pdf;
            }
        } catch (TicketException exception) {
            LOGGER.warn("Ticket PDF generation failed; code={}, ticketId={}", exception.code(), ticket.ticketId());
            tickets.recordPdfOutcome(ticket.ticketId(), actorId, false);
            throw exception;
        } catch (IOException | WriterException | RuntimeException exception) {
            LOGGER.warn("Ticket PDF generation failed; exceptionType={}, ticketId={}",
                    exception.getClass().getName(), ticket.ticketId());
            tickets.recordPdfOutcome(ticket.ticketId(), actorId, false);
            throw new TicketException(HttpStatus.SERVICE_UNAVAILABLE, "TICKET_PDF_GENERATION_FAILED",
                    "The ticket document could not be generated");
        }
    }

    private static void drawTicket(
            PDPageContentStream content,
            org.apache.pdfbox.pdmodel.font.PDFont font,
            PDImageXObject qr,
            TicketView ticket,
            String token
    ) throws IOException {
        content.setNonStrokingColor(new Color(16, 63, 117));
        content.addRect(0, 770, PDRectangle.A4.getWidth(), 72);
        content.fill();
        text(content, font, PLATFORM, 35, 810, 12, Color.WHITE);
        text(content, font, "RAILCONNECT PNR", 35, 744, 11, new Color(87, 100, 120));
        text(content, font, ticket.pnr(), 35, 716, 25, new Color(16, 63, 117));
        content.drawImage(qr, 430, 680, 125, 125);
        text(content, font, "Scan to verify RailConnect record", 410, 667, 8, new Color(87, 100, 120));

        text(content, font, ticket.trainNumber() + "  " + ticket.trainName(), 35, 675, 15, Color.BLACK);
        text(content, font, ticket.boardingStation() + "  to  " + ticket.destinationStation(),
                35, 650, 11, Color.BLACK);
        text(content, font, "Journey " + ticket.journeyDate()
                + "   Class " + ticket.coachClassCode(), 35, 628, 10, Color.BLACK);
        text(content, font, "Boarding " + value(ticket.boardingTime())
                + "   Arrival " + value(ticket.arrivalTime()), 35, 608, 10, Color.BLACK);
        text(content, font, "Booking " + ticket.bookingReference()
                + "   Booked " + TIMESTAMP.format(ticket.bookingCreatedAt()), 35, 586, 9, Color.BLACK);
        text(content, font, "Issued " + TIMESTAMP.format(ticket.issuedAt())
                + "   Status " + ticket.ticketStatus(), 35, 568, 9, Color.BLACK);
        text(content, font, "Payment " + value(ticket.paymentStatus())
                + "   Reference " + value(ticket.paymentReference()), 35, 550, 9, Color.BLACK);
        text(content, font, "Total paid " + ticket.currency() + " " + ticket.totalPaid(), 35, 532, 10, Color.BLACK);

        content.setStrokingColor(new Color(210, 218, 228));
        content.moveTo(35, 514);
        content.lineTo(560, 514);
        content.stroke();
        text(content, font, "PASSENGERS AND ALLOCATIONS", 35, 494, 10, new Color(16, 63, 117));
        float y = 470;
        for (TicketPassenger passenger : ticket.passengers()) {
            text(content, font, passenger.passengerNumber() + ". " + passenger.passengerName(),
                    35, y, 10, Color.BLACK);
            text(content, font, "Ticket " + passenger.ticketPassengerId()
                    + "   " + passenger.coachNumber() + " / " + passenger.berthNumber()
                    + " (" + passenger.berthType() + ")", 52, y - 16, 9, Color.BLACK);
            text(content, font, passenger.currency() + " " + passenger.farePaid(), 430, y, 9, Color.BLACK);
            y -= 42;
        }
        content.setStrokingColor(new Color(210, 218, 228));
        content.moveTo(35, 120);
        content.lineTo(560, 120);
        content.stroke();
        text(content, font, DISCLAIMER, 35, 96, 8, new Color(75, 82, 92));
        text(content, font, "Verification token: " + token, 35, 76, 6, new Color(110, 110, 110));
    }

    private static void text(
            PDPageContentStream content,
            org.apache.pdfbox.pdmodel.font.PDFont font,
            String value,
            float x,
            float y,
            float size,
            Color color
    ) throws IOException {
        content.beginText();
        content.setNonStrokingColor(color);
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(value == null ? "" : value);
        content.endText();
    }

    private static byte[] qrPng(String value) throws WriterException, IOException {
        var matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 240, 240);
        BufferedImage image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 240; y++) {
            for (int x = 0; x < 240; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static String value(Object value) {
        return value == null ? "Not available" : value.toString();
    }

    private static String validateOrigin(String origin) {
        try {
            java.net.URI uri = java.net.URI.create(origin);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Ticket verification origin is invalid");
            }
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp) {
                throw new IllegalArgumentException("Ticket verification origin must use HTTPS");
            }
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Ticket verification origin is invalid", exception);
        }
    }
}
