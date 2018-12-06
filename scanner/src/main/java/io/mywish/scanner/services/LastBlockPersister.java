package io.mywish.scanner.services;

import io.mywish.eventscan.model.LastBlock;
import io.mywish.eventscan.repositories.LastBlockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class LastBlockPersister {
    @Autowired
    private LastBlockRepository lastBlockRepository;

    @Value("${etherscanner.start-block:#{null}}")
    private Long lastBlock;

    @PostConstruct
    protected void init() {
        if (lastBlock != null) {
            saveLastBlock(lastBlock);
        } else {
            lastBlock = lastBlockRepository.getLastBlock();
        }
    }

    public Long getLastBlock() {
        return lastBlock;
    }

    public synchronized void saveLastBlock(long blockNumber) {
        if (lastBlock == null) {
            lastBlockRepository.save(new LastBlock(blockNumber));
        } else {
            lastBlockRepository.updateLastBlock(blockNumber);
        }
        lastBlock = blockNumber;
    }
}
