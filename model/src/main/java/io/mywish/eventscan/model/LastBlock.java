package io.mywish.eventscan.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "last_block")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class LastBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    public LastBlock(Long blockNumber) {
        this.blockNumber = blockNumber;
    }
}
