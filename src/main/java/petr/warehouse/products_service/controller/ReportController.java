package petr.warehouse.products_service.controller;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import petr.warehouse.products_service.dto.SummaryReportDto;
import petr.warehouse.products_service.filter.ReportFilter;
import petr.warehouse.products_service.service.ReportService;

@RestController
@RequestMapping("/report")
public class ReportController {
    private final ReportService reportService;

    @Autowired
    public ReportController(ReportService reportService){
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryReportDto> getReportSummary(
            @Valid @ParameterObject ReportFilter filter
    ) {
        return ResponseEntity.ok(
                reportService.createNewReport(filter.getStorageName(), filter.getDateFrom(), filter.getDateTo())
        );
    }
}
