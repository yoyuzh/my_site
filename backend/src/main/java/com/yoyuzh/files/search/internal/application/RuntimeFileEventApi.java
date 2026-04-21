package com.yoyuzh.files.search.internal.application;

import com.yoyuzh.files.search.api.FileEventApi;
import com.yoyuzh.files.search.api.FileEventRecordCommand;
import com.yoyuzh.files.search.internal.domain.FileEvent;
import com.yoyuzh.files.search.internal.infra.FileEventCrossInstancePublisher;
import com.yoyuzh.files.search.internal.infra.FileEventPayloadCodec;
import com.yoyuzh.files.search.internal.infra.FileEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RuntimeFileEventApi implements FileEventApi {
    private static final String CLIENT_ID_HEADER = "X-Yoyuzh-Client-Id";

    private final FileEventRepository fileEventRepository;
    private final FileEventPayloadCodec payloadCodec;
    private final FileEventDispatcher fileEventDispatcher;
    private final FileEventCrossInstancePublisher fileEventCrossInstancePublisher;

    public RuntimeFileEventApi(FileEventRepository fileEventRepository,
                               FileEventPayloadCodec payloadCodec,
                               FileEventDispatcher fileEventDispatcher,
                               FileEventCrossInstancePublisher fileEventCrossInstancePublisher) {
        this.fileEventRepository = fileEventRepository;
        this.payloadCodec = payloadCodec;
        this.fileEventDispatcher = fileEventDispatcher;
        this.fileEventCrossInstancePublisher = fileEventCrossInstancePublisher;
    }

    @Override
    public SseEmitter openStream(Long userId, String path, String clientId) {
        return fileEventDispatcher.openStream(userId, path, resolveClientId(clientId));
    }

    @Override
    public void record(FileEventRecordCommand command) {
        if (command == null || command.userId() == null || command.eventType() == null) {
            return;
        }

        FileEvent event = new FileEvent();
        event.setUserId(command.userId());
        event.setEventType(command.eventType());
        event.setFileId(command.fileId());
        event.setFromPath(command.fromPath());
        event.setToPath(command.toPath());
        event.setClientId(resolveClientId(command.clientId()));
        event.setPayloadJson(payloadCodec.toJson(command.payload()));
        fileEventRepository.save(event);
        broadcast(event);
    }

    public void broadcastReplicatedEvent(FileEvent event) {
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
