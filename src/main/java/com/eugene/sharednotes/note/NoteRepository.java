package com.eugene.sharednotes.note;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<NoteEntity, Long> {
    @Query("""
            SELECT n FROM NoteEntity n
            WHERE n.owner.id = :ownerId
            AND (n.title ILIKE CONCAT('%', :text, '%') OR n.body ILIKE CONCAT('%', :text, '%'))
            """)
    Page<NoteEntity> findByUserWhereTitleOrBodyLikeText(
            @Param("ownerId") Long ownerId,
            @Param("text") String text,
            Pageable pageable);

    Optional<NoteEntity> findByOwnerIdAndId(Long ownerId, Long id);
}
