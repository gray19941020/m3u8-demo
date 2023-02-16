package com.m3u8.demo.controller;

import com.m3u8.demo.ffmpeg.FFmpegUtils;
import com.m3u8.demo.ffmpeg.TranscodeConfig;
import com.m3u8.demo.service.AmazonS3Service;
import com.m3u8.demo.utils.ZipUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/storage/")
public class UploadS3Controller {
    @Value("${app.video-folder}")
    private String videoFolder;
    @Autowired
    private AmazonS3Service amazonS3Service;

    /**
     * 文件上传到S3
     */
    @PostMapping("/uploadFile")
    public String uploadFile(@RequestPart(value = "file") MultipartFile file) {
        return this.amazonS3Service.uploadFile(file);
    }

    /**
     * 远程文件 转码 压缩 上传到S3
     *
     * @param url
     * @return String
     */
    @PostMapping("/convertFile")
    public String convertFile(String url) {
        String suffix = FilenameUtils.getExtension(url);
        if (!"mp4".equals(suffix)) {
            return "文件类型不是mp4";
        }

        try {
            TranscodeConfig transcodeConfig = new TranscodeConfig("00:00:00.001", "30");
            String filename = FilenameUtils.getName(url);
            String title = FilenameUtils.getBaseName(url);

            URL remoteUrl = new URL(url);
            File destFile = new File(videoFolder + filename);
            FileUtils.copyURLToFile(remoteUrl, destFile);


            Path tempFile = Paths.get(destFile.toURI());
            // 按照日期生成子目录
            String today = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());

            // 尝试创建视频目录
            Path targetFolder = Files.createDirectories(Paths.get(videoFolder, today, title));

            log.info("创建文件夹目录：{}", targetFolder);
            Files.createDirectories(targetFolder);

            // 执行转码操作
            log.info("开始转码");
            try {
                FFmpegUtils.transcodeToM3u8(tempFile.toString(), targetFolder.toString(), transcodeConfig);
            } catch (Exception e) {
                log.error("转码异常：{}", e.getMessage());
            }

            File zipFile = new File(videoFolder + title + ".zip");
            FileOutputStream fos1 = new FileOutputStream(zipFile);
            ZipUtils.toZip(targetFolder.toString(), fos1, true);

            MultipartFile multiFile = new MockMultipartFile("file", zipFile.getName(), null, new FileInputStream(zipFile));
            return this.amazonS3Service.uploadFile(multiFile);
        } catch (Exception e) {
            log.error("", e);
            return "系统异常";
        }
    }

}
