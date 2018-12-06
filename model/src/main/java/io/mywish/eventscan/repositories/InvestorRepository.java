package io.mywish.eventscan.repositories;

import io.mywish.eventscan.model.Investor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvestorRepository extends CrudRepository<Investor, Long> {
    List<Investor> getInvestorsByContractAddress(@Param("contractAddress") String contractAddress);
}
