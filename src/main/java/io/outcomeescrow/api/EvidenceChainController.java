package io.outcomeescrow.api;

import io.outcomeescrow.evidence.EvidenceRecord;
import io.outcomeescrow.evidence.EvidenceRepository;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceChainController {
  private final JdbcClient jdbcClient;
  private final EvidenceRepository evidenceRepository;

  public EvidenceChainController(JdbcClient jdbcClient, EvidenceRepository evidenceRepository) {
    this.jdbcClient = jdbcClient;
    this.evidenceRepository = evidenceRepository;
  }

  @GetMapping("/contracts/{contractId}/evidence")
  public List<EvidenceRecord> list(@PathVariable String contractId) {
    return evidenceRepository.findByContract(contractId);
  }

  @GetMapping("/contracts/{contractId}/evidence/verify")
  public EvidenceRepository.VerificationResult verify(@PathVariable String contractId) {
    return evidenceRepository.verify(contractId);
  }
}
