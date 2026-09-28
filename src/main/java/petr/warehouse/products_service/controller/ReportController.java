package petr.warehouse.products_service.controller;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import petr.warehouse.products_service.dto.SummaryReportDto;
import petr.warehouse.products_service.filter.ReportFilter;
import petr.warehouse.products_service.service.ReportPdfClient;
import petr.warehouse.products_service.service.ReportService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/report")
public class ReportController {
    private final ReportService reportService;
    private final ReportPdfClient reportPdfClient;

    @Autowired
    public ReportController(ReportService reportService, ReportPdfClient reportPdfClient){
        this.reportService = reportService;
        this.reportPdfClient = reportPdfClient;
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryReportDto> getReportSummary(
            @Valid @ParameterObject ReportFilter filter
    ) {
        return ResponseEntity.ok(
                reportService.createNewReport(filter.getStorageName(), filter.getDateFrom(), filter.getDateTo())
        );
    }

    //Тот же отчёт, но отрисованный в PDF отдельным python-сервисом.
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReportPdf(
            @Valid @ParameterObject ReportFilter filter,
            //Отчёт печатается в той же теме, в которой пользователь смотрит интерфейс.
            @RequestParam(defaultValue = "light") String theme
    ){
        String storageName = filter.getStorageName();
        LocalDate dateFrom = filter.getDateFrom();
        LocalDate dateTo = filter.getDateTo();

        SummaryReportDto summary = reportService.createNewReport(storageName, dateFrom, dateTo);
        byte[] pdf = reportPdfClient.render(summary, "dark".equalsIgnoreCase(theme) ? "dark" : "light");

        //Имя склада кириллическое, поэтому filename* в кодировке UTF-8.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("report-%s-%s-%s.pdf".formatted(storageName, dateFrom, dateTo), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}
