package com.bank.bian.chequeprocessing.infrastructure;

import com.bank.bian.chequeprocessing.domain.Cheque;
import com.bank.bian.chequeprocessing.domain.ChequeRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 2a adapter; transition atomicity is the service layer's per-cheque lock. */
@Repository
public class InMemoryChequeRepository implements ChequeRepository {

    private final Map<String, Cheque> cheques = new ConcurrentHashMap<>();

    @Override
    public void save(Cheque cheque) {
        cheques.put(cheque.getChequeId(), cheque);
    }

    @Override
    public Optional<Cheque> findById(String chequeId) {
        return Optional.ofNullable(cheques.get(chequeId));
    }

    @Override
    public Collection<Cheque> findAll() {
        return cheques.values();
    }
}
