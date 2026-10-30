package com.eugene.sharednotes.note;

import com.eugene.sharednotes.exceptions.EmptyNoteException;
import com.eugene.sharednotes.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "notes")
public class NoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public NoteEntity(String title, String body, UserEntity owner) {
        String newTitle = resolveField(title, null);
        String newBody = resolveField(body, null);

        validateTitleOrBody(newTitle, newBody);

        this.title = newTitle;
        this.body = newBody;
        this.owner = Objects.requireNonNull(owner, "Owner must be provided");
    }

    public void update(String title, String body) {
        String newTitle = resolveField(title, this.title);
        String newBody = resolveField(body, this.body);

        validateTitleOrBody(newTitle, newBody);

        this.title = newTitle;
        this.body = newBody;
    }

    private static String resolveField(String newString, String currentString) {
        if (newString == null) return currentString;
        if (newString.isBlank()) return null;
        return newString.strip();
    }

    private static void validateTitleOrBody(String title, String body) {
        if ((title == null || title.isBlank()) && (body == null || body.isBlank())) {
            throw new EmptyNoteException();
        }
    }
}
