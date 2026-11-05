package in.railconnect.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final long maximumBytes;

    public RequestBodyLimitFilter(
            ObjectMapper objectMapper,
            @Value("${railconnect.security.max-request-body-bytes:1048576}") long maximumBytes
    ) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("Maximum request body size must be positive");
        }
        this.objectMapper = objectMapper;
        this.maximumBytes = maximumBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            jakarta.servlet.FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getContentLengthLong() > maximumBytes) {
            writeTooLarge(response);
            return;
        }

        filterChain.doFilter(new LimitedRequest(request, maximumBytes), response);
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiEnvelope.failure(
                        new ApiError(
                                "REQUEST_TOO_LARGE",
                                "The request body exceeds the allowed size",
                                java.util.List.of()),
                        RequestIds.current()));
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maximumBytes;
        private ServletInputStream limitedInputStream;

        private LimitedRequest(HttpServletRequest request, long maximumBytes) {
            super(request);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedInputStream == null) {
                ServletInputStream delegate = super.getInputStream();
                limitedInputStream = new ServletInputStream() {
                    private long bytesRead;

                    @Override
                    public int read() throws IOException {
                        int value = delegate.read();
                        count(value < 0 ? 0 : 1);
                        return value;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int count = delegate.read(buffer, offset, length);
                        count(count);
                        return count;
                    }

                    @Override
                    public boolean isFinished() {
                        return delegate.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return delegate.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        delegate.setReadListener(readListener);
                    }

                    private void count(int bytes) throws PayloadTooLargeException {
                        if (bytes <= 0) {
                            return;
                        }
                        bytesRead += bytes;
                        if (bytesRead > maximumBytes) {
                            throw new PayloadTooLargeException(maximumBytes);
                        }
                    }
                };
            }
            return limitedInputStream;
        }
    }
}
