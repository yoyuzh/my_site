package com.yoyuzh.files.events;

import com.yoyuzh.auth.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Service
public class FileEventService {
    private static final String CLIENT_ID_HEADER = "X-Yoyuzh-Client-Id";

    private final FileEventRepository fileEventRepository;
    private final FileEventPayloadCodec payloadCodec;
    private final FileEventDispatcher fileEventDispatcher;
    private final FileEventCrossInstancePublisher fileEventCrossInstancePublisher;

    public FileEventService(FileEventRepository fileEventRepository,
                            FileEventPayloadCodec payloadCodec,
                            FileEventDispatcher fileEventDispatcher,
                            FileEventCrossInstancePublisher fileEventCrossInstancePublisher) {
        this.fileEventRepository = fileEventRepository;
        this.payloadCodec = payloadCodec;
        this.fileEventDispatcher = fileEventDispatcher;
        this.fileEventCrossInstancePublisher = fileEventCrossInstancePublisher;
    }

    public SseEmitter openStream(User user, String path, String clientId) {
        return fileEventDispatcher.openStream(user.getId(), path, resolveClientId(clientId));
    }

    public FileEvent record(User user,
                            FileEventType eventType,
                            Long fileId,
                            String fromPath,
                            String toPath,
                            String clientId,
                            Map<String, Object> payload) {
        FileEvent event = new FileEvent();
        event.setUserId(user.getId());
        event.setEventType(eventType);
        event.setFileId(fileId);
        event.setFromPath(fromPath);
        event.setToPath(toPath);
        event.setClientId(resolveClientId(clientId));
        event.setPayloadJson(payloadCodec.toJson(payload));
        fileEventRepository.save(event);
        broadcast(event);
        return event;
    }

    public FileEvent record(User user,
                            FileEventType eventType,
                            Long fileId,
                            String fromPath,
                            String toPath,
                            Map<String, Object> payload) {
        return record(user, eventType, fileId, fromPath, toPath, null, payload);
    }

    void broadcastReplicatedEvent(FileEvent event) {
        fileEventDispatcher.broadcast(event);
    }

    private void broadcast(FileEvent event) {
        Runnable broadcastTask = () -> {
            fileEventDispatcher.broadcast(event);
            fileEventCrossInstancePublisher.publish(event);
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcastTask.run();
                }
            });
            return;
        }

        broadcastTask.run();
    }

    private String resolveClientId(String explicitClientId) {
        if (StringUtils.hasText(explicitClientId)) {
            return explicitClientId.trim();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String requestClientId = request.getHeader(CLIENT_ID_HEADER);
        return StringUtils.hasText(requestClientId) ? requestClientId.trim() : null;
    }
}
