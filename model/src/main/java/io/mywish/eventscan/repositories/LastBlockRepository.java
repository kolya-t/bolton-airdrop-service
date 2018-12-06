package io.mywish.eventscan.repositories;

import io.mywish.eventscan.model.LastBlock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LastBlockRepository extends CrudRepository<LastBlock, Long> {
    @Query("select block.blockNumber from LastBlock block")
    Long getLastBlock();

    @Modifying
    @Transactional
    @Query("update LastBlock lastBlock set lastBlock.blockNumber = :blockNumber")
    void updateLastBlock(@Param("blockNumber") Long blockNumber);
}
