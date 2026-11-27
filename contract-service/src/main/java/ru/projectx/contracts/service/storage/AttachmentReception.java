package ru.projectx.contracts.service.storage;

import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.StorageUnavailableException;

@Service
public class AttachmentReception implements AttachmentUploads {

    private final AttachmentFiles files;
    private final AttachmentRegistry registry;
    private final ObjectKeys keys;
    private final CertificateStore store;
    private final AttachmentMetrics metrics;

    public AttachmentReception(AttachmentFiles files, AttachmentRegistry registry, ObjectKeys keys,
            CertificateStore store, AttachmentMetrics metrics) {
        this.files = files;
        this.registry = registry;
        this.keys = keys;
        this.store = store;
        this.metrics = metrics;
    }

    @Override
    public AttachmentResponse receive(CertificateUpload upload) {
        registry.requireOpen(upload.individualRequestId());
        return place(upload, registry::accepted, registry::register);
    }

    @Override
    public ConsentResponse receive(ConsentUpload upload) {
        return place(upload, registry::accepted, registry::register);
    }

    private <U extends IncomingFile, R> R place(U upload, BiFunction<U, String, Optional<R>> found,
            BiFunction<U, StoredFile, R> save) {
        AttachmentFiles.AcceptedFile accepted = files.accept(upload);
        String hash = ContentDigests.sha256(upload.content());
        Optional<R> prior = found.apply(upload, hash);
        if (prior.isPresent()) {
            return prior.get();
        }
        return write(upload, accepted, hash, new Persist<>(found, save));
    }

    private <U extends IncomingFile, R> R write(U upload, AttachmentFiles.AcceptedFile accepted, String hash,
            Persist<U, R> persist) {
        StoredFile file = new StoredFile(keys.next(upload.attachmentId(), accepted.extension()), hash, upload.size(),
                accepted.contentType());
        try {
            store.store(file, upload.content());
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
        return commit(upload, file, persist);
    }

    private <U extends IncomingFile, R> R commit(U upload, StoredFile file, Persist<U, R> persist) {
        try {
            R response = persist.save().apply(upload, file);
            metrics.countStored();
            return response;
        } catch (DataIntegrityViolationException concurrent) {
            return persist.found().apply(upload, file.hash()).orElseThrow(() -> concurrent);
        }
    }

    private record Persist<U, R>(BiFunction<U, String, Optional<R>> found, BiFunction<U, StoredFile, R> save) {
    }
}
