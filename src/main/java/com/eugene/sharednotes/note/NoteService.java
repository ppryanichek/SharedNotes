package com.eugene.sharednotes.note;

import com.eugene.sharednotes.note.dto.CreateNoteRequest;
import com.eugene.sharednotes.note.dto.NoteResponse;
import com.eugene.sharednotes.note.dto.UpdateNoteRequest;
import com.eugene.sharednotes.note.exceptions.EmptyNoteException;
import com.eugene.sharednotes.note.exceptions.NoteNotFoundException;
import com.eugene.sharednotes.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Сервис управления заметками текущего пользователя: CRUD-операции над {@link NoteEntity}
 */
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class NoteService {
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;

    /**
     * Ищет заметки текущего пользователя по указанному тексту.
     * <p> Убирает пробелы по краям. Если поле {@code text} null – приводит его к пустой строке.
     * <p> Если поле {@code text} пустая строка – ищет все заметки пользователя.
     *
     * @param userId id владельца.
     * @param text искомый текст.
     * @param pageable параметры пагинации:
     *                 <ul>
     *                     <li>{@code page} — номер (индекс) страницы,</li>
     *                     <li>{@code size} — размер страницы,</li>
     *                     <li>{@code sort} — параметры сортировки.</li>
     *                 </ul>
     * @return постраничное отображение информации о заметках текущего пользователя.
     */
    public Page<NoteResponse> findByText(Long userId, String text, Pageable pageable) {
        String searchText = Objects.requireNonNullElse(text, "").strip();

        return noteRepository
                .findByUserWhereTitleOrBodyLikeText(userId, searchText, pageable)
                .map(NoteResponse::from);
    }

    /**
     * Ищет заметку по id пользователя и id заметки.
     *
     * @return информацию о заметке текущего пользователя
     * @throws NoteNotFoundException если заметка с указанным id не найдена у текущего пользователя.
     * В том числе если она принадлежит другому пользователю.
     */
    public NoteResponse findById(Long userId, Long noteId) {
        NoteEntity note = getOwnedNote(userId, noteId);

        return NoteResponse.from(note);
    }

    /**
     * Создаёт заметку текущего пользователя с указанными полями.
     *
     * @param userId id владельца заметки из principal.
     * @param request поля создаваемой заметки:
     *                <ul>
     *                    <li>{@code title} оглавление,</li>
     *                    <li>{@code body} основной текст.</li>
     *                </ul>
     * @return информацию о заметке текущего пользователя.
     * @throws EmptyNoteException если все текстовые поля создаваемой заметки пустые.
     */
    @Transactional
    public NoteResponse createNote(Long userId, CreateNoteRequest request) {
        NoteEntity note = NoteEntity.builder()
                .title(request.title())
                .body(request.body())
                .owner(userRepository.getReferenceById(userId))
                .build();

        noteRepository.save(note);

        return NoteResponse.from(note);
    }

    /**
     * Обновляет указанные поля заметки.
     * <p> Пустая строка очищает поле. {@code null}-строка оставляет текущее значение.
     * <p> Вызывается {@code flush},
     * чтобы {@code @UpdateTimestamp} установил новое время в {@code updatedAt} до формирования ответа.
     *
     * @param userId id владельца заметки из principal.
     * @param noteId id заметки.
     * @param request поля для изменения:
     *                <ul>
     *                    <li>{@code title} оглавление,</li>
     *                    <li>{@code body} основной текст.</li>
     *                </ul>
     * @return информацию обновлённой заметки текущего пользователя.
     * @throws EmptyNoteException если все текстовые поля после обновления стали пустыми.
     * @throws NoteNotFoundException если заметка с указанным id не найдена у текущего пользователя.
     * В том числе если она принадлежит другому пользователю.
     */
    @Transactional
    public NoteResponse updateNote(Long userId, Long noteId, UpdateNoteRequest request) {
        NoteEntity note = getOwnedNote(userId, noteId);

        note.update(request.title(), request.body());

        noteRepository.flush();

        return NoteResponse.from(note);
    }

    /**
     * Удаляет существующую заметку.
     *
     * @throws NoteNotFoundException если заметка с указанным id не найдена у текущего пользователя.
     * В том числе если она принадлежит другому пользователю.
     */
    @Transactional
    public void deleteNote(Long userId, Long noteId) {
        NoteEntity note = getOwnedNote(userId, noteId);

        noteRepository.delete(note);
    }

    private NoteEntity getOwnedNote(Long userId, Long noteId) {
        return noteRepository.findByOwnerIdAndId(userId, noteId)
                .orElseThrow(() -> new NoteNotFoundException(noteId));
    }
}
