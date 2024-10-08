package io.bootify.health_hive.service;

import io.bootify.health_hive.domain.DataUploadRequest;
import io.bootify.health_hive.domain.File;
import io.bootify.health_hive.domain.UserFile;
import io.bootify.health_hive.domain.LabDataUpload;
import io.bootify.health_hive.model.FileDTO;
import io.bootify.health_hive.model.UserFileDTO;
import io.bootify.health_hive.repos.DataUploadRequestRepository;
import io.bootify.health_hive.repos.FileRepository;
import io.bootify.health_hive.repos.UserFileRepository;
import io.bootify.health_hive.repos.LabDataUploadRepository;
import io.bootify.health_hive.util.NotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;


@Service
public class FileService {

    @Value("${file.upload-dir}") // Spring Boot configuration property for upload directory
    private String UPLOAD_DIR;

    private final FileRepository fileRepository;
    private final LabDataUploadRepository labDataUploadRepository;
    private final DataUploadRequestRepository dataUploadRequestRepository;
    private final UserFileRepository userFileRepository;

    public FileService(final FileRepository fileRepository,
                       final LabDataUploadRepository labDataUploadRepository,
                       final DataUploadRequestRepository dataUploadRequestRepository,
                       final UserFileRepository userFileRepository) {
        this.fileRepository = fileRepository;
        this.labDataUploadRepository = labDataUploadRepository;
        this.dataUploadRequestRepository = dataUploadRequestRepository;
        this.userFileRepository = userFileRepository;
    }

    public List<FileDTO> findAll() {
        final List<File> files = fileRepository.findAll(Sort.by("id"));
        return files.stream()
                .map(file -> mapToDTO(file, new FileDTO()))
                .toList();
    }

    public List<UserFileDTO> findAllByUserId(Long userId) {
        final List<UserFile> userFiles = userFileRepository.findAllByUserId(userId);
        return userFiles.stream()
                .map(userFile -> mapToDTO(userFile, new UserFileDTO()))
                .toList();
    }

    public List <FileDTO> findLatestFile( Long dataUploadRequestId,Long labDataUploadId ){
        return fileRepository.findTop5ByDataUploadRequestIdOrLabDataUploadIdOrderByLastUpdatedDesc(dataUploadRequestId.intValue(), labDataUploadId.intValue())
                .stream()
                .map(file -> mapToDTO(file, new FileDTO()))
                .toList();
    }



    public FileDTO get(final Long id) {
        return fileRepository.findById(id)
                .map(file -> mapToDTO(file, new FileDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public Long create(final FileDTO fileDTO) {
        final File file = new File();
        mapToEntity(fileDTO, file);
        return fileRepository.save(file).getId();
    }

    public void update(final Long id, final FileDTO fileDTO) {
        final File file = fileRepository.findById(id)
                .orElseThrow(NotFoundException::new);
        mapToEntity(fileDTO, file);
        fileRepository.save(file);
    }

    public void delete(final Long id) {
        fileRepository.deleteById(id);
    }

    private FileDTO mapToDTO(final File file, final FileDTO fileDTO) {
        fileDTO.setId(file.getId());
        fileDTO.setName(file.getName());
        fileDTO.setType(file.getType());
        fileDTO.setFilePath(file.getFilePath());
        fileDTO.setCreatedDate(file.getCreatedDate());
        fileDTO.setFileHash(file.getFileHash());
        fileDTO.setLabDataUpload(file.getLabDataUpload() == null ? null : file.getLabDataUpload().getId());
        fileDTO.setDataUploadReqeust(file.getDataUploadRequest() == null ? null : file.getDataUploadRequest().getId());
        return fileDTO;
    }

    private UserFileDTO mapToDTO(final UserFile userFile, final UserFileDTO userFileDTO) {
        userFileDTO.setId(userFile.getId());
        userFileDTO.setUserId(userFile.getUserId());
        userFileDTO.setDataUploadRequestId(userFile.getDataUploadRequestId());
        userFileDTO.setLabDataUploadId(userFile.getLabDataUploadId());
        userFileDTO.setCreatedDate(userFile.getCreatedDate());
        userFileDTO.setLastUpdated(userFile.getLastUpdated());
        userFileDTO.setFileHash(userFile.getFileHash());
        userFileDTO.setFilePath(userFile.getFilePath());
        userFileDTO.setName(userFile.getName());
        userFileDTO.setLabRequestId(userFile.getLabRequestId());
        return userFileDTO;
    }

    private File mapToEntity(final FileDTO fileDTO, final File file) {
        file.setName(fileDTO.getName());
        file.setType(fileDTO.getType());
        file.setFilePath(fileDTO.getFilePath());
        file.setFileHash(fileDTO.getFileHash());
        file.setCreatedDate(fileDTO.getCreatedDate());
        final LabDataUpload labDataUpload = fileDTO.getLabDataUpload() == null ? null : labDataUploadRepository.findById(fileDTO.getLabDataUpload())
                .orElseThrow(() -> new NotFoundException("labDataUpload not found"));
        file.setLabDataUpload(labDataUpload);
        final DataUploadRequest dataUploadReqeust = fileDTO.getDataUploadRequest() == null ? null : dataUploadRequestRepository.findById(fileDTO.getDataUploadRequest())
                .orElseThrow(() -> new NotFoundException("dataUpload Reqeust not found"));
        file.setDataUploadRequest(dataUploadReqeust);
        return file;
    }


    public String saveFile(MultipartFile file) throws IOException {
        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;
        Path uploadPath = Paths.get(UPLOAD_DIR);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        try {
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);
            return filePath.toString();
        } catch (IOException e) {
            throw new IOException("Could not save file: " + fileName, e);
        }
    }

    public ByteArrayResource getFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            byte[] data = Files.readAllBytes(path);
            return new ByteArrayResource(data);
        } catch (IOException e) {
            throw new NotFoundException("File not found");
        }
    }
}

