package petr.warehouse.products_service.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import petr.warehouse.products_service.dto.SummaryReportDto;
import petr.warehouse.products_service.exception.request.ReportPdfUnavailableException;

import java.net.http.HttpClient;
import java.time.Duration;

//Клиент к python-сервису, который рисует PDF.
//Наружу сервис не торчит: один origin, одна авторизация.
@Component
public class ReportPdfClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ReportPdfClient(
            ObjectMapper objectMapper,
            @Value("${report.pdf-service.url}") String baseUrl,
            @Value("${report.pdf-service.connect-timeout:5s}") Duration connectTimeout,
            @Value("${report.pdf-service.read-timeout:30s}") Duration readTimeout
    ) {
        this.objectMapper = objectMapper;

        //uvicorn не понимает h2c-апгрейд, который JDK-клиент шлёт по умолчанию.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public byte[] render(SummaryReportDto summary, String theme) {
        //Сериализуем тем же маппером, что и остальные ответы приложения,
        //иначе даты уехали бы в другой формат, чем ждёт python.
        String payload;
        try {
            payload = objectMapper.writeValueAsString(summary);
        } catch (JacksonException e) {
            throw new ReportPdfUnavailableException("Не удалось подготовить данные для PDF", e);
        }

        byte[] pdf;
        try {
            pdf = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/render").queryParam("theme", theme).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PDF)
                    .body(payload)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new ReportPdfUnavailableException("Сервис формирования PDF недоступен", e);
        }

        if (pdf == null || pdf.length == 0) {
            throw new ReportPdfUnavailableException("Сервис формирования PDF вернул пустой ответ", null);
        }

        return pdf;
    }
}
