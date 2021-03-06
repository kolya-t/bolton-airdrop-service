package io.mywish.airdrop.service;

import com.google.common.collect.Lists;
import io.mywish.airdrop.model.contracts.DepositPlan;
import io.mywish.eventscan.model.Investor;
import io.mywish.eventscan.repositories.InvestorRepository;
import io.mywish.scanner.model.NewBlockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class AirdropService {
    @Autowired
    private Web3j web3j;
    @Autowired
    private InvestorRepository investorRepository;

    @Value("${io.mywish.airdrop.admin-address}")
    private String serverAddress;
    @Value("${io.mywish.airdrop.admin-private-key}")
    private String serverPrivateKey;
    @Value("${io.mywish.airdrop.silver-address}")
    private String silverAddress;
    @Value("${io.mywish.airdrop.gold-address}")
    private String goldAddress;
    @Value("${io.mywish.airdrop.platinum-address}")
    private String platinumAddress;
    @Value("${io.mywish.airdrop.try-and-buy-address}")
    private String tryAndBuyAddress;
    @Value("${io.mywish.airdrop.investors-batch-size:100}")
    private int investorsBatchSize;

    private List<String> contractAddresses;
    private Credentials credentials;
    private ContractGasProvider contractGasProvider;

    @PostConstruct
    protected void init() {
        credentials = Credentials.create(serverPrivateKey);
        contractGasProvider = new DefaultGasProvider();
        contractAddresses = Stream.of(silverAddress, goldAddress, platinumAddress, tryAndBuyAddress)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    private DepositPlan loadDepositPlan(String contractAddress) {
        return DepositPlan.load(contractAddress, web3j, credentials, contractGasProvider);
    }

    @EventListener
    public synchronized void onNewBlockEvent(final NewBlockEvent blockEvent) {
        blockEvent.getBlock().getTransactions()
                .stream()
                .map(result -> (EthBlock.TransactionObject) result)
                .map(EthBlock.TransactionObject::get)
                .filter(tx -> tx.getTo() != null)
                .forEach(tx -> {
                    String contractAddress = tx.getTo().toLowerCase();

                    if (!contractAddresses.contains(contractAddress)) {
                        return;
                    }

                    log.debug("Found transaction with contract address");

                    TransactionReceipt receipt;
                    try {
                        receipt = web3j.ethGetTransactionReceipt(tx.getHash())
                                .send()
                                .getTransactionReceipt()
                                .orElseThrow(() -> new Exception("Empty transaction receipt in result."));
                    } catch (Exception e) {
                        log.warn("Error while getting transaction receipt.", e);
                        return;
                    }

                    log.debug("Got transaction receipt for its transaction");
                    DepositPlan depositPlan = loadDepositPlan(contractAddress);

                    depositPlan
                            .getAddInvestorEvents(receipt)
                            .stream()
                            .map(addInvestorEventResponse -> addInvestorEventResponse._investor)
                            .forEach(investor -> {
                                log.info("Saving investor to DB: {}.", investor);
                                investorRepository.save(new Investor(investor, contractAddress));
                            });

                    depositPlan
                            .getRemoveInvestorEvents(receipt)
                            .stream()
                            .map(removeInvestorEventResponse -> removeInvestorEventResponse._investor)
                            .forEach(investor -> {
                                log.info("Removing investor from DB: {}.", investor);
                                investorRepository.delete(new Investor(investor, contractAddress));
                            });
                });
    }

    @Scheduled(cron = "0 0 1 * * ?")
    protected synchronized void doAirdrop() {
        try {
            if (web3j.ethSyncing().send().isSyncing()) {
                log.info("Skipping airdrop because node is still synching.");
                return;
            }
        } catch (IOException e) {
            log.warn("Error when checking node synch status.", e);
        }

        BigInteger currentLastBlockTime;
        try {
            currentLastBlockTime = web3j
                    .ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                    .send()
                    .getBlock()
                    .getTimestamp();
        } catch (IOException e) {
            log.warn("Error getting last block time.", e);
            return;
        }

        contractAddresses.forEach(contractAddress -> {
            DepositPlan depositPlan = loadDepositPlan(contractAddress);

            List<String> investors = investorRepository.getInvestorsByContractAddress(contractAddress)
                    .stream()
                    .map(Investor::getInvestorAddress)
                    .filter(investor -> {
                        try {
                            return depositPlan.calculateInvestorPayoutsForTime(investor, currentLastBlockTime)
                                    .send()
                                    .compareTo(BigInteger.ZERO) != 0;
                        } catch (Exception e) {
                            log.warn("Error calculating {} payouts in {}.", investor, contractAddress, e);
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            if (investors.isEmpty()) {
                return;
            }

            log.debug("Starting airdrop for contract {} for {} investors.", contractAddress, investors.size());
            Lists.partition(investors, investorsBatchSize)
                    .forEach(investorsBatch -> {
                                try {
                                    log.debug("Preparing to airdrop batch {} investors.", investorsBatch.size());
                                    String txHash = depositPlan
                                            .airdrop(investorsBatch)
                                            .send()
                                            .getTransactionHash();
                                    log.info("Executed airdrop for {} investors, tx hash: {}", investorsBatch.size(), txHash);
                                } catch (Exception e) {
                                    log.warn("Error when executing airdrop function.", e);
                                }
                            }
                    );
        });
    }
}
