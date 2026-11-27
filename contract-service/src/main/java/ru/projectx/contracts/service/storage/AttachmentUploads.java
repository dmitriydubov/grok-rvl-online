package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.dto.ConsentResponse;

public interface AttachmentUploads {

    AttachmentResponse receive(CertificateUpload upload);

    ConsentResponse receive(ConsentUpload upload);
}
