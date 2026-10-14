package com.bank.fd.repository;
import com.bank.fd.entity.FdJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface FdJournalRepository extends JpaRepository<FdJournal,Long> {
    Optional<FdJournal> findByJournalKey(String key);
    List<FdJournal> findByFdAccountIdOrderByPostedAtAscIdAsc(Long id);
}
