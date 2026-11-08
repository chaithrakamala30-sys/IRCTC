package in.railconnect.core.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.masterdata.MasterDataModels.RouteVersionRequest;
import in.railconnect.core.masterdata.MasterDataModels.TrainRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MasterDataServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MasterDataService service = new MasterDataService(jdbcTemplate, new ObjectMapper());

    @Test
    void routeCannotUseTheSameOriginAndDestination() {
        UUID stationId = UUID.randomUUID();
        RouteVersionRequest request = new RouteVersionRequest(
                "Invalid route",
                stationId,
                stationId,
                BigDecimal.TEN,
                LocalDate.now(),
                null,
                null,
                List.of()
        );

        assertThatThrownBy(() -> service.createRoute(request, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(MasterDataException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_ROUTE_STATIONS");
                });
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void trainCannotUseTheSameSourceAndDestination() {
        UUID stationId = UUID.randomUUID();
        TrainRequest request = new TrainRequest(
                "12345",
                "Invalid train",
                UUID.randomUUID(),
                UUID.randomUUID(),
                stationId,
                stationId,
                null,
                null,
                null,
                LocalDate.now(),
                null
        );

        assertThatThrownBy(() -> service.createTrain(request, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(MasterDataException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_TRAIN_STATIONS");
                });
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void pageSizeIsCappedToProtectMasterDataQueries() {
        assertThatThrownBy(() -> service.trains(0, 101, null, null, null))
                .isInstanceOfSatisfying(MasterDataException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_PAGE");
                });
        verifyNoInteractions(jdbcTemplate);
    }
}
