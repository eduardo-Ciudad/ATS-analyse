package eduar.atsanalyzer.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analise")
public class AnaliseController {

    private final AnaliseService analiseService;
}
