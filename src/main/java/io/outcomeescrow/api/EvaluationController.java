package io.outcomeescrow.api;

import io.outcomeescrow.services.EvaluationService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvaluationController {
  private final EvaluationService evaluationService;

  public EvaluationController(EvaluationService evaluationService) {
    this.evaluationService = evaluationService;
  }

  @PostMapping("/contracts/{id}/evaluate")
  public Map<String, Object> evaluate(@PathVariable String id) {
    return evaluationService.evaluate(id);
  }

  @PostMapping("/verdicts/{id}/replay")
  public Map<String, Object> replay(@PathVariable String id) {
    return evaluationService.replay(id);
  }
}
