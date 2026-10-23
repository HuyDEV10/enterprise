package com.huy.enterprise.ai.external;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GdeltFeedAvailabilityTest {
    private static final String MANIFEST = "https://data.gdeltproject.org/gdeltv2/lastupdate.txt";
    private static String export(String stamp) {
        return "https://data.gdeltproject.org/gdeltv2/" + stamp + ".export.CSV.zip";
    }

    @Test void usesThirdPublishedBatchAndReportsItsActualSource() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MANIFEST)).andRespond(withSuccess("20 ignored " + export("20261023130000"), MediaType.TEXT_PLAIN));
        server.expect(requestTo(export("20261023130000"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(export("20261023124500"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(export("20261023123000"))).andRespond(withSuccess(new byte[]{1,2,3}, MediaType.APPLICATION_OCTET_STREAM));
        var download = GdeltIngestionService.downloadLatest(builder.build(), MANIFEST);
        assertThat(download.sourceUrl()).isEqualTo(export("20261023123000"));
        assertThat(download.bytes()).containsExactly((byte)1,(byte)2,(byte)3);
        server.verify();
    }

    @Test void candidateTimesCrossMonthAndDayBoundariesWithExactlyFourFallbacks() {
        assertThat(GdeltIngestionService.exportCandidates(export("20260301000000"))).containsExactly(
                export("20260301000000"), export("20260228234500"), export("20260228233000"),
                export("20260228231500"), export("20260228230000"));
    }

    @Test void exhaustedOfficialBatchesReturnUnavailableWithoutUnboundedRetries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MANIFEST)).andRespond(withSuccess("20 ignored " + export("20261023130000"), MediaType.TEXT_PLAIN));
        for (String candidate : GdeltIngestionService.exportCandidates(export("20261023130000"))) {
            server.expect(requestTo(candidate)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        }
        assertThatThrownBy(() -> GdeltIngestionService.downloadLatest(builder.build(), MANIFEST))
                .isInstanceOf(GdeltUnavailableException.class).hasMessageContaining("not published yet");
        server.verify();
    }

    @Test void localHttpExport404HasNoFollowupRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String local = "http://127.0.0.1:54321/gdeltv2/20261023130000.export.CSV.zip";
        server.expect(requestTo(MANIFEST)).andRespond(withSuccess("20 ignored " + local, MediaType.TEXT_PLAIN));
        server.expect(requestTo(local)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> GdeltIngestionService.downloadLatest(builder.build(), MANIFEST))
                .isInstanceOf(GdeltUnavailableException.class);
        assertThat(GdeltIngestionService.exportCandidates(local)).isEqualTo(List.of(local));
        server.verify();
    }

    @Test void non404UpstreamErrorsFailWithoutTryingPreviousBatches() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MANIFEST)).andRespond(withSuccess("20 ignored " + export("20261023130000"), MediaType.TEXT_PLAIN));
        server.expect(requestTo(export("20261023130000"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> GdeltIngestionService.downloadLatest(builder.build(), MANIFEST))
                .isInstanceOf(GdeltUnavailableException.class).hasMessageContaining("temporarily unavailable");
        server.verify();
    }

    @Test void manifestErrorsAreAlsoUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MANIFEST)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> GdeltIngestionService.downloadLatest(builder.build(), MANIFEST))
                .isInstanceOf(GdeltUnavailableException.class);
        server.verify();
    }
}
