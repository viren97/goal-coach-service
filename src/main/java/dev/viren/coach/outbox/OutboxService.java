package dev.viren.coach.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * MANDATORY: the event must be written in the same DB transaction as the business change,
     * otherwise we could commit the change and lose the event (or the reverse).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OutboxEventType type, String aggregateId, Object payload) {
        try {
            outbox.save(new OutboxEvent(type, aggregateId, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise outbox payload " + type, e);
        }
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> pending(int limit) {
        return outbox.findByStatusOrderByCreatedAtAsc(OutboxStatus.NEW, PageRequest.of(0, limit));
    }

    @Transactional
    public void markPublished(Collection<String> ids) {
        if (!ids.isEmpty()) outbox.findAllById(ids).forEach(OutboxEvent::markPublished);
    }
}
