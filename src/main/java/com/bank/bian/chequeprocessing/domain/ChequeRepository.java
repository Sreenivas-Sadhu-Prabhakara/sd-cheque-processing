package com.bank.bian.chequeprocessing.domain;

import java.util.Collection;
import java.util.Optional;

/** Persistence port — in-memory now, Postgres when the platform hydrates. */
public interface ChequeRepository {

    void save(Cheque cheque);

    Optional<Cheque> findById(String chequeId);

    Collection<Cheque> findAll();
}
