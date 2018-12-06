package io.mywish.airdrop.service;

import io.mywish.airdrop.exception.UnlockAddressException;
import io.mywish.airdrop.model.contracts.DepositPlan;
import io.mywish.eventscan.model.Investor;
import io.mywish.eventscan.repositories.InvestorRepository;
import io.mywish.scanner.NewBlockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.admin.Admin;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class AirdropService {
    @Autowired
    private Web3j web3j;
    @Autowired
    private Admin admin;
    @Autowired
    private InvestorRepository investorRepository;

    @Value("${io.mywish.airdrop.admin-address}")
    private String serverAddress;
    @Value("${io.mywish.airdrop.admin-password}")
    private String serverAccountPassword;
    @Value("${io.mywish.airdrop.origin-block}")
    private Long originBlock;
    @Value("${io.mywish.airdrop.silver-address}")
    private String silverAddress;
    @Value("${io.mywish.airdrop.gold-address}")
    private String goldAddress;
    @Value("${io.mywish.airdrop.platinum-address}")
    private String platinumAddress;
    @Value("${io.mywish.airdrop.try-and-buy-address}")
    private String tryAndBuyAddress;

    private List<String> contractAddresses;
    private TransactionManager transactionManager;
    private ContractGasProvider contractGasProvider;

    @PostConstruct
    protected void init() {
        transactionManager = new ClientTransactionManager(web3j, serverAddress, Integer.MAX_VALUE, 5000);
        contractGasProvider = new DefaultGasProvider();
        contractAddresses = Stream.of(silverAddress, goldAddress, platinumAddress, tryAndBuyAddress)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        unlockInvoke();
    }

    private DepositPlan loadDepositPlan(String contractAddress) {
        return DepositPlan.load(contractAddress, web3j, transactionManager, contractGasProvider);
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

                    if (contractAddresses.contains(contractAddress)) {
                        return;
                    }

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

                    DepositPlan depositPlan = loadDepositPlan(contractAddress);

                    depositPlan
                            .getAddInvestorEvents(receipt)
                            .stream()
                            .map(addInvestorEventResponse -> addInvestorEventResponse._investor)
                            .forEach(investor -> investorRepository.save(new Investor(investor, contractAddress)));

                    depositPlan
                            .getRemoveInvestorEvents(receipt)
                            .stream()
                            .map(removeInvestorEventResponse -> removeInvestorEventResponse._investor)
                            .forEach(investor -> investorRepository.delete(new Investor(investor, contractAddress)));
                });
    }

    private CompletableFuture<Void> unlockInvokeFuture() {
        if (serverAccountPassword == null || serverAccountPassword.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return admin.personalUnlockAccount(serverAddress, serverAccountPassword)
                .sendAsync()
                .thenAccept(personalUnlockAccount -> {
                    if (personalUnlockAccount.getResult() == null || !personalUnlockAccount.getResult()) {
                        throw new UnlockAddressException("Impossible to unlock account " + serverAddress + ".");
                    }
                });
    }

    private CompletionStage<Void> unlockInvoke() {
        return unlockInvokeFuture();
    }

    @Scheduled(cron = "0 0 12 * * *")
    protected synchronized void doAirdrop() {
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

            unlockInvoke()
                    .thenAccept(v -> {
                        try {
                            depositPlan
                                    .airdrop(investors)
                                    .send();
                        } catch (Exception e) {
                            log.warn("Error when executing airdrop function.", e);
                        }
                    });
        });
    }
}
