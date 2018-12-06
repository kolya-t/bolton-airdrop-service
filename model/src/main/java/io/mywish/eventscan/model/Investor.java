package io.mywish.eventscan.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "investor")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Investor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investor_address", nullable = false)
    private String investorAddress;

    @Column(name = "contract_address", nullable = false)
    private String contractAddress;

    public Investor(String investorAddress, String contractAddress) {
        this.investorAddress = investorAddress;
        this.contractAddress = contractAddress;
    }
}
