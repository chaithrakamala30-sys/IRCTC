package in.railconnect.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestBodyLimitFilterTest {
    @Test
    void streamedRequestWithoutContentLengthCannotExceedLimit() {
        long limit = 16;
        MockHttpServletRequest original = new MockHttpServletRequest("POST", "/test");
        original.setContent("{\"name\":\"longer-than-limit\"}".getBytes(StandardCharsets.UTF_8));
        HttpServletRequest request = new HttpServletRequestWrapper(original) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(new ObjectMapper(), limit);

        assertThrows(PayloadTooLargeException.class, () -> filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (limitedRequest, response) -> limitedRequest.getInputStream().readAllBytes()));
    }
}
